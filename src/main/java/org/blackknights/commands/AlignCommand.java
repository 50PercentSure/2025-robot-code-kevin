/* Black Knights Robotics (C) 2025 */
package org.blackknights.commands;

import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.*;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.blackknights.constants.AlignConstants;
import org.blackknights.framework.Odometry;
import org.blackknights.subsystems.SwerveSubsystem;
import org.blackknights.utils.AlignUtils;
import org.blackknights.utils.ConfigManager;
import org.blackknights.utils.NetworkTablesUtils;

/** Align the robot in fieldspace */
public class AlignCommand extends Command {
    private static final Logger LOGGER = LogManager.getLogger();
    private final SwerveSubsystem swerveSubsystem;

    private final ProfiledPIDController xAxisPid =
            new ProfiledPIDController(
                    AlignConstants.X_AXIS_P,
                    AlignConstants.X_AXIS_I,
                    AlignConstants.X_AXIS_D,
                    AlignConstants.X_AXIS_CONSTRAINTS);

    private final ProfiledPIDController yAxisPid =
            new ProfiledPIDController(
                    AlignConstants.Y_AXIS_P,
                    AlignConstants.Y_AXIS_I,
                    AlignConstants.Y_AXIS_D,
                    AlignConstants.Y_AXIS_CONSTRAINTS);

    private final ProfiledPIDController rotationPid =
            new ProfiledPIDController(
                    AlignConstants.ROTATION_P,
                    AlignConstants.ROTATION_I,
                    AlignConstants.ROTATION_D,
                    AlignConstants.ROTATION_CONSTRAINTS);

    /// Infinite acceleration PIDs
    private final ProfiledPIDController xAxisInfPid =
            new ProfiledPIDController(
                    AlignConstants.X_AXIS_P,
                    AlignConstants.X_AXIS_I,
                    AlignConstants.X_AXIS_D,
                    AlignConstants.X_AXIS_CONSTRAINTS);

    private final ProfiledPIDController yAxisInfPid =
            new ProfiledPIDController(
                    AlignConstants.Y_AXIS_P,
                    AlignConstants.Y_AXIS_I,
                    AlignConstants.Y_AXIS_D,
                    AlignConstants.Y_AXIS_CONSTRAINTS);

    private final Odometry odometry = Odometry.getInstance();
    private final ConfigManager configManager = ConfigManager.getInstance();

    private final String profile;
    private final boolean stopWhenFinished;

    private final Supplier<Pose2d> pose2dSupplier;

    private final NetworkTablesUtils debug = NetworkTablesUtils.getTable("debug");

    private Pose2d targetPos;

    private double timeSenseFinished = -1;
    private boolean doUpdate = true;

    private double distToTarget = Double.MAX_VALUE;

    private double finalX = 0.0;
    private double finalY = 0.0;
    private boolean log = true;

    /**
     * Align to a fieldspace position with odometry
     *
     * @param swerveSubsystem The instance of swerve subsystem // * @param controller The primary
     *     driving {@link edu.wpi.first.wpilibj.XboxController}, used for driver to override vision
     * @param poseSupplier A {@link Supplier<Pose2d>} for poses
     * @param stopWhenFinished Weather to stop swerve or not when the command is complete, set to
     *     false if you are doing multiple paths in a row
     * @param profile The tuning profile to use, generates separate entries in {@link ConfigManager}
     *     for tolerances and trapezoid tuning (DON'T spell it wrong unless you want 10 extra
     *     useless values in cfg manager!!!)
     */
    public AlignCommand(
            SwerveSubsystem swerveSubsystem,
            Supplier<Pose2d> poseSupplier,
            boolean stopWhenFinished,
            String profile) {
        this.swerveSubsystem = swerveSubsystem;
        this.pose2dSupplier = poseSupplier;
        this.stopWhenFinished = stopWhenFinished;
        this.profile = profile;

        LOGGER.debug("Created new align command with '{}' profile", this.profile);

        this.xAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));
        this.yAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));
        this.rotationPid.setTolerance(
                Math.toRadians(
                        configManager.get(
                                String.format("align_%s_rotation_tolerance", this.profile), 1)));

        Pose3d robotPose = Odometry.getInstance().getRobotPose();

        this.rotationPid.enableContinuousInput(-Math.PI, Math.PI);

        this.xAxisPid.reset(
                robotPose.getX(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vxMetersPerSecond);
        this.yAxisPid.reset(
                robotPose.getY(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vyMetersPerSecond);
        this.rotationPid.reset(
                robotPose.getRotation().getZ(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().omegaRadiansPerSecond);

        this.xAxisInfPid.reset(
                robotPose.getX(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vxMetersPerSecond);
        this.yAxisInfPid.reset(
                robotPose.getY(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vyMetersPerSecond);

        this.xAxisPid.setGoal(robotPose.getX());
        this.yAxisPid.setGoal(robotPose.getY());
        this.rotationPid.setGoal(robotPose.getRotation().getZ());

        this.xAxisInfPid.setGoal(robotPose.getX());
        this.yAxisInfPid.setGoal(robotPose.getY());

        this.xAxisPid.calculate(robotPose.getX());
        this.yAxisPid.calculate(robotPose.getY());
        this.rotationPid.calculate(robotPose.getRotation().getZ());

        this.xAxisInfPid.calculate(robotPose.getX());
        this.yAxisInfPid.calculate(robotPose.getY());

        addRequirements(swerveSubsystem);
    }

    @Override
    public void initialize() {
        this.targetPos = pose2dSupplier.get();
        this.timeSenseFinished = -1;
        this.doUpdate = true;
        this.distToTarget = Double.MAX_VALUE;
        this.finalX = 0.0;
        this.finalY = 0.0;
        this.log = true;

        LOGGER.info("Initializing AlignCommand");
        Pose3d robotPose = odometry.getRobotPose();

        // PID updates
        this.xAxisPid.setP(configManager.get("align_x_axis_p", 3));
        this.yAxisPid.setP(configManager.get("align_y_axis_p", 3));
        this.rotationPid.setP(configManager.get("align_rot_p", 7.3));

        this.xAxisInfPid.setP(configManager.get("align_x_axis_p", 3));
        this.yAxisInfPid.setP(configManager.get("align_y_axis_p", 3));

        this.xAxisPid.setD(configManager.get("align_x_axis_d", .25));
        this.yAxisPid.setD(configManager.get("align_y_axis_d", .25));
        this.rotationPid.setD(configManager.get("align_rot_d", 0.5));

        this.xAxisInfPid.setD(configManager.get("align_x_axis_d", .25));
        this.yAxisInfPid.setD(configManager.get("align_y_axis_d", .25));

        this.xAxisPid.setI(configManager.get("align_x_axis_i", 0.0));
        this.yAxisPid.setI(configManager.get("align_y_axis_i", 0.0));
        this.rotationPid.setI(configManager.get("align_rot_i", 0.0));

        this.xAxisInfPid.setI(configManager.get("align_x_axis_i", 0.0));
        this.yAxisInfPid.setI(configManager.get("align_y_axis_i", 0.0));

        this.xAxisPid.setConstraints(
                new TrapezoidProfile.Constraints(
                        configManager.get(String.format("align_%s_x_max_vel_m", this.profile), 3.0),
                        configManager.get(
                                String.format("align_%s_x_max_accel_mps", this.profile), 2.5)));
        this.yAxisPid.setConstraints(
                new TrapezoidProfile.Constraints(
                        configManager.get(String.format("align_%s_y_max_vel_m", this.profile), 3.0),
                        configManager.get(
                                String.format("align_%s_y_max_accel_mps", this.profile), 2.5)));
        this.rotationPid.setConstraints(
                new TrapezoidProfile.Constraints(
                        Math.toRadians(
                                configManager.get(
                                        String.format("align_%s_rot_max_vel_deg", this.profile),
                                        360)),
                        Math.toRadians(
                                configManager.get(
                                        String.format("align_%s_rot_max_accel_degps", this.profile),
                                        360))));

        this.xAxisInfPid.setConstraints(
                new TrapezoidProfile.Constraints(
                        configManager.get(String.format("align_%s_x_max_vel_m", this.profile), 3.0),
                        5000));
        this.yAxisInfPid.setConstraints(
                new TrapezoidProfile.Constraints(
                        configManager.get(String.format("align_%s_y_max_vel_m", this.profile), 3.0),
                        5000));

        this.xAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));
        this.yAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));
        this.rotationPid.setTolerance(
                Math.toRadians(
                        configManager.get(
                                String.format("align_%s_rotation_tolerance", this.profile), 1)));

        this.xAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));
        this.yAxisPid.setTolerance(
                configManager.get(String.format("align_%s_pos_tolerance", this.profile), 0.05));

        // Reset All pids
        this.xAxisPid.reset(
                robotPose.getX(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vxMetersPerSecond);
        this.yAxisPid.reset(
                robotPose.getY(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vyMetersPerSecond);
        this.rotationPid.reset(
                robotPose.getRotation().getZ(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().omegaRadiansPerSecond);

        this.xAxisInfPid.reset(
                robotPose.getX(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vxMetersPerSecond);
        this.yAxisInfPid.reset(
                robotPose.getY(),
                swerveSubsystem.getFieldRelativeChassisSpeeds().vyMetersPerSecond);

        this.xAxisPid.setGoal(targetPos.getX());
        this.yAxisPid.setGoal(targetPos.getY());
        this.rotationPid.setGoal(targetPos.getRotation().getRadians());

        this.xAxisInfPid.setGoal(targetPos.getX());
        this.yAxisInfPid.setGoal(targetPos.getY());

        if (!stopWhenFinished) {
            Pose2d fakePose =
                    AlignUtils.getXDistBack(
                            robotPose.toPose2d(),
                            ConfigManager.getInstance().get("fake_pose_dist_back", 0.5));
            this.xAxisPid.calculate(fakePose.getX());
            this.yAxisPid.calculate(fakePose.getY());
        }
    }

    @Override
    public void execute() {
        Pose3d robotPose = odometry.getRobotPose();

        this.distToTarget =
                Math.pow(robotPose.getX() - targetPos.getX(), 2)
                        + Math.pow(robotPose.getY() - targetPos.getY(), 2);

        double xAxisCalc = this.xAxisPid.calculate(robotPose.getX());
        double yAxisCalc = this.yAxisPid.calculate(robotPose.getY());
        double rotationPidCalc = this.rotationPid.calculate(robotPose.getRotation().getZ());

        double infX = xAxisInfPid.calculate(robotPose.getX());
        double infY = yAxisInfPid.calculate(robotPose.getY());

        debug.setEntry("Dist to target (Error)", distToTarget);

        debug.setEntry("X Pid Error", this.xAxisPid.getPositionError());
        debug.setEntry("Y Pid Error", this.yAxisPid.getPositionError());
        debug.setEntry("Rot Pid Error", this.rotationPid.getPositionError());

        debug.setEntry("X Pid Error (inf)", this.xAxisInfPid.getPositionError());
        debug.setEntry("Y Pid Error (inf)", this.yAxisInfPid.getPositionError());

        debug.setEntry("X Pid setpoint", this.xAxisPid.atSetpoint());
        debug.setEntry("X Pid goal", this.xAxisPid.atGoal());

        debug.setEntry("y Pid setpoint", this.xAxisPid.atSetpoint());
        debug.setEntry("Y Pid goal", this.yAxisPid.atGoal());

        debug.setEntry("Rot Pid setpoint", this.rotationPid.atSetpoint());
        debug.setEntry("Rot Pid goal", this.rotationPid.atGoal());
        debug.setEntry("Robot rotation: ", robotPose.getRotation().getZ());
        debug.setEntry("Rot setpoint", this.rotationPid.getSetpoint().position);

        debug.setEntry(
                "Rot diff",
                Math.abs(
                        Math.abs(this.targetPos.getRotation().getRadians())
                                - Math.abs(odometry.getRobotPose().getRotation().getZ())));

        this.finalX =
                xAxisCalc
                        + (xAxisPid.atGoal() || xAxisPid.atSetpoint()
                                ? 0
                                : Math.signum(xAxisCalc) * configManager.get("align_ff", 0.1));
        this.finalY =
                yAxisCalc
                        + (yAxisPid.atGoal() || yAxisPid.atSetpoint()
                                ? 0
                                : Math.signum(yAxisCalc) * configManager.get("align_ff", 0.1));

        if (!stopWhenFinished) {
            this.finalX = infX;
            this.finalY = infY;
        }

        if (log) {
            log = false;
            LOGGER.info("First commanded speeds: {} {}", xAxisCalc, xAxisCalc);
        }

        debug.setEntry("Xms", finalX);
        debug.setEntry("Yms", finalY);
        debug.setEntry("Rrads", rotationPidCalc);

        this.debug.setArrayEntry(
                "target_pose",
                new double[] {
                    this.targetPos.getX(),
                    this.targetPos.getY(),
                    this.targetPos.getRotation().getRadians()
                });

        swerveSubsystem.drive(finalX, finalY, rotationPidCalc, true, !stopWhenFinished, true);

        if (checkAtGoal() && doUpdate) {
            LOGGER.info("Hit goal, waiting for time to expire");
            this.timeSenseFinished = Timer.getFPGATimestamp() * 1000;
            this.doUpdate = false;
        }
    }

    @Override
    public boolean isFinished() {
        return checkAtGoal()
                && Timer.getFPGATimestamp() * 1000 - this.timeSenseFinished
                        > configManager.get(
                                String.format("align_%s_finish_time", this.profile), 200.0);
    }

    @Override
    public void end(boolean interrupted) {
        if (stopWhenFinished) swerveSubsystem.zeroVoltage();
        else swerveSubsystem.drive(this.finalX, this.finalY, 0.0, true, true, true);
        LOGGER.info("Final commanded speeds: {} {}", this.finalX, this.finalY);
    }

    private boolean checkAtGoal() {
        return (stopWhenFinished && xAxisPid.atGoal() && yAxisPid.atGoal() && rotationPid.atGoal())
                || (!stopWhenFinished
                        && this.distToTarget
                                <= Math.pow(
                                        configManager.get(
                                                String.format(
                                                        "align_%s_pos_dist_tol", this.profile),
                                                0.05),
                                        2)
                        && rotationPid.atGoal());
    }
}
