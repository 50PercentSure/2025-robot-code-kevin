/* Black Knights Robotics (C) 2025 */
package frc.robot.subsystems;

import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.SparkBase;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel;
import com.revrobotics.spark.config.SparkBaseConfig;
import com.revrobotics.spark.config.SparkFlexConfig;
import edu.wpi.first.math.controller.ElevatorFeedforward;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.networktables.DoubleEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.constants.ElevatorConstants;
import frc.robot.utils.ConfigManager;

public class ElevatorSubsystem extends SubsystemBase {

    // TODO Elevator move to position function

    // Two neovortexes
    private final SparkFlex leftElevatorMotor =
            new SparkFlex(ElevatorConstants.LEFT_MOTOR_ID, SparkLowLevel.MotorType.kBrushless);
    private final SparkFlex rightElevatorMotor =
            new SparkFlex(ElevatorConstants.RIGHT_MOTOR_ID, SparkLowLevel.MotorType.kBrushless);

    // Relative Encoders
    private final RelativeEncoder leftEncoder = leftElevatorMotor.getEncoder();
    private final RelativeEncoder rightEncoder = rightElevatorMotor.getEncoder();

    // Linebreaks
    private final DigitalInput topLineBreak = new DigitalInput(ElevatorConstants.TOP_LINEBREAK_ID);
    private final DigitalInput bottomLineBreak =
            new DigitalInput(ElevatorConstants.BOTTOM_LINEBREAK_ID);

    private final ProfiledPIDController elevatorPID =
            new ProfiledPIDController(
                    ElevatorConstants.ELEVATOR_P,
                    ElevatorConstants.ELEVATOR_I,
                    ElevatorConstants.ELEVATOR_D,
                    ElevatorConstants.CONSTRAINTS);

    private final ElevatorFeedforward elevatorFF =
            new ElevatorFeedforward(
                    ElevatorConstants.ELEVATOR_KS,
                    ElevatorConstants.ELEVATOR_KG,
                    ElevatorConstants.ELEVATOR_KV,
                    ElevatorConstants.ELEVATOR_KA);

    private final DoubleEntry elevatorEncoderPos =
            NetworkTableInstance.getDefault()
                    .getTable("Elevator")
                    .getDoubleTopic("EncoderPos")
                    .getEntry(getElevatorPosition());

    /** Subsystem for the elevator */
    public ElevatorSubsystem() {
        SparkFlexConfig rightElevatorMotorConfig = new SparkFlexConfig();
        SparkFlexConfig leftElevatorMotorConfig = new SparkFlexConfig();

        elevatorPID.setTolerance(
                ConfigManager.getInstance()
                        .get("elevator_pid_tolerance", ElevatorConstants.ELEVATOR_TOLERANCE));

        rightElevatorMotorConfig.inverted(true);
        rightElevatorMotorConfig.idleMode(SparkBaseConfig.IdleMode.kBrake);
        leftElevatorMotorConfig.idleMode(SparkBaseConfig.IdleMode.kBrake);

        rightElevatorMotor.configure(
                rightElevatorMotorConfig,
                SparkBase.ResetMode.kNoResetSafeParameters,
                SparkBase.PersistMode.kPersistParameters);
        leftElevatorMotor.configure(
                leftElevatorMotorConfig,
                SparkBase.ResetMode.kResetSafeParameters,
                SparkBase.PersistMode.kPersistParameters);

        elevatorPID.setGoal(0);
    }

    /**
     * Set the elevator speed in percent
     *
     * @param speed Target percent
     */
    public void setElevatorSpeed(double speed) {
        leftElevatorMotor.set(speed);
        rightElevatorMotor.set(speed);
    }

    /**
     * Set the voltage of the motors for the elevator
     *
     * @param voltage The target voltage
     */
    public void setVoltage(double voltage) {
        leftElevatorMotor.setVoltage(voltage);
        rightElevatorMotor.setVoltage(voltage);
        // Always set voltage for PID and FF
    }

    /**
     * Set the target position for the elevator
     *
     * @param position The target position in meters
     */
    public void setTargetPosition(double position) {
        elevatorPID.setGoal(position);
        double pidCalc = elevatorPID.calculate(getElevatorPosition());
        double ffCalc = elevatorFF.calculate(getElevatorPosition());
        setVoltage(pidCalc + ffCalc);
    }

    /** Reset elevator PID */
    public void resetPID() {
        elevatorPID.reset(getElevatorPosition());
    }

    /**
     * Get the elevator position
     *
     * @return The elevator position in meters
     */
    public double getElevatorPosition() {
        double encoderAveragePos = (leftEncoder.getPosition() + rightEncoder.getPosition()) / 2;
        // Calculates average pos
        return encoderAveragePos * ElevatorConstants.ROTATIONS_TO_METERS;
    }

    /**
     * Get the velocity of the elevator
     *
     * @return The velocity of the elevator in m/s
     */
    public double getElevatorVelocity() {
        double encoderAverageVel = (leftEncoder.getVelocity() + rightEncoder.getVelocity()) / 2;
        // Calculates average pos
        return encoderAverageVel * ElevatorConstants.ROTATIONS_TO_METERS;
    }

    /**
     * Check if elevator is at target position
     *
     * @return If the elevator is at the correct position
     */
    public boolean isAtPosition() {
        return elevatorPID.atSetpoint();
    }

    @Override
    public void periodic() {

        elevatorEncoderPos.set(getElevatorPosition());

        //
        // Elevator zeroing
    }
}
// TODO Add elevator timeout, add boolean position,
