package com.example.catapulter;

import static java.lang.Math.abs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.renderscript.Sampler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.MotionEventCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.function.LongBinaryOperator;


public class CatapulterView extends View {
    Context mContext;

    CatapulterViewInterface mCatapulterViewInterface;

    private FirebaseDatabase mFbDb;
    private DatabaseReference mFbDbRef_Catapulter;
    private DatabaseReference mFbDbRef_Catapulter_Angle;
    private DatabaseReference mFbDbRef_Catapulter_Speed;
    private DatabaseReference mFbDbRef_HitCount;
    private DatabaseReference mFbDbRef_HitCountHighScore;

    //Touch Event Variables
    private float mWidth;                   //canvas Width
    private float mHeight;                  //canvas Height
    private long mDelayTime;
    private long mLastTime;
    private float mGestureStartX, mGestureStartY;

    //Text Paint Parameters
    private Paint mTextPaint1;

    //Catapulter View Parameters
    private Paint mPaintCatapulterLaunch;

    //Surroundings, Walls, ground etc
    private Path mGroundPath;
    private float mGroundElevation;

    //CatapulterAilien Views
    private Alien mAlien;

    //Catapulter Ball
    private CatapulterBall mCatapulterBall1;

    //Catapult Base
    private CatapultUnit mCatapultUnit;

    //Catapult Launch Control
    private CatapultLaunchThread mCatapultLaunchThread;
    private double catLaunchSpeed;
    private double catLaunchAngle;

    //CataPult LaunchBar
    private Drawable mLaunchBarLaunchDrawable;
    private Rect mLaunchBarLaunchRect;

    private Drawable mLaunchBarChargeDrawable;
    private Rect mLaunchBarChargeRect;

    private Drawable mLaunchBarSensorActivationDrawable;
    private Rect mLaunchBarSensorActivationRect;

    //Thread Parameters
    private FlyingBallThread mFlyingBallThread = new FlyingBallThread();
    private Handler mUI_AnimHandler;

    //HitThread Params
    private alienHitAnimThread mAlienHitAnimThread = new alienHitAnimThread();
    private Path mAlienHitPath;


    public CatapulterView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public CatapulterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    //Initialization
    private void init(){
        catLaunchAngle = 45; //Collected by firebase data
        catLaunchSpeed = 20; //Collected from firebase data

        //FIREBASE DEF
        mFbDb = FirebaseDatabase.getInstance();
        mFbDbRef_Catapulter = mFbDb.getReference().child("CatapulterData");
        mFbDbRef_Catapulter_Angle = mFbDbRef_Catapulter.child("CatapultAngle");
        mFbDbRef_Catapulter_Speed = mFbDbRef_Catapulter.child("CatapultSpeed");
        mFbDbRef_HitCount = mFbDbRef_Catapulter.child("HitScore");
        mFbDbRef_HitCountHighScore = mFbDbRef_Catapulter.child("HitHighScore");

        mFbDbRef_Catapulter.child("LoginData").setValue("hi");

        mFbDbRef_Catapulter.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                catLaunchAngle = Double.parseDouble(snapshot.child("CatapultAngle").getValue().toString());
                catLaunchSpeed = Double.parseDouble(snapshot.child("CatapultSpeed").getValue().toString());
                mCatapultUnit.setCatapultArmAngleStop(catLaunchAngle);
                mCatapulterBall1.setBallInitialVelocity((float)catLaunchSpeed);
                invalidate();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        //END FIREBASE

        mUI_AnimHandler = new Handler(Looper.getMainLooper());
        mFlyingBallThread.start(); //start's thread so that it is available. Work is passed to this thread via mFlyingBallRunnable passed to mFlyingBallHandler

        mTextPaint1 = new Paint();
        mTextPaint1.setColor(Color.BLACK);
        mTextPaint1.setAntiAlias(true);
        mTextPaint1.setTextSize(50);

        mPaintCatapulterLaunch = new Paint();
        mPaintCatapulterLaunch.setAntiAlias(true);
        mPaintCatapulterLaunch.setARGB(255,161,198,87); //Green
        mPaintCatapulterLaunch.setStrokeWidth(5);

        mCatapultUnit = new CatapultUnit();
        mCatapultUnit.setCatapultArmAngleStart(-45);
        mCatapultUnit.setCatapultArmAngleStop(catLaunchAngle);

        mCatapulterBall1 = new CatapulterBall(50,5);
        mCatapulterBall1.setBallInitialVelocity((float)catLaunchSpeed);

        //mCatapulterBall1.setBallPos_Y(mCatapultUnit.getCatapultBaseRect().top);
        //mCatapulterBall1.setBallPos_X(mCatapultUnit.getCatapultBaseRect().left);
        mCatapultUnit.setCatapulterBall(mCatapulterBall1);

        mAlien = new Alien();
        mAlien.setCatapulterBall(mCatapulterBall1); //Gives the Alien access to the ball position, so that the eyeballs can follow it.
        mFbDbRef_HitCountHighScore.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                mAlien.setAlienBallHitCountHighScore( Integer.parseInt(snapshot.getValue().toString()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        //Surroundings
        mGroundPath = new Path();
        mGroundElevation = mHeight*0.85f;
        mAlien.setAlienFootElevation(mGroundElevation*0.5f);

        //AlienHitThreadInits
        mAlienHitAnimThread = new alienHitAnimThread();
        mAlienHitAnimThread.start();
        mAlienHitPath = new Path();

        //CatapultLaunchThread inits
        mCatapultLaunchThread = new CatapultLaunchThread();
        mCatapultLaunchThread.start(); //Starts the thread, so that it runs with the looper
        SystemClock.sleep(20); //Adds slight delay so that thread looper can be initiated properly

        //LaunchBar - Details set in onsizechanged
        mLaunchBarLaunchRect = new Rect();
        mLaunchBarLaunchDrawable = getResources().getDrawable(R.drawable.rocket_black_48dp,null);
        mLaunchBarLaunchDrawable.setBounds(mLaunchBarLaunchRect); //If Rect is changed, bounds must be re-set.

        mLaunchBarChargeRect = new Rect();
        mLaunchBarChargeDrawable = getResources().getDrawable(R.drawable.charger_volt_48dp,null);
        mLaunchBarLaunchDrawable.setBounds(mLaunchBarChargeRect);//If Rect is changed, bounds must be re-set.

        mLaunchBarSensorActivationRect = new Rect();
        mLaunchBarSensorActivationDrawable = getResources().getDrawable(R.drawable.motion_sensor_active_z48,null);
        mLaunchBarSensorActivationDrawable.setBounds(mLaunchBarSensorActivationRect);

    }
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);


        canvas.drawCircle((float)mCatapulterBall1.getBallPos_X(),(float)mCatapulterBall1.getBallPos_Y(),(float)mCatapulterBall1.getBallDiam()/2f,mPaintCatapulterLaunch);
        canvas.drawPath(mCatapulterBall1.getVelVectPath_X(), mCatapulterBall1.getVelVectPaint());
        canvas.drawPath(mCatapulterBall1.getVelVectPath_Y(), mCatapulterBall1.getVelVectPaint());

        mCatapultUnit.getCatapultBaseDrawable().draw(canvas);
        //canvas.drawArc(mCatapultUnit.getCatapultArcRectF(),270,90,false,mCatapultUnit.getCatapultArcPaint());
        canvas.drawPath(mCatapultUnit.getCatapultLaunchVectorPath(),mCatapultUnit.getCatapultArcPaint());

        canvas.drawPath(mCatapultUnit.getCatapultArmPath(), mCatapultUnit.getCatapultArmPaint());

        mAlien.getAlienDrawable().draw(canvas);
        canvas.drawPath(mAlien.getAlienLeftFootPath(), mAlien.getAlienPathPaint());
        canvas.drawPath(mAlien.getAlienRightFootPath(), mAlien.getAlienPathPaint());
        canvas.drawPath(mAlien.getAlienLeftArmPath(), mAlien.getAlienPathPaint());
        canvas.drawPath(mAlien.getAlienRightArmPath(), mAlien.getAlienPathPaint());

        canvas.drawPath(mAlien.getAlienEyeLeftPath(), mAlien.getAlienPathPaint());
        canvas.drawPath(mAlien.getAlienEyeRightPath(), mAlien.getAlienPathPaint());

        canvas.drawCircle((float)mAlien.getAlienLeftEyeCircleX(),(float)mAlien.getAlienLeftEyeCircleY(), (float)mAlien.getAlienEyeRadius(),mAlien.getAlienPathPaint());
        canvas.drawCircle((float)mAlien.getAlienRightEyeCircleX(),(float)mAlien.getAlienRightEyeCircleY(), (float)mAlien.getAlienEyeRadius(),mAlien.getAlienPathPaint());
        //EyeBall - Follows the Ball

        mAlien.sCalculateEyeBallProperties();
        canvas.drawCircle((float)mAlien.getAlienLeftEyeBallCircleX(),(float)mAlien.getAlienLeftEyeBallCircleY(), (float)mAlien.getAlienEyeballRadius(),mAlien.getAlienEyeBallPaint());
        canvas.drawCircle((float)mAlien.getAlienRightEyeBallCircleX(),(float)mAlien.getAlienRightEyeBallCircleY(), (float)mAlien.getAlienEyeballRadius(),mAlien.getAlienEyeBallPaint());

        canvas.drawPath(mGroundPath, mCatapulterBall1.getVelVectPaint());

        //TEXT_Info - Speed and Angle and scores
        canvas.drawText(String.format("%d",mAlien.getmAlienBallHitCount()),mWidth*0.1f, mHeight*0.05f, mTextPaint1);
        canvas.drawText(String.format("%d",mAlien.getmAlienBallHitCountHighScore()),mWidth*0.2f, mHeight*0.05f, mTextPaint1);

        canvas.drawPath(mAlienHitPath, mAlien.mAlienPathPaint); //

        //LaunchBar
        mLaunchBarLaunchDrawable.draw(canvas);
        mLaunchBarChargeDrawable.draw(canvas);
        mLaunchBarSensorActivationDrawable.draw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = (float)w;
        mHeight= (float)h;

        mGroundElevation = mHeight*0.85f;
        mAlien.setAlienFootElevation(mGroundElevation);//*0.7f);
        mCatapultUnit.setCatapultBaseElevation(mGroundElevation);

        mCatapulterBall1.setBallPos_Y((float)mCatapultUnit.getCatapultBallCenter_Y());
        mCatapulterBall1.setBallPos_X((float)mCatapultUnit.getCatapultBallCenter_X());
        mGroundPath.reset();
        mGroundPath.moveTo(mWidth*0.05f, mGroundElevation);
        mGroundPath.lineTo(mWidth*0.95f, mGroundElevation);

        //LAUNCHBAR
        Integer launchBarWidth; //=110;
        launchBarWidth = (int)((h - (int)mGroundElevation)*0.9); //90% of view below mGroundElevation = launchBarWidth
        mLaunchBarLaunchRect.left = (int)(w/2) - launchBarWidth/2;
        mLaunchBarLaunchRect.top  = (int)mGroundElevation+launchBarWidth/12;
        mLaunchBarLaunchRect.right = mLaunchBarLaunchRect.left+launchBarWidth;
        mLaunchBarLaunchRect.bottom = mLaunchBarLaunchRect.top+launchBarWidth;
        mLaunchBarLaunchDrawable.setBounds(mLaunchBarLaunchRect);

        mLaunchBarChargeRect.left = mLaunchBarLaunchRect.left-(int)(w/4) - launchBarWidth/2;
        mLaunchBarChargeRect.top = mLaunchBarLaunchRect.top;
        mLaunchBarChargeRect.right = mLaunchBarChargeRect.left+launchBarWidth;
        mLaunchBarChargeRect.bottom = mLaunchBarChargeRect.top + launchBarWidth;
        mLaunchBarChargeDrawable.setBounds(mLaunchBarChargeRect);

        mLaunchBarSensorActivationRect.left = mLaunchBarLaunchRect.right + (int)(w/4)-launchBarWidth/2;
        mLaunchBarSensorActivationRect.top = mLaunchBarLaunchRect.top;
        mLaunchBarSensorActivationRect.right = mLaunchBarSensorActivationRect.left +launchBarWidth;
        mLaunchBarSensorActivationRect.bottom = mLaunchBarSensorActivationRect.top + launchBarWidth;
        mLaunchBarSensorActivationDrawable.setBounds(mLaunchBarSensorActivationRect);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        int actionType = event.getAction();

        switch (actionType){
            case MotionEvent.ACTION_DOWN: {
                final float StartX, StartY;
                StartX = event.getX();
                StartY = event.getY();
                mGestureStartX = StartX;
                mGestureStartY = StartY;
                break;
            }
            case MotionEvent.ACTION_UP: {
                catLaunchSpeed = catLaunchSpeed + 1;

                break;
            }
            case MotionEvent.ACTION_MOVE:{

                final float MoveX, MoveY, dx, dy;
                MoveX = event.getX();
                MoveY = event.getY();

                dx = (mGestureStartX - MoveX)/100;
                dy = (mGestureStartY - MoveY)/100;

                if (Math.abs(dx) >= 1) {
                    mGestureStartX = MoveX;
                    catLaunchAngle -= dx;
                    mCatapulterBall1.setBallInitialVelocity_Angle_deg((float)catLaunchAngle);
                    mCatapultUnit.setCatapultArmAngleStop(catLaunchAngle);
                    mFbDbRef_Catapulter_Angle.setValue(catLaunchAngle);
                    invalidate();
                }

                if (Math.abs(dy) >= 1){
                    catLaunchSpeed += dy;
                    mCatapulterBall1.setBallInitialVelocity((float)catLaunchSpeed);
                    mFbDbRef_Catapulter_Speed.setValue(catLaunchSpeed);
                    mGestureStartY = MoveY;
                    invalidate();
                }

                break;
            }
        }

        //-----------------------------------------------------
        // Create a rectangle from the point of touch - THis rectangle is checked for intersection with other "rectangles", ref below.
        Rect touchPoint = new Rect((int)x-1,(int)y-1,(int)x+1,(int)y+1);

        if(mLastTime < 0)
        {
            mLastTime = System.currentTimeMillis(); //preventing from too speedy touches.
        }
        else
        {
            if(System.currentTimeMillis() - mLastTime < mDelayTime) //how much time you decide
            {
                return true; //ignore this event, but still treat it as handled
            }
            else {
                mDelayTime = 25; //Resets delay-time
                mLastTime = System.currentTimeMillis();

                if (touchPoint.intersect(mLaunchBarChargeRect)) {
                    mDelayTime = 1000; //Ensures delay is made longer than normal (to avoid sudden disconnection by finger touch)

                    Snackbar.make(this, "CHARGE CATAPULT?", Snackbar.LENGTH_SHORT).setAction("YES", new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mCatapulterViewInterface.catapultStartChargeEvent("START_CHARGE",85);
                        }
                    }).show();
                    //-------------------------------

                } else if (touchPoint.intersect(mLaunchBarLaunchRect)) {
                    mDelayTime = 1000; //Ensures delay is made longer than normal (to avoid sudden disconnection by finger touch)

                    Snackbar.make(this, "LAUNCH BALL?", Snackbar.LENGTH_SHORT).setAction("YES", new OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            mCatapulterBall1.setBallPos_X_Max(mWidth*0.95f);
                            mCatapulterBall1.setBallPos_Y_Max(mGroundElevation);
                            launchCatapult(catLaunchAngle, catLaunchSpeed);
                            mCatapulterViewInterface.catapultLaunchEvent("LAUNCH_CATAPULT",13);
                            //launchFlyingBall(mCatapulterBall1); //Launched within launchCatapult thread

                        }
                    }).show();
                    //-------------------------------

                }
            }
        }

        return true;//super.onTouchEvent(event);

    }

    //INTERFACES
    public void setCatapulterViewInterface(CatapulterViewInterface interfaceListener){
        mCatapulterViewInterface = interfaceListener;
    }
    public interface CatapulterViewInterface{

        public void catapultLaunchEvent(String launchCommandString, double catapultPowerValue); //ModeNr4 --> "LAUNCH_CATAPULT"
        public void catapultAlienHitEvent(Integer hitCount);
        public void catapultStartChargeEvent(String chargeStartCommandString, double catapultDutyCyclePercent); //ModeNr1 --> "START_CHARGE"
        public void catapultStopChargeEvent(String chargeStopCommandString); //ModeNr2 --> "STOP_CHARGE"
        public void catapultGetVoltageEvent(String getVoltageCommandString); //ModeNr6 --> Command: "GET_VOLTAGE"


    }

    //ANIMATION THREAD SUBCLASSES
    private void launchFlyingBall(CatapulterBall ball){

        FlyingBallRunnable flyingBallRunnable = new FlyingBallRunnable(ball);
        Handler flyingBallHandler = new Handler(mFlyingBallThread.looper);
        flyingBallHandler.post(flyingBallRunnable); //This starts flyingBallRunnable on the mFlyingBallThread.

    }

    private class FlyingBallThread extends Thread{

        Looper looper;

        @Override
        public void run() {

            looper.prepare();
            looper = Looper.myLooper();
            looper.loop();

        }
    }

    private class FlyingBallRunnable implements Runnable{

        /*
            Runnable which calculated next point for catapulterball based on physics

         */
        private CatapulterBall mBall;

        private int dt_ms=20;                           //(time_step) for update of view
        private double dt_s;
        private double t_act;                           //current time
        private double t, t0;
        private double t_0;                             //t_act = t_0 + dt
        private double vX_0, vY_0, v_net_0;             //velocity initial in x and y direction
        private double vX_t, vY_t, v_net_t;             //velocity actual (speed)
        private double aY_0, aX_0, a_net_0;             //velocity initial in y_direction
        private double X_0, Y_0;                        //Position initial
        private double X_t0, Y_t0;                      //Position initial dt before t
        private double X_t, Y_t;                        //Position actual
        private double cD_X, cD_Y, cD_net_0, cD_net_t;  //Drag components
        private double theta_t;                         //Speed angle at t_act

        private Path xVectPath, yVectPath, velVectPath; //Path drawn to show arrows
        private Paint xyVectPaint;

        private double canvas_Scale_X, canvas_Scale_Y;

        public FlyingBallRunnable(CatapulterBall catapulterBall) {
            this.mBall = catapulterBall;

            //Initializaiton
            canvas_Scale_X = mWidth/100; //
            canvas_Scale_Y = mHeight/100;

            dt_s = (double)dt_ms/1000;

            t_0 = 0;
            t0=0; //Starting point
            X_0 = mBall.getBallPos_X();
            Y_0 = mBall.getBallPos_Y();

            X_t0 = X_0;
            Y_t0 = Y_0;

            vX_0 = mBall.getBallInitialVelocity()*Math.cos((double)mBall.getBallInitialVelocity_Angle_deg()*2*Math.PI/360);
            vY_0 = mBall.getBallInitialVelocity()*Math.sin((double)mBall.getBallInitialVelocity_Angle_deg()*2*Math.PI/360);
            aY_0 = -9.81;
            aX_0 = 0;

            // Vector Paths
            xVectPath = mBall.getVelVectPath_X();
            yVectPath = mBall.getVelVectPath_Y();
            velVectPath = mBall.getVelVectPath_XY();

        }

        @Override
        public void run() {

            for (int i = 0; i <= 10000; i++) {
                SystemClock.sleep(dt_ms);

                //t_act += (double)dt_s;
                //t_0 = t_act;
                t = t0 + (double)dt_s;

                //X_Pos (No accelleration in direction (no drag)

                vX_t = vX_0 + canvas_Scale_X * aX_0*(t-t0);
                X_t = X_t0 + canvas_Scale_X * (0.5*aX_0*t*t + 0.5*aX_0*t0*t0 - aX_0*t0*t + vX_0*t - vX_0*t0);

                vY_t = vY_0 + canvas_Scale_Y * aY_0*(t-t0);
                Y_t = Y_t0 - canvas_Scale_Y * (0.5*aY_0*t*t + 0.5*aY_0*t0*t0 - aY_0*t0*t + vY_0*t - vY_0*t0);

                t_0 = t;

                vY_0 = vY_t;
                vX_0 = vX_t;
                X_t0 = X_t;
                Y_t0 = Y_t;

                if (Y_t > mBall.fBallPos_Y_Max-mBall.getBallDiam()*0.5) //Bounce up again from bottom
                {
                    if (vY_t < 0)
                    {
                        vY_t = vY_t*-1;
                        vY_t = vY_t*0.9; //Reduced to 85% of it's value for each bounce
                        vY_0 = vY_t;

                        vX_t = vX_t*0.9; //Reduces horisontal speed also.
                        vX_0 = vX_t;

                    }
                }

                if (X_t > mWidth - mBall.getBallDiam()) //Bounce back from right wall
                {
                    if (vX_t > 0)
                    {
                        vX_t = vX_t*-1;
                        vX_0 = vX_t;
                    }
                }

                if (X_t < mBall.getBallDiam()) //Bounce back from left wall
                {
                    if (vX_t < 0)
                    {
                        vX_t = vX_t*-1;
                        vX_0 = vX_t;
                    }
                }


                if (i == 1000)
                {
                    X_t = X_0;
                    Y_t = Y_0;
                }

                //DRAWING VECTOR LINES (X, Y and Velocity)
                yVectPath.reset();
                if (vY_t < 0) //If falling
                {
                    yVectPath.moveTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam());
                    yVectPath.lineTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t);
                    //Arrow
                    yVectPath.lineTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t-6);
                    yVectPath.lineTo((float)X_t+6, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t-6);
                    yVectPath.lineTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t);
                    yVectPath.lineTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t-6);
                    yVectPath.lineTo((float)X_t-6, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t-6);
                    yVectPath.lineTo((float)X_t, (float)Y_t + (float)mBall.getBallDiam()-(float)vY_t);
                }
                else
                {
                    yVectPath.moveTo((float)X_t,     (float)Y_t - (float)mBall.getBallDiam());
                    yVectPath.lineTo((float)X_t,     (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t);
                    //Arrow
                    yVectPath.lineTo((float)X_t,      (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t+6);
                    yVectPath.lineTo((float)X_t+6, (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t+6);
                    yVectPath.lineTo((float)X_t,      (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t);
                    yVectPath.lineTo((float)X_t,      (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t+6);
                    yVectPath.lineTo((float)X_t-6, (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t+6);
                    yVectPath.lineTo((float)X_t,      (float)Y_t - (float)mBall.getBallDiam()-(float)vY_t);
                }

                xVectPath.reset();
                if (vX_t > 0) //If moving to the right
                {
                    xVectPath.moveTo((float)X_t + (float)mBall.getBallDiam(), (float)Y_t);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                    //ARROW
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t - 6, (float)Y_t);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t - 6, (float)Y_t + 6);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t - 6, (float)Y_t - 6);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t - 6, (float)Y_t);
                    xVectPath.lineTo((float)X_t + (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                }
                else
                {
                    xVectPath.moveTo((float)X_t - (float)mBall.getBallDiam(), (float)Y_t);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                    //ARROW(float)
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t + 6, (float)Y_t);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t + 6, (float)Y_t + 6);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t + 6, (float)Y_t - 6);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t + 6, (float)Y_t);
                    xVectPath.lineTo((float)X_t - (float)mBall.getBallDiam() + (float)vX_t, (float)Y_t);
                }


                if (abs(vX_0)+ abs(vY_0) < 0.8)
                {
                    i = 10001;
                }

                mUI_AnimHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBall.setBallPos_X((float)X_t);
                        mBall.setBallPos_Y((float)Y_t);
                        invalidate(); //This re-draw's the UI and ensures alien eyes etc move.

                    }
                });


            }

            //Runnable to continue until velocity is < 0.01 m/s

        }
    }




    //PROPERTY SUBCLASSES
    private class CatapulterBall{

        private double fBallDiam;
        private double fBallWeight;
        private double fBallInitialVelocity;
        private double fBallCurrentVelocity;
        private double fBallInitialVelocity_Angle_deg;
        private double fBallPos_X, fBallPos_Y;
        private Path mVelVectPath_X, mVelVectPath_Y, mVelVectPath_XY; //Velocity vector in X and Y_direction and in speed direction
        private Paint mVelVectPaint;
        private Paint mBallPaint;

        private float fBallPos_X_Max, fBallPos_X_Min, fBallPos_Y_Max, fBallPos_Y_Min;

        public CatapulterBall(float diam_m, float weight_kg) {
            fBallDiam = diam_m;
            fBallWeight = weight_kg;

            mBallPaint = new Paint();

            mVelVectPath_X = new Path();
            mVelVectPath_Y = new Path();
            mVelVectPath_XY = new Path();

            mVelVectPaint = new Paint();
            mVelVectPaint.setAntiAlias(true);
            mVelVectPaint.setStrokeWidth(5);
            mVelVectPaint.setColor(Color.GRAY);
            mVelVectPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            fBallPos_X_Max = mWidth;
            fBallPos_Y_Max = mHeight;

        }

        public double getBallDiam() {
            return fBallDiam;
        }

        public void setBallDiam(float dBallDiam) {
            this.fBallDiam = dBallDiam;
        }

        public double getBallWeight() {
            return fBallWeight;
        }

        public void setBallWeight(double dBallWeight) {
            this.fBallWeight = dBallWeight;
        }

        public double getBallInitialVelocity() {
            return fBallInitialVelocity;
        }

        public void setBallInitialVelocity(float fBallInitialVelocity) {
            this.fBallInitialVelocity = fBallInitialVelocity;
        }

        public double getBallPos_X() {
            return fBallPos_X;
        }

        public void setBallPos_X(float fBallPos_X) {
            this.fBallPos_X = fBallPos_X;
        }

        public double getBallPos_Y() {
            return fBallPos_Y;
        }

        public void setBallPos_Y(float fBallPos_Y) {
            this.fBallPos_Y = fBallPos_Y;
        }

        public double getBallInitialVelocity_Angle_deg() {
            return fBallInitialVelocity_Angle_deg;
        }

        public void setBallInitialVelocity_Angle_deg(float fBallInitialVelocity_Angle_deg) {
            this.fBallInitialVelocity_Angle_deg = fBallInitialVelocity_Angle_deg;
        }

        public Path getVelVectPath_X() {
            return mVelVectPath_X;
        }

        public Path getVelVectPath_Y() {
            return mVelVectPath_Y;
        }

        public Path getVelVectPath_XY() {
            return mVelVectPath_XY;
        }

        public Paint getBallPaint() {
            return mBallPaint;
        }

        public Paint getVelVectPaint() {
            return mVelVectPaint;
        }

        public void setBallPos_X_Max(float X_max){
            fBallPos_X_Max = X_max;
        }
        public void setBallPos_Y_Max(float Y_max){
            fBallPos_Y_Max = Y_max; //Bottom basically
        }
    }
    //Alien related classes and threads
    private class Alien{
        //Class only available from within the "CatapulterView" class.
        private Drawable mAlienDrawable;
        private Rect mAlienRect;
        private Path mAlienLeftFootPath, mAlienRightFootPath, mAlienLeftArmPath, mAlienRightArmPath, mAlienEyeLeftPath, mAlienEyeRightPath;
        private Matrix mLeftArmMatrix, mRightArmMatrix;
        private RectF mLeftArmBounds, mRightArmBounds;
        private double mAlienLeftEyeCircleX, mAlienLeftEyeCircleY, mAlienRightEyeCircleX, mAlienRightEyeCircleY, mAlienEyeRadius;
        private Paint mAlienPathPaint, mAlienEyeBallPaint;
        private CatapulterBall mCatapulterBall; //Alien eye-balls will follow the catapulterball.
        private double mEyeBallRadius;
        private double mAlienLeftEyeBallCircleX, mAlienLeftEyeBallCircleY, mAlienRightEyeBallCircleX, mAlienRightEyeBallCircleY;
        private double mLeftEyeBallAngle, mRightEyeBallAngle, mLeftEyeballShiftDistance, mRightEyeballShiftDistance;
        private float mAlienFootElevation;

        private int mAlienBallHitCount;
        private int mAlienBallHitCountHighScore; //Retreived from firebase
        private boolean mCountBallHit;

        //Constructor
        public Alien() {
            init();
        }

        public Alien(Rect mAlienPositionRect){
            init();
            mAlienRect = mAlienPositionRect;

        }

        private void init()
        {
            //mAlienDrawable
            mAlienLeftFootPath = new Path();
            mAlienRightFootPath = new Path();
            mAlienLeftArmPath = new Path();
            mAlienRightArmPath = new Path();
            mAlienEyeLeftPath = new Path();
            mAlienEyeRightPath = new Path();
            mAlienPathPaint = new Paint();
            mAlienEyeBallPaint = new Paint();
            mAlienEyeRadius =12 ;
            mEyeBallRadius = 5;
            mRightEyeballShiftDistance = mAlienEyeRadius-mEyeBallRadius;
            mLeftEyeballShiftDistance = mAlienEyeRadius-mEyeBallRadius;

            mAlienRect = new Rect();

            mAlienDrawable = getResources().getDrawable(R.drawable.alien_body_only,null);
            mAlienDrawable.setBounds(mAlienRect); //If alienRect is changed, bounds must be re-set.

            //Eyes, feet and arms
            mAlienPathPaint.setColor(Color.BLACK);
            mAlienPathPaint.setAntiAlias(true);
            mAlienPathPaint.setStrokeWidth(3);
            mAlienPathPaint.setStyle(Paint.Style.STROKE);

            mAlienEyeBallPaint.setColor(Color.BLACK);

            mAlienEyeBallPaint.setAntiAlias(true);
            mAlienEyeBallPaint.setStrokeWidth(2);
            mAlienEyeBallPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mAlienBallHitCount = 0;
            mCountBallHit = true;

            //Initialize Paths
            mLeftArmMatrix = new Matrix();
            mRightArmMatrix = new Matrix();
            mLeftArmBounds = new RectF();
            mRightArmBounds = new RectF();
        }

        public Drawable getAlienDrawable() {

            mAlienRect.left = (int)mWidth-(int)(mWidth*0.3);
            mAlienRect.right = mAlienRect.left + (int)(mWidth*0.1);
            mAlienRect.bottom = (int)mAlienFootElevation-(int)(mWidth*0.01f)-(int)mAlienPathPaint.getStrokeWidth();
            mAlienRect.top = mAlienRect.bottom - (int)(mWidth*0.1);

            mAlienDrawable.setBounds(mAlienRect);

            return mAlienDrawable;
        }

        //Alien BaseProperties
        public void setAlienFootElevation(float baseElevation)
        {
            mAlienFootElevation = baseElevation;
        }

        //ALIEN FEET and Arms
        public Path getAlienLeftFootPath() {
            mAlienLeftFootPath.moveTo(mAlienRect.left+mAlienRect.width()*0.4f,mAlienRect.bottom - mAlienRect.height()*0.1f);
            mAlienLeftFootPath.lineTo(mAlienRect.left+mAlienRect.width()*0.4f,mAlienRect.bottom + mAlienRect.height()*0.1f);
            mAlienLeftFootPath.lineTo(mAlienRect.left+mAlienRect.width()*0.30f,mAlienRect.bottom + mAlienRect.height()*0.1f);

            return mAlienLeftFootPath;
        }
        public Path getAlienRightFootPath(){
            mAlienRightFootPath.moveTo(mAlienRect.left+mAlienRect.width()*0.6f,mAlienRect.bottom - mAlienRect.height()*0.1f);
            mAlienRightFootPath.lineTo(mAlienRect.left+mAlienRect.width()*0.6f,mAlienRect.bottom + mAlienRect.height()*0.1f);
            mAlienRightFootPath.lineTo(mAlienRect.left+mAlienRect.width()*0.70f,mAlienRect.bottom + mAlienRect.height()*0.1f);

            return mAlienRightFootPath;
        }
        public Path getAlienLeftArmPath() { //'MOVING ARMS'
            //Obs - Moved to init
            float armStartPosX;
            float armStartPosY;
            double armAngle;
            double armLength;

            mAlienLeftArmPath.reset();
            armStartPosX = mAlienRect.right-mAlienRect.width()*0.18f;
            armStartPosY = mAlienRect.bottom - mAlienRect.height()*0.25f;
            mAlienLeftArmPath.moveTo(armStartPosX,armStartPosY);// 'STARTING POINT SAME ALWAYS
            armLength = mAlienRect.width()*0.35;

            //First Quadrant, left eye:
            if(this.mCatapulterBall.getBallPos_X() >= armStartPosX && this.mCatapulterBall.getBallPos_Y() <= armStartPosY) //1st quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*Math.cos(armAngle)),(float)(armStartPosY - armLength*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle-Math.PI/2)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle-Math.PI/2)));

            }
            else if(this.mCatapulterBall.getBallPos_X() <= armStartPosX && this.mCatapulterBall.getBallPos_Y() <= armStartPosY) //2nd quadrant
            {

                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*Math.cos(armAngle)),                                               (float)(armStartPosY - armLength*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY - armLength*0.7*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle+Math.PI/2)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle+Math.PI/2)));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= armStartPosX && this.mCatapulterBall.getBallPos_Y() >= armStartPosY) //3rd quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*Math.cos(armAngle)),                                               (float)(armStartPosY + armLength*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY + armLength*0.7*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle-Math.PI/2)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle-Math.PI/2)));
            }
            else if((double)this.mCatapulterBall.getBallPos_X() >= armStartPosX && this.mCatapulterBall.getBallPos_Y() >= armStartPosY) //4th Quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*Math.cos(armAngle)),(float)(armStartPosY + armLength*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY + armLength*0.7*Math.sin(armAngle)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle+Math.PI/2)));
                mAlienLeftArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle+Math.PI/2)));
            }
            else
            {

            }

            return mAlienLeftArmPath;
        }
        public Path getAlienRightArmPath() {
            //Obs - Moved to init
            float armStartPosX;
            float armStartPosY;
            double armAngle;
            double armLength;

            mAlienRightArmPath.reset();
            armStartPosX = mAlienRect.left+mAlienRect.width()*0.18f;
            armStartPosY = mAlienRect.bottom - mAlienRect.height()*0.25f;
            mAlienRightArmPath.moveTo(armStartPosX,armStartPosY);// 'STARTING POINT SAME ALWAYS
            armLength = mAlienRect.width()*0.35;

            //First Quadrant, left eye:
            if(this.mCatapulterBall.getBallPos_X() >= armStartPosX && this.mCatapulterBall.getBallPos_Y() <= armStartPosY) //1st quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*Math.cos(armAngle)),(float)(armStartPosY - armLength*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle-Math.PI/2)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle-Math.PI/2)));

            }
            else if(this.mCatapulterBall.getBallPos_X() <= armStartPosX && this.mCatapulterBall.getBallPos_Y() <= armStartPosY) //2nd quadrant
            {

                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*Math.cos(armAngle)),(float)(armStartPosY - armLength*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY - armLength*0.7*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle+Math.PI/2)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY - armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle+Math.PI/2)));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= armStartPosX && this.mCatapulterBall.getBallPos_Y() >= armStartPosY) //3rd quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*Math.cos(armAngle)),(float)(armStartPosY + armLength*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY + armLength*0.7*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle-Math.PI/2)));
                mAlienRightArmPath.lineTo((float)(armStartPosX-armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle-Math.PI/2)));
            }
            else if((double)this.mCatapulterBall.getBallPos_X() >= armStartPosX && this.mCatapulterBall.getBallPos_Y() >= armStartPosY) //4th Quadrant
            {
                armAngle = Math.atan((Math.abs((armStartPosY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-armStartPosX)) ));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*Math.cos(armAngle)),(float)(armStartPosY + armLength*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)),                                           (float)(armStartPosY + armLength*0.7*Math.sin(armAngle)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)+armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) - armLength*0.3*Math.sin(armAngle+Math.PI/2)));
                mAlienRightArmPath.lineTo((float)(armStartPosX+armLength*0.7*Math.cos(armAngle)-armLength*0.3*Math.cos(armAngle-Math.PI/2)),(float)(armStartPosY + armLength*0.7*Math.sin(armAngle) + armLength*0.3*Math.sin(armAngle+Math.PI/2)));
            }
            else
            {

            }
            return mAlienRightArmPath;
        }

        //ALIEN EYES
        public Path getAlienEyeLeftPath(){
            mAlienEyeLeftPath.moveTo(mAlienRect.left+mAlienRect.width()*0.15f,mAlienRect.top + mAlienRect.height()*0.21f);
            mAlienEyeLeftPath.lineTo(mAlienRect.left+mAlienRect.width()*0.15f,mAlienRect.top - mAlienRect.height()/4f); //NB: Circle on top

            mAlienLeftEyeCircleX = mAlienRect.left + mAlienRect.width()*0.15f;
            mAlienLeftEyeCircleY = mAlienRect.top - mAlienRect.height()/4f - mAlienEyeRadius;

            return mAlienEyeLeftPath;
        }
        public Path getAlienEyeRightPath(){
            mAlienEyeRightPath.moveTo(mAlienRect.left+mAlienRect.width()*0.59f,mAlienRect.top + mAlienRect.height()*0.2f);
            mAlienEyeRightPath.lineTo(mAlienRect.left+mAlienRect.width()*0.59f,mAlienRect.top - mAlienRect.height()/4f); //NB: Circle on top

            mAlienRightEyeCircleX = mAlienRect.left + mAlienRect.width()*0.59;
            mAlienRightEyeCircleY = mAlienRect.top - mAlienRect.height()/4f - mAlienEyeRadius;

            return mAlienEyeRightPath;
        }

        public double getAlienEyeRadius() {
            return mAlienEyeRadius;
        }

        public double getAlienLeftEyeCircleX() {
            return mAlienLeftEyeCircleX;
        }
        public double getAlienLeftEyeCircleY() {
            return mAlienLeftEyeCircleY;
        }
        public double getAlienRightEyeCircleX() {
            return mAlienRightEyeCircleX;
        }
        public double getAlienRightEyeCircleY() {
            return mAlienRightEyeCircleY;
        }

        //Alien Eyeballs; They follow the ball
        public double getAlienLeftEyeBallCircleX() {
            return mAlienLeftEyeBallCircleX;
        }
        public double getAlienLeftEyeBallCircleY() {
            return mAlienLeftEyeBallCircleY;
        }
        public double getAlienRightEyeBallCircleX() {
            return mAlienRightEyeBallCircleX;
        }
        public double getAlienRightEyeBallCircleY() {
            return mAlienRightEyeBallCircleY;
        }

        public void setCatapulterBall(CatapulterBall catapulterBall) {
            this.mCatapulterBall = catapulterBall;
        }

        public Paint getAlienPathPaint() { //Paint used to draw feet and, arms and eyes
            return mAlienPathPaint;
        }
        public Paint getAlienEyeBallPaint() {
            return mAlienEyeBallPaint;
        }

        public double getAlienEyeballRadius(){
            return mEyeBallRadius;
        }

        public void sResetHitScore(){
            mAlienBallHitCount = 0;
        }
        public int getmAlienBallHitCount(){
            return mAlienBallHitCount;
        }
        public void setAlienBallHitCountHighScore(int highScore){
            mAlienBallHitCountHighScore = highScore;
        }
        public int getmAlienBallHitCountHighScore(){
            return mAlienBallHitCountHighScore;
        }

        public void sCalculateEyeBallProperties(){

            //First Quadrant, left eye:
            if(this.mCatapulterBall.getBallPos_X() >= mAlienLeftEyeCircleX && this.mCatapulterBall.getBallPos_Y() <= mAlienLeftEyeCircleY) //1st quadrant
            {
                mLeftEyeBallAngle = Math.atan((Math.abs(  (mAlienLeftEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienLeftEyeCircleX)) ));
                mAlienLeftEyeBallCircleX = mAlienLeftEyeCircleX + (mLeftEyeballShiftDistance*Math.cos(mLeftEyeBallAngle));
                mAlienLeftEyeBallCircleY = mAlienLeftEyeCircleY - (mLeftEyeballShiftDistance*Math.sin(mLeftEyeBallAngle));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= mAlienLeftEyeCircleX && this.mCatapulterBall.getBallPos_Y() <= mAlienLeftEyeCircleY) //2nd quadrant
            {
                mLeftEyeBallAngle = Math.atan((Math.abs(  (mAlienLeftEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienLeftEyeCircleX)) ));
                mAlienLeftEyeBallCircleX = mAlienLeftEyeCircleX - (mLeftEyeballShiftDistance*Math.cos(mLeftEyeBallAngle));
                mAlienLeftEyeBallCircleY = mAlienLeftEyeCircleY - (mLeftEyeballShiftDistance*Math.sin(mLeftEyeBallAngle));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= mAlienLeftEyeCircleX && this.mCatapulterBall.getBallPos_Y() >= mAlienLeftEyeCircleY) //3rd quadrant
            {
                mLeftEyeBallAngle = Math.atan((Math.abs(  (mAlienLeftEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienLeftEyeCircleX)) ));
                mAlienLeftEyeBallCircleX = mAlienLeftEyeCircleX - (mLeftEyeballShiftDistance*Math.cos(mLeftEyeBallAngle));
                mAlienLeftEyeBallCircleY = mAlienLeftEyeCircleY + (mLeftEyeballShiftDistance*Math.sin(mLeftEyeBallAngle));
            }
            else if((double)this.mCatapulterBall.getBallPos_X() >= mAlienLeftEyeCircleX && this.mCatapulterBall.getBallPos_Y() >= mAlienLeftEyeCircleY) //4th Quadrant
            {
                mLeftEyeBallAngle = Math.atan((Math.abs(  (mAlienLeftEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienLeftEyeCircleX)) ));
                mAlienLeftEyeBallCircleX = mAlienLeftEyeCircleX + (mLeftEyeballShiftDistance*Math.cos(mLeftEyeBallAngle));
                mAlienLeftEyeBallCircleY = mAlienLeftEyeCircleY + (mLeftEyeballShiftDistance*Math.sin(mLeftEyeBallAngle));
            }
            else
            {
                mAlienLeftEyeBallCircleX = mAlienLeftEyeCircleX;
                mAlienLeftEyeBallCircleY = mAlienLeftEyeCircleY;
            }

            //First Quadrant, Right eye:
            if(this.mCatapulterBall.getBallPos_X() >= mAlienRightEyeCircleX && this.mCatapulterBall.getBallPos_Y() <= mAlienRightEyeCircleY) //1st quadrant
            {
                mRightEyeBallAngle = Math.atan((Math.abs(  (mAlienRightEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienRightEyeCircleX)) ));
                mAlienRightEyeBallCircleX = mAlienRightEyeCircleX + (mRightEyeballShiftDistance*Math.cos(mRightEyeBallAngle));
                mAlienRightEyeBallCircleY = mAlienRightEyeCircleY - (mRightEyeballShiftDistance*Math.sin(mRightEyeBallAngle));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= mAlienRightEyeCircleX && this.mCatapulterBall.getBallPos_Y() <= mAlienRightEyeCircleY) //2nd quadrant
            {
                mRightEyeBallAngle = Math.atan((Math.abs(  (mAlienRightEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienRightEyeCircleX)) ));
                mAlienRightEyeBallCircleX = mAlienRightEyeCircleX - (mRightEyeballShiftDistance*Math.cos(mRightEyeBallAngle));
                mAlienRightEyeBallCircleY = mAlienRightEyeCircleY - (mRightEyeballShiftDistance*Math.sin(mRightEyeBallAngle));
            }
            else if(this.mCatapulterBall.getBallPos_X() <= mAlienRightEyeCircleX && this.mCatapulterBall.getBallPos_Y() >= mAlienRightEyeCircleY) //3rd quadrant
            {
                mRightEyeBallAngle = Math.atan((Math.abs(  (mAlienRightEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienRightEyeCircleX)) ));
                mAlienRightEyeBallCircleX = mAlienRightEyeCircleX - (mRightEyeballShiftDistance*Math.cos(mRightEyeBallAngle));
                mAlienRightEyeBallCircleY = mAlienRightEyeCircleY + (mRightEyeballShiftDistance*Math.sin(mRightEyeBallAngle));
            }
            else if((double)this.mCatapulterBall.getBallPos_X() >= mAlienRightEyeCircleX && this.mCatapulterBall.getBallPos_Y() >= mAlienRightEyeCircleY) //4th Quadrant
            {
                mRightEyeBallAngle = Math.atan((Math.abs(  (mAlienRightEyeCircleY - this.mCatapulterBall.getBallPos_Y()) / (this.mCatapulterBall.getBallPos_X()-mAlienRightEyeCircleX)) ));
                mAlienRightEyeBallCircleX = mAlienRightEyeCircleX + (mRightEyeballShiftDistance*Math.cos(mRightEyeBallAngle));
                mAlienRightEyeBallCircleY = mAlienRightEyeCircleY + (mRightEyeballShiftDistance*Math.sin(mRightEyeBallAngle));
            }
            else
            {
                mAlienRightEyeBallCircleX = mAlienRightEyeCircleX;
                mAlienRightEyeBallCircleY = mAlienRightEyeCircleY;
            }

            //Calculates HITSCORE
            if (mCountBallHit==true && this.mCatapulterBall.getBallPos_X() > mAlienRect.left && this.mCatapulterBall.getBallPos_X() < mAlienRect.right && this.mCatapulterBall.getBallPos_Y() > mAlienRect.top && this.mCatapulterBall.getBallPos_Y() < mAlienRect.bottom){
                mAlienBallHitCount+=1;
                mCountBallHit = false;
                //Starts Anim
                mCatapulterViewInterface.catapultAlienHitEvent(mAlienBallHitCount); //Sends Hit-info to listeners
                alienHitAnimStart(); //Triggers animation
                //Update FireBase
                mFbDbRef_HitCount.setValue(mAlienBallHitCount); //Updates firebase
                if (mAlienBallHitCountHighScore < mAlienBallHitCount){
                    mFbDbRef_HitCountHighScore.setValue(mAlienBallHitCount);
                    mAlienBallHitCountHighScore = mAlienBallHitCount;
                }


            }
            else if (mCountBallHit==false && this.mCatapulterBall.getBallPos_X() > mAlienRect.left && this.mCatapulterBall.getBallPos_X() < mAlienRect.right && this.mCatapulterBall.getBallPos_Y() > mAlienRect.top && this.mCatapulterBall.getBallPos_Y() < mAlienRect.bottom){
                //Does nothing, as the hit has been counted for.
            }
            else
            {
                mCountBallHit = true;
            }

        }

    }

    //Catapult Related Classes and Threads
    private class CatapultUnit{

        private CatapulterBall myCatapulterBall; //Ball being fired
        private Drawable mCatapultBaseDrawable;
        private Rect mCatapultBaseRect;
        private RectF mCatapultArcRectF;
        private Paint mCatapultAngleArcPaint;
        private float mCatapultBaseElevation;

        private Path mCatapultArmPath;
        private double mCatapultArmLengt;
        private double mCatapultArmCenter_X, mCatapultArmCenter_Y;
        private double mCatapultArmAngle;
        private double mCatapultArmAngleStart;
        private double mCatapultArmAngleStop;
        private Paint mCatapultArmPaint;
        private Matrix mCatapultArmMatrix;
        private RectF mCatapultArmBounds;

        private Path mCatapultLaunchVectorPath;
        private Matrix mCatapultLaunchVectorMatrix;
        private RectF mCatapultLaunchVectorBounds;
        private Rect mCatapultLaunchVectorTextSpeedX, mCatapultLaunchVectorTextSpeedY, mCatapultLaunchVectorTextAngleX, mCatapultLaunchVectorTextAngleY;

        public CatapultUnit() {
            init();
        }

        private void init(){

            mCatapultBaseDrawable = getResources().getDrawable(R.drawable.catapult_base,null);
            mCatapultBaseRect = new Rect();
            mCatapultArcRectF = new RectF();
            mCatapultArmPath = new Path();

            mCatapultArmPaint = new Paint();
            mCatapultArmPaint.setColor(Color.BLACK);
            mCatapultArmPaint.setStyle(Paint.Style.STROKE);
            mCatapultArmPaint.setStrokeWidth(8);
            mCatapultArmPaint.setAntiAlias(true);

            mCatapultAngleArcPaint = new Paint();
            mCatapultAngleArcPaint.setColor(Color.GRAY);
            mCatapultAngleArcPaint.setStyle(Paint.Style.STROKE);
            mCatapultAngleArcPaint.setStrokeWidth(5);
            mCatapultAngleArcPaint.setAntiAlias(true);


            mCatapultArmMatrix = new Matrix();
            mCatapultArmBounds = new RectF();
            mCatapultArmAngle = 0;

            mCatapultArmAngleStart = -45;
            mCatapultArmAngleStop = 45;
            mCatapultBaseElevation = mHeight*0.85f;

            mCatapultLaunchVectorPath = new Path();

        }

        public void setCatapulterBall(CatapulterBall mCatBall){
            myCatapulterBall = mCatBall;
        }

        public void setCatapultBaseElevation(float baseElevation){
            mCatapultBaseElevation = baseElevation;

            mCatapultBaseRect.left = (int)(mWidth*0.1);
            mCatapultBaseRect.right = mCatapultBaseRect.left + (int)(mWidth*0.15f);
            mCatapultBaseRect.bottom = (int)mCatapultBaseElevation;
            mCatapultBaseRect.top = mCatapultBaseRect.bottom - (int)(mWidth*0.15f);
            mCatapultBaseDrawable.setBounds(mCatapultBaseRect);

            mCatapultArcRectF.left = (float)getCatapultArmCenter_X() - mWidth*0.2f;
            mCatapultArcRectF.right = (float)getCatapultArmCenter_X() + mWidth*0.2f;
            mCatapultArcRectF.top = (float)getCatapultArmCenter_Y() - mWidth*0.2f;
            mCatapultArcRectF.bottom = (float)getCatapultArmCenter_Y() + mWidth*0.2f;

        }

        public Drawable getCatapultBaseDrawable() {

            mCatapultBaseDrawable.setBounds(mCatapultBaseRect);
            return mCatapultBaseDrawable;
        }

        public Rect getCatapultBaseRect() {
            return mCatapultBaseRect;
        }
        public RectF getCatapultArcRectF() {
            return mCatapultArcRectF;
        }

        public void setCatapultArmAngle(double mCatapultArmAngle) {
            this.mCatapultArmAngle = mCatapultArmAngle; //angle set by Thread // read, so that catapult movement is animated.
        }
        public double getCatapultArmCenter_X(){
            mCatapultArmCenter_X = mCatapultBaseRect.left+mCatapultBaseRect.width()*0.493f;
            mCatapultArmCenter_Y = mCatapultBaseRect.top + mCatapultBaseRect.height()*0.395f;
            return mCatapultArmCenter_X;
        }
        public double getCatapultArmCenter_Y(){
            mCatapultArmCenter_X = mCatapultBaseRect.left+mCatapultBaseRect.width()*0.493f;
            mCatapultArmCenter_Y = mCatapultBaseRect.top + mCatapultBaseRect.height()*0.395f;
            return mCatapultArmCenter_Y;
        }
        public double getCatapultBallCenter_X(){
            double X_val;
            X_val = getCatapultArmCenter_X() - (getCatapultArmLengt()-myCatapulterBall.getBallDiam()*0.5) * Math.cos(2*Math.PI*(getCatapultArmAngleStart()+17)/360);

            return X_val;
        }
        public double getCatapultBallCenter_Y(){
            double Y_val;
            Y_val = getCatapultArmCenter_Y() - (getCatapultArmLengt()-myCatapulterBall.getBallDiam()*0.5) * Math.sin(2*Math.PI*(getCatapultArmAngleStart()+17)/360);

            return Y_val;
        }

        public double getCatapultArmLengt() {
            mCatapultArmLengt = mCatapultBaseRect.width()*0.475;
            return mCatapultArmLengt;
        }

        public Path getCatapultArmPath() {

            mCatapultArmLengt = getCatapultArmLengt();
            mCatapultArmCenter_X = getCatapultArmCenter_X();
            mCatapultArmCenter_Y = getCatapultArmCenter_Y();

            mCatapultArmPath.reset();

            mCatapultArmPath.moveTo((float)mCatapultArmCenter_X, (float)mCatapultArmCenter_Y); //Center of hole
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X + (float)mCatapultArmLengt, (float)mCatapultArmCenter_Y);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt, (float)mCatapultArmCenter_Y);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt, (float)mCatapultArmCenter_Y - (float)myCatapulterBall.getBallDiam()*0.5f);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt, (float)mCatapultArmCenter_Y);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt + (float)myCatapulterBall.getBallDiam(), (float)mCatapultArmCenter_Y);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt + (float)myCatapulterBall.getBallDiam(), (float)mCatapultArmCenter_Y - (float)myCatapulterBall.getBallDiam()*0.5f);
            mCatapultArmPath.lineTo((float)mCatapultArmCenter_X - (float)mCatapultArmLengt + (float)myCatapulterBall.getBallDiam(), (float)mCatapultArmCenter_Y);
            //mCatapultArmPath.lineTo(mCatapultBaseRect.left+mCatapultBaseRect.width()*0.9f, mCatapultBaseRect.top + mCatapultBaseRect.height()*0.3f); //Center of hole
            mCatapultArmBounds = new RectF();
            mCatapultArmPath.computeBounds(mCatapultArmBounds, true);
            mCatapultArmMatrix = new Matrix();
            mCatapultArmMatrix.postRotate((float)mCatapultArmAngle,mCatapultBaseRect.left+mCatapultBaseRect.width()*0.493f, mCatapultBaseRect.top + mCatapultBaseRect.height()*0.395f );
            mCatapultArmPath.transform(mCatapultArmMatrix);


            return mCatapultArmPath;
        }

        public Paint getCatapultArmPaint() {
            return mCatapultArmPaint;
        }
        public Paint getCatapultArcPaint() {
            return mCatapultAngleArcPaint;
        }

        public void setCatapultArmAngleStart(double mCatapultArmAngleStart) {
            this.mCatapultArmAngleStart = mCatapultArmAngleStart;
            this.mCatapultArmAngle = mCatapultArmAngleStart;
        }

        public double getCatapultArmAngleStart() {
            return mCatapultArmAngleStart;
        }

        public void setCatapultArmAngleStop(double mCatapultArmAngleStop) {
            this.mCatapultArmAngleStop = mCatapultArmAngleStop;
        }

        public double getCatapultArmAngleStop() {
            return mCatapultArmAngleStop;
        }

        public Path getCatapultLaunchVectorPath(){

            mCatapultLaunchVectorPath.reset();
            mCatapultLaunchVectorPath.moveTo((float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width()) *Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)), (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width())*Math.sin((90-mCatapultArmAngleStop+0)*2*Math.PI/360)) );
            mCatapultLaunchVectorPath.lineTo((float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width()) *Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)) + (float)(2*myCatapulterBall.getBallInitialVelocity()), (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width()) *Math.sin((90-mCatapultArmAngleStop+0)*2*Math.PI/360)));
            mCatapultLaunchVectorPath.lineTo((float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width()) *Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)) + (float)(2*myCatapulterBall.getBallInitialVelocity())-10, (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width())*Math.sin((90-mCatapultArmAngleStop+0)*2*Math.PI/360)) + 10);
            mCatapultLaunchVectorPath.lineTo((float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width()) *Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)) + (float)(2*myCatapulterBall.getBallInitialVelocity()), (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width())*Math.sin((90-mCatapultArmAngleStop+0)*2*Math.PI/360)));
            mCatapultLaunchVectorPath.lineTo((float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width()) *Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)) + (float)(2*myCatapulterBall.getBallInitialVelocity())-10, (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width())*Math.sin((90-mCatapultArmAngleStop+0)*2*Math.PI/360)) - 10);

            mCatapultLaunchVectorBounds = new RectF();
            mCatapultLaunchVectorPath.computeBounds(mCatapultLaunchVectorBounds, true);
            mCatapultLaunchVectorMatrix = new Matrix();
            mCatapultLaunchVectorMatrix.postRotate((float)(mCatapultArmAngleStop-90),(float)mCatapultArmCenter_X + (float)((0.6*mCatapultBaseRect.width())*Math.cos((90-mCatapultArmAngleStop)*2*Math.PI/360)), (float)mCatapultArmCenter_Y - (float)((0.6*mCatapultBaseRect.width())*Math.sin((90-mCatapultArmAngleStop)*2*Math.PI/360)) );
            mCatapultLaunchVectorPath.transform(mCatapultLaunchVectorMatrix);

            return mCatapultLaunchVectorPath;
        }
    }

    public void launcCatapultExternally(){
        //Routine can be called from view holder, e.g. from ESP8266 units. See "MainActivity".
        mCatapulterBall1.setBallPos_X_Max(mWidth*0.95f);
        mCatapulterBall1.setBallPos_Y_Max(mGroundElevation);

        launchCatapult(catLaunchAngle,catLaunchSpeed);
        //mCatapulterViewInterface.catapultLaunchEvent("EXTERNALLY LAUNCHED CATAPULT",13);

    }

    private void launchCatapult(double finalAngle, double finalSpeed){

        mCatapultUnit.setCatapultArmAngleStop(finalAngle);
        mCatapulterBall1.setBallInitialVelocity((float)finalSpeed);
        mCatapulterBall1.setBallInitialVelocity_Angle_deg(90f-(float)finalAngle);
        mAlien.sResetHitScore(); //Set's hitscore to 0;

        CatapultLaunchRunnable catapultLaunchRunnable = new CatapultLaunchRunnable(mCatapultUnit, mCatapulterBall1);


        Handler launchHandler = new Handler(mCatapultLaunchThread.looper); //Attaches handler to looper
        launchHandler.post(catapultLaunchRunnable); //Handler passes runnable to thread message que (run's the runnable on the thread)
    }

    private static class CatapultLaunchThread extends Thread{

        public Looper looper;
        //public Handler handler;

        @Override
        public void run() {

            Looper.prepare();
            looper = looper.myLooper();
            looper.loop();

        }
    }

    private class CatapultLaunchRunnable implements Runnable{

        private CatapultUnit mCatUnit;
        private CatapulterBall mCatBall;
        private int dt_ms; //ms Delay
        private double mAngle;
        private double dAngle;
        private double deltaAngle;
        private double ballPosX, ballposY;

        public CatapultLaunchRunnable(CatapultUnit mCatapultUnit, CatapulterBall catBall) {
            mCatUnit = mCatapultUnit;
            mCatBall = catBall;
            init();
        }

        private void init(){

            dt_ms = 2; //10ms Delay
            mAngle = mCatapultUnit.getCatapultArmAngleStart();
            deltaAngle = Math.abs(mCatapultUnit.getCatapultArmAngleStart()-mCatapultUnit.getCatapultArmAngleStop());
            dAngle = deltaAngle/1000;

        }

        @Override
        public void run() {

            for (int i = 0; i < 1000; i++) {

                SystemClock.sleep(dt_ms);
                mAngle = mAngle + dAngle;
                dAngle = dAngle + 0.04*dAngle*dAngle;
                ballPosX = mCatUnit.getCatapultArmCenter_X() - (mCatUnit.getCatapultArmLengt()-mCatBall.getBallDiam()*0.5) * Math.cos(2*Math.PI*(mAngle+17)/360);
                ballposY = mCatUnit.getCatapultArmCenter_Y() - (mCatUnit.getCatapultArmLengt()-mCatBall.getBallDiam()*0.5) * Math.sin(2*Math.PI*(mAngle+17)/360);

                if (mAngle > mCatapultUnit.getCatapultArmAngleStop()){
                    i = 1000;

                    mUI_AnimHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            launchFlyingBall(mCatapulterBall1); //Launches Ball when catapult has reached it's end.
                            invalidate();
                        }
                    });
                }

                mUI_AnimHandler.post(new Runnable() { //Posting to main UI Thread)
                    @Override
                    public void run() {
                        mCatapultUnit.setCatapultArmAngle(mAngle);
                        mCatBall.setBallPos_X((float)ballPosX);
                        mCatBall.setBallPos_Y((float)ballposY);

                        invalidate();
                    }
                });

            }


        }
    }

    //ALIEN HIT THREAD AND ANIM

    private void alienHitAnimStart(){
        //Starts Animation --> Star Aroind Alien Eyes is drawn - indicating it was hit by the ball
        alienHitAnimRunnable run = new alienHitAnimRunnable(mAlien.mAlienRect.left+ mAlien.mAlienRect.width()/2, mAlien.mAlienRect.top + mAlien.mAlienRect.height()/3);
        Handler hnd = new Handler(mAlienHitAnimThread.looper); //Associates handler with thread
        hnd.post(run);
    }

    private static class alienHitAnimThread extends Thread{
        //Thread started in activity init metchod. From there on, the thread looper is available for handlers to connect.
        public Looper looper;
        public alienHitAnimThread() {

        }
        @Override
        public void run() {
            Looper.prepare(); //or looper.prepare
            looper = Looper.myLooper();
            looper.loop();

        }
    }

    private class alienHitAnimRunnable implements Runnable{
        private int dt_ms;
        private float leftStartX, leftStartY, rightStartX, rightStartY;

        private float starSize; //Corresponds to sidelength of stars.
        private float starHeight; //Corresponds to starsize*0.5*sin(PI/3);
        private Path leftStar1Path, rightStar1Path;
        private RectF leftStar1Rect, rightStar1Rect;
        private Matrix leftStar1Matrix, rightStar1Matrix;
        private int j;

        alienHitAnimRunnable(double leftX, double leftY){
            dt_ms = 4;
            starSize = 110;
            starHeight = (float)(starSize*Math.sin(Math.PI/3));
            leftStartX = (float)leftX;
            leftStartY = (float)leftY;
            leftStar1Path = new Path();
        }

        @Override
        public void run() {

            j=0;
            leftStar1Path.reset();
            leftStar1Path.moveTo(leftStartX+starSize*0.5f, leftStartY-starHeight);

            for (int i=0; i<=64; i++){

                SystemClock.sleep(dt_ms);
                if (j<5) { //5 steps to complete left edge of star side
                    leftStar1Path.rLineTo(-starSize * 0.5f * 0.2f, -starHeight * 0.2f);
                }
                else if (j<10){ //steps to complete right edge of star side
                    leftStar1Path.rLineTo(-starSize*0.5f * 0.2f, +starHeight * 0.2f);
                }
                else {
                    //Rotate Path 60deg around center and continue drawing until 6 triangles have been drawn completing the star.
                    leftStar1Rect = new RectF();
                    leftStar1Path.computeBounds(leftStar1Rect, true);
                    leftStar1Matrix = new Matrix();
                    leftStar1Matrix.postRotate(60,leftStartX, leftStartY);
                    leftStar1Path.transform(leftStar1Matrix);

                    leftStar1Path.moveTo(leftStartX+starSize*0.5f, leftStartY-starHeight);
                    j=-1; //Resets counter
                }

                j++; //Increments counter

                //UPDATE MAIN UI
                mUI_AnimHandler.post(new Runnable() { //Posting to main UI Thread)
                    @Override
                    public void run() {
                        //Publish Path to ui
                        mAlienHitPath = leftStar1Path; //Updates UI
                        invalidate();
                    }
                });

            }

            //UPDATE MAIN UI WHEN FINISHED
            SystemClock.sleep(150); //Maintains the Star for a while
            mAlienHitPath.reset();
            mUI_AnimHandler.post(new Runnable() { //Posting to main UI Thread)
                @Override
                public void run() {
                    //Publish Path to ui
                    mAlienHitPath = leftStar1Path; //Updates UI
                    invalidate();
                }
            });

            }

        }
    }



