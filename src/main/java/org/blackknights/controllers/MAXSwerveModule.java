/* Black Knights Robotics (C) 2025 */
package org.blackknights.controllers;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.SparkMaxConfig;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import org.blackknights.utils.ConfigManager;
import org.blackknights.utils.NetworkTablesUtils;

/** A wrapper class for swerve modules */
public class MAXSwerveModule {
    private final SparkFlex drivingSpark;
    private final SparkMax turningSpark;

    private final int drivingCanId;

    private final RelativeEncoder drivingEncoder;
    private final AbsoluteEncoder turningEncoder;

    private final SparkClosedLoopController drivingClosedLoopController;
    private final SparkClosedLoopController turningClosedLoopController;

    private final double chassisAngularOffset;
    private SwerveModuleState desiredState = new SwerveModuleState(0.0, new Rotation2d());

    private SimpleMotorFeedforward feedforward =
            new SimpleMotorFeedforward(0.096286, 2.3216, 0.41854, 1);

    /**
     * Constructs a MAXSwerveModule and configures the driving and turning motor, encoder, and PID
     * controller. This configuration is specific to the REV MAXSwerve Module built with NEOs,
     * SPARKS MAX, and a Through Bore Encoder.
     */
    public MAXSwerveModule(int drivingCANId, int turningCANId, double chassisAngularOffset) {
        drivingSpark = new SparkFlex(drivingCANId, MotorType.kBrushless);
        turningSpark = new SparkMax(turningCANId, MotorType.kBrushless);
        this.drivingCanId = drivingCANId;

        drivingEncoder = drivingSpark.getEncoder();
        turningEncoder = turningSpark.getAbsoluteEncoder();

        drivingClosedLoopController = drivingSpark.getClosedLoopController();
        turningClosedLoopController = turningSpark.getClosedLoopController();

        // Apply the respective configurations to the SPARKS. Reset parameters before
        // applying the configuration to bring the SPARK to a known good state. Persist
        // the settings to the SPARK to avoid losing them on a power cycle.
        drivingSpark.configure(
                MAXSwerveModuleConfig.drivingConfig,
                ResetMode.kResetSafeParameters,
                PersistMode.kPersistParameters);
        turningSpark.configure(
                MAXSwerveModuleConfig.turningConfig,
                ResetMode.kResetSafeParameters,
                PersistMode.kPersistParameters);

        this.chassisAngularOffset = chassisAngularOffset;
        desiredState.angle = new Rotation2d(turningEncoder.getPosition());
        drivingEncoder.setPosition(0);
    }

    /**
     * Returns the current state of the module.
     *
     * @return The current state of the module.
     */
    public SwerveModuleState getState() {
        // Apply chassis angular offset to the encoder position to get the position
        // relative to the chassis.
        return new SwerveModuleState(
                drivingEncoder.getVelocity(),
                new Rotation2d(turningEncoder.getPosition() - chassisAngularOffset));
    }

    /**
     * Returns the current position of the module.
     *
     * @return The current position of the module.
     */
    public SwerveModulePosition getPosition() {
        // Apply chassis angular offset to the encoder position to get the position
        // relative to the chassis.
        return new SwerveModulePosition(
                drivingEncoder.getPosition(),
                new Rotation2d(turningEncoder.getPosition() - chassisAngularOffset));
    }

    /**
     * Sets the desired state for the module.
     *
     * @param desiredState Desired state with speed and angle.
     */
    public void setDesiredState(SwerveModuleState desiredState) {
        // Apply chassis angular offset to the desired state.
        SwerveModuleState correctedDesiredState = new SwerveModuleState();
        correctedDesiredState.speedMetersPerSecond = desiredState.speedMetersPerSecond;
        correctedDesiredState.angle =
                desiredState.angle.plus(Rotation2d.fromRadians(chassisAngularOffset));

        // Optimize the reference state to avoid spinning further than 90 degrees.
        correctedDesiredState.optimize(new Rotation2d(turningEncoder.getPosition()));

        double ffOutput =
                feedforward.calculateWithVelocities(
                        drivingEncoder.getVelocity(), correctedDesiredState.speedMetersPerSecond);

        NetworkTablesUtils.getTable("debug")
                .setEntry(String.format("ID(%s) - Swerve FF Output", drivingCanId), ffOutput);
        NetworkTablesUtils.getTable("debug")
                .setEntry(
                        String.format("ID(%s) - Swerve target mps", drivingCanId),
                        correctedDesiredState.speedMetersPerSecond);

        // Command driving and turning SPARKS towards their respective endpoints.
        drivingClosedLoopController.setReference(
                correctedDesiredState.speedMetersPerSecond,
                ControlType.kVelocity,
                ClosedLoopSlot.kSlot0,
                MathUtil.isNear(
                                0.0,
                                correctedDesiredState.speedMetersPerSecond,
                                ConfigManager.getInstance().get("swerve_min_velocity", 0.01))
                        ? 0.0
                        : ffOutput);

        turningClosedLoopController.setReference(
                correctedDesiredState.angle.getRadians(), ControlType.kPosition);

        this.desiredState = desiredState;
    }

    public void reconfigure(SparkFlexConfig drivingConfig, SparkMaxConfig turningConfig) {
        drivingSpark.configure(
                drivingConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
        turningSpark.configure(
                turningConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    }

    /** Zeroes all the SwerveModule encoders. */
    public void resetEncoders() {
        drivingEncoder.setPosition(0);
    }

    /**
     * Set the voltage of the turning motor
     *
     * @param voltage The target voltage
     */
    public void setTurningVoltage(double voltage) {
        this.turningSpark.setVoltage(voltage);
    }

    /**
     * Set the voltage of the driving motor
     *
     * @param voltage The target voltage
     */
    public void setDrivingVoltage(double voltage) {
        this.drivingSpark.setVoltage(voltage);
    }
}
