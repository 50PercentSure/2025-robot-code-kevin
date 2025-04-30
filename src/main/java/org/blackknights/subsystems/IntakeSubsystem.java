/* Black Knights Robotics (C) 2025 */
package org.blackknights.subsystems;

import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import org.blackknights.constants.ArmConstants;
import org.blackknights.utils.NetworkTablesUtils;

/** Subsystem to manage the intake (NOT HAND) */
public class IntakeSubsystem extends SubsystemBase {
    private final WPI_TalonSRX motor = new WPI_TalonSRX(ArmConstants.MOTOR_ID);

    private final DigitalInput intakeLinebreak = new DigitalInput(1);

    /** Create a new intake subsystem */
    public IntakeSubsystem() {
        motor.setInverted(true);
        motor.enableCurrentLimit(true);
        motor.configContinuousCurrentLimit(20);
        motor.configPeakCurrentLimit(0);
    }

    /**
     * Set the speed of the intake
     *
     * @param speed The target speed in percent (0-1)
     */
    public void setSpeed(double speed) {
        motor.set(speed);
    }

    /**
     * Set the voltage of the intake
     *
     * @param voltage The target voltage (0-12)
     */
    public void setVoltage(double voltage) {
        motor.setVoltage(voltage);
    }

    public boolean getLinebreak() {
        return !intakeLinebreak.get();
    }

    @Override
    public void periodic() {
        NetworkTablesUtils.getTable("debug").setEntry("Intake/linebreak", this.getLinebreak());
    }
}
