/* Black Knights Robotics (C) 2025 */
package frc.robot.utils;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.wpilibj.Timer;
import java.util.Optional;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.targeting.PhotonPipelineResult;

/** */
public class Camera {
    private Optional<NetworkTablesUtils> limelightTable;

    private Optional<PhotonCamera> photonCamera;
    private Optional<PhotonPoseEstimator> photonPoseEstimator;

    private Transform3d camOffset;
    private double photonTimestamp;
    private double camDist;
    private String name;

    private final CameraType camType;

    //    private static final Logger  LOGGER = LogManager.getLogger();

    /**
     * Util class for cameras
     *
     * @param name The name of the camera
     * @param camType The {@link CameraType} of the camera
     */
    public Camera(String name, CameraType camType, Transform3d camOffset) {
        this.camType = camType;
        this.camOffset = camOffset;
        this.name = name;

        switch (this.camType) {
            case PHOTONVISION -> {
                photonCamera = Optional.of(new PhotonCamera(name));
                photonPoseEstimator =
                        Optional.of(
                                new PhotonPoseEstimator(
                                        AprilTagFieldLayout.loadField(
                                                AprilTagFields.k2025Reefscape),
                                        PhotonPoseEstimator.PoseStrategy
                                                .MULTI_TAG_PNP_ON_COPROCESSOR,
                                        this.camOffset));
                limelightTable = Optional.empty();
            }
            case LIMELIGHT -> {
                photonCamera = Optional.empty();
                photonPoseEstimator = Optional.empty();
                limelightTable = Optional.of(NetworkTablesUtils.getTable(name));
            }
        }
    }

    /**
     * Get the robots position relative to the field (blue side)
     *
     * @param prevPosition The previous position of the robot
     * @return Either an empty optional or the a {@link Pose3d} of the robot
     */
    public Optional<Pose3d> getPoseFieldSpace(Pose3d prevPosition) {
        switch (this.camType) {
            case PHOTONVISION -> {
                assert this.photonPoseEstimator.isPresent();
                assert this.photonCamera.isPresent();

                PhotonPipelineResult res;
                try {
                    res = this.photonCamera.get().getAllUnreadResults().get(0);
                    camDist = res.getBestTarget().getBestCameraToTarget().getX();
                } catch (Exception e) {
                    return Optional.empty();
                }

                this.photonPoseEstimator.get().setReferencePose(prevPosition);
                return photonPoseEstimator
                        .get()
                        .update(res)
                        .map(
                                (e) -> {
                                    this.photonTimestamp = e.timestampSeconds;
                                    return e.estimatedPose;
                                });
            }
            case LIMELIGHT -> {
                return getPose3dLimelight();
            }
        }
        return Optional.empty();
    }

    private Optional<Pose3d> getPose3dLimelight() {
        assert this.limelightTable.isPresent();
        String key = "botpose_wpiblue";

        double[] rawPose = this.limelightTable.get().getArrayEntry(key, new double[0]);
        if (rawPose.length == 0) return Optional.empty();

        return Optional.of(
                new Pose3d(
                                new Translation3d(rawPose[0], rawPose[1], rawPose[2]),
                                new Rotation3d(0.0, 0.0, Math.toRadians(rawPose[5])))
                        .transformBy(camOffset));
    }

    /**
     * Get the timestamp in seconds since the last frame
     *
     * @return The timestamp in seconds since the last frame
     */
    public double getTimestamp() {
        switch (this.camType) {
            case LIMELIGHT -> {
                assert this.limelightTable.isPresent();
                return Timer.getFPGATimestamp()
                        - (this.limelightTable.get().getEntry("tl", Double.POSITIVE_INFINITY)
                                / 1000)
                        - (this.limelightTable.get().getEntry("cl", Double.POSITIVE_INFINITY)
                                / 1000);
            }
            case PHOTONVISION -> {
                assert this.photonCamera.isPresent();

                return photonTimestamp;
            }
        }
        return 0;
    }

    /**
     * Get the distance from the april tag
     *
     * @return The distance in meters from the april tag
     */
    public double getDistanceFromTag() {
        switch (this.camType) {
            case PHOTONVISION -> {
                assert this.photonCamera.isPresent();

                // LOGGER.info(camDist);
                return camDist
                        - ConfigManager.getInstance()
                                .get("photon_camera_offset", 0.4); // TODO: This is stupid
            }
            case LIMELIGHT -> {
                assert this.getPose3dLimelight().isPresent();

                Pose3d pose = this.getPose3dLimelight().get();
                return pose.getZ();
            }
        }

        return -1;
    }

    /**
     * Get the name of the camera
     *
     * @return The name of the camera
     */
    public String getName() {
        return this.name;
    }

    public enum CameraType {
        LIMELIGHT,
        PHOTONVISION
    }
}
