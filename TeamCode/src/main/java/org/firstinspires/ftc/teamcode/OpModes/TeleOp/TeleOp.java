package org.firstinspires.ftc.teamcode.OpModes.TeleOp;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.arcrobotics.ftclib.controller.PIDController;
import com.arcrobotics.ftclib.controller.wpilibcontroller.ProfiledPIDController;
import com.arcrobotics.ftclib.trajectory.TrapezoidProfile;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.arcrobotics.ftclib.controller.PIDController;

import org.firstinspires.ftc.teamcode.Subsystems.Scoring.Arm;
import org.firstinspires.ftc.teamcode.Subsystems.Scoring.Intake;
import org.firstinspires.ftc.teamcode.Subsystems.Scoring.Constants;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;

@com.qualcomm.robotcore.eventloop.opmode.TeleOp(name = "CenterStage_TeleOp")
public class TeleOp extends LinearOpMode {

    public SampleMecanumDrive driveTrain;
    private PIDController controller;
    public DcMotorEx leftSlide, rightSlide;
    public Arm armSystem;
    public Intake intakeSystem;
    public static int target = 0;
    public static boolean rightSlideRest = true;
    public static boolean scoreAllowed = false;
    public static boolean tiltBox = false;

    public enum SpeedState {
        NORMAL(0.5),
        FAST(0.9);
        double multiplier = 0.5; //Default

        SpeedState(double value) {
            this.multiplier = value;
        }
    }

    SpeedState speedState;

    @Override
    public void runOpMode() throws InterruptedException {
        driveTrain = new SampleMecanumDrive(hardwareMap);
        armSystem = new Arm(hardwareMap);
        intakeSystem = new Intake(hardwareMap);
        controller = new PIDController(Constants.Kp, Constants.Ki, Constants.Kd);

        rightSlide = hardwareMap.get(DcMotorEx.class, "rightSlide");
        leftSlide = hardwareMap.get(DcMotorEx.class, "leftSlide");

        rightSlide.setMode(DcMotorEx.RunMode.STOP_AND_RESET_ENCODER);
        rightSlide.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightSlide.setMode(DcMotorEx.RunMode.RUN_WITHOUT_ENCODER);
        rightSlide.setDirection(DcMotor.Direction.REVERSE);

        leftSlide.setMode(DcMotorEx.RunMode.STOP_AND_RESET_ENCODER);
        leftSlide.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        leftSlide.setMode(DcMotorEx.RunMode.RUN_WITHOUT_ENCODER);
        leftSlide.setDirection(DcMotor.Direction.FORWARD);


        armSystem.init(); //Depowers
        intakeSystem.init();


        speedState = SpeedState.NORMAL;
        rightSlideRest = true;
        scoreAllowed = false;
        tiltBox = false;
        target = 0;


        if (isStopRequested()) return;
        while (!isStopRequested()) {
            while (opModeIsActive()) {


                if(gamepad1.left_bumper) {
                    speedState = SpeedState.NORMAL;
                } else if(gamepad1.right_bumper) {
                    speedState = SpeedState.FAST;
                }

                driveTrain.setWeightedDrivePower(
                        new Pose2d(
                                -gamepad1.left_stick_y * speedState.multiplier,
                                -gamepad1.left_stick_x * speedState.multiplier,
                                -gamepad1.right_stick_x * speedState.multiplier
                        )
                );

                armSystem.loop(gamepad2);
                intakeSystem.loop(gamepad2);

                if (gamepad1.square) {
                    target = Constants.LIFT_FIRST_LEVEL;
                } else if (gamepad1.triangle) {
                    target = Constants.LIFT_SECOND_LEVEL;
                } else if (gamepad1.circle) {
                    target = Constants.LIFT_THIRD_LEVEL;
                } else if (gamepad1.cross) {
                    armSystem.armIdle();
                    target = Constants.LIFT_LEVEL_ZERO;
                }

                controller.setPID(Constants.Kp, Constants.Ki, Constants.Kd);
                int leftPosition = leftSlide.getCurrentPosition();
                double pid = controller.calculate(leftPosition, target);
                double power = pid + Constants.Kf;
                if (pid < 0) { // Going down
                    power = Math.max(power, -0.17);
                    scoreAllowed = false;
                } else { //Going up
                    power = Math.min(power, 1); //Power Range 0 -> 1;
                }
                leftSlide.setPower(power);
                rightSlide.setPower(power);
                if(leftSlide.getCurrentPosition() > 15) {
                    rightSlideRest = false;
                    scoreAllowed = true;
                }
                if(pid < 0) {
                    armSystem.armIdle();
                }
                if(scoreAllowed) {
                    if(gamepad2.triangle) {
                        tiltBox = true;
                    }
                    if(tiltBox) {
                        armSystem.armScore();
                    } else {
                        armSystem.armIdle();
                    }

                }

                if( (target == 0)  ) { //Ensure Lifts are Fully Down (Observation: Right Slide Mainly Issues)
                    armSystem.armIdle();
                    scoreAllowed = false;
                    tiltBox = false;
                    while( (rightSlide.getCurrentPosition() > 1 || rightSlide.getCurrentPosition() <= -1) && !rightSlideRest) {
                        rightSlide.setPower( (Math.signum(rightSlide.getCurrentPosition() * -1) * 0.3) );
                        if(rightSlide.getCurrentPosition() < 1 || rightSlide.getCurrentPosition() >= -1) {
                            rightSlideRest = true;
                            rightSlide.setPower(0);
                            break;
                        }
                    }
                    while(leftSlide.getCurrentPosition() > 0) {
                        leftSlide.setPower(-0.3);
                        if(leftSlide.getCurrentPosition() == 0) {
                            leftSlide.setPower(0);
                            break;
                        }
                    }
                }

                if(rightSlideRest) {
                    armSystem.dePower();
                    scoreAllowed = false;
                    tiltBox = false;
                }

                telemetry.addData("leftPos", leftPosition);
                telemetry.addData("rightPos", rightSlide.getCurrentPosition());
                telemetry.addData("target", target);
                telemetry.addData("Calculated PID", pid);
                telemetry.addData("Slides Power", power);
                telemetry.addData("Slide Direction:", pid < 0 ? "Down" : "Up");
                telemetry.addData("Right Slide @ Rest", rightSlideRest);
                telemetry.update();
            }
        }
    }
}


