/* Black Knights Robotics (C) 2025 */
package org.blackknights.commands;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Command;
import java.util.function.DoubleSupplier;
import org.blackknights.subsystems.SwerveSubsystem;
import org.blackknights.utils.ConfigManager;
import org.blackknights.utils.NetworkTablesUtils;

/** Command to drive swerve */
public class DriveCommands extends Command {
    private final SwerveSubsystem swerveSubsystem;
    private final DoubleSupplier forward;
    private final DoubleSupplier sideways;
    private final DoubleSupplier radians;
    private final boolean fieldRelativeFromButton;

    /**
     * This class contains all the drive commands for swerve
     *
     * @param swerveSubsystem SwerveSubsystem instance for controlling the swerve drive
     * @param forward The target forward meters/second
     * @param sideways The target sideways meters/second
     * @param radians The target radian for the rotation
     * @param fieldRelative If the swerve should be relative to the robot or the field
     * @param limited If we are limiting the motors
     */
    public DriveCommands(
            SwerveSubsystem swerveSubsystem,
            DoubleSupplier forward,
            DoubleSupplier sideways,
            DoubleSupplier radians,
            boolean fieldRelative,
            boolean limited) {
        this.swerveSubsystem = swerveSubsystem;
        this.forward = forward;
        this.sideways = sideways;
        this.radians = radians;
        this.fieldRelativeFromButton = true;
        addRequirements(swerveSubsystem);
    }

    // Don't write javadoc for wpilib functions
    @Override
    public void execute() {
        double forwardDesired =
                MathUtil.applyDeadband(
                        forward.getAsDouble(),
                        ConfigManager.getInstance().get("controller_deadband", 0.06));
        double sidewaysDesired =
                MathUtil.applyDeadband(
                        sideways.getAsDouble(),
                        ConfigManager.getInstance().get("controller_deadband", 0.06));
        double radiansDesired =
                MathUtil.applyDeadband(
                        radians.getAsDouble(),
                        ConfigManager.getInstance().get("controller_deadband", 0.06));

        NetworkTablesUtils debug = NetworkTablesUtils.getTable("debug");

        debug.setEntry("Forward desired", forwardDesired);
        debug.setEntry("Sideways desired", sidewaysDesired);
        debug.setEntry("Rotation desired", radiansDesired);

        swerveSubsystem.drive(
                forwardDesired,
                sidewaysDesired,
                radiansDesired,
                fieldRelativeFromButton,
                false,
                false);
    }

    @Override
    public void end(boolean interrupted) {
        //        swerveSubsystem.drive(0.0, 0.0, 0.0, true, true, false);
    }
}
