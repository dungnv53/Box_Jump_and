package com.littlewing.archangle.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.JetPlayer;
import android.os.Handler;
import android.view.SurfaceHolder;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by dungnv on 12/19/14.
 */
public class BoxJumpGame {
    /**
     * State-tracking constants.
     */
    public static final int STATE_START = -1;
    public static final int STATE_PLAY = 0;
    public static final int STATE_LOSE = 1;
    public static final int STATE_PAUSE = 2;
    public static final int STATE_RUNNING = 3;

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;

    public static final int DIR_LEFT = 1;
    public static final int DIR_RIGHT = -1;
    public static final int DIR_UP = 1;
    public static final int DIR_DOWN = -1;


    // used to calculate level for mutes and trigger clip
    public int mHitStreak = 0;

    // total number asteroids you need to hit.
    public int mHitTotal = 0;

    // which music bed is currently playing?
    public int mCurrentBed = 0;

    // a lazy graphic fudge for the initial title splash
    public Bitmap mTitleBG;

    public Bitmap mTitleBG2;

    public boolean mInitialized = false;

    /** Queue for GameEvents */
    protected ConcurrentLinkedQueue<GameEvent> mEventQueue = new ConcurrentLinkedQueue<GameEvent>();

    /** Context for processKey to maintain state accross frames * */
    protected Object mKeyContext = null;

    // the timer display in seconds
    public int mTimerLimit;

    // used for internal timing logic.
    public final int TIMER_LIMIT = 72;

    // string value for timer display
    private String mTimerValue = "1:12";

    // start, play, running, lose are the states we use
    public int mState;

    // has laser been fired and for how long?
    // user for fx logic on laser fire
    boolean mLaserOn = true;

    long mLaserFireTime = 0;

    private final byte TIMER_EVENT = 82;

    // used to track beat for synch of mute/unmute actions
    private int mBeatCount = 1;

    // used to save the beat event system time.
    private long mLastBeatTime;

    private long mPassedTime;

    // how much do we move the asteroids per beat?
    private int mPixelMoveX = 25;

    // the asteroid send events are generated from the Jet File.
    // but which land they start in is random.
    private Random mRandom = new Random();

    private boolean mJetPlaying = false;

    /** Indicate whether the surface has been created & is ready to draw */
    private boolean mRun = false;

    // updates the screen clock. Also used for tempo timing.
    private Timer mTimer = null;

    private TimerTask mTimerTask = null;

    // one second - used to update timer
    public int mTaskIntervalInMillis = 1000;

    private int mCanvasHeight = 1;

    private int mCanvasWidth = 1;

    // used to track the picture to draw for ship animation
    private int mShipIndex = 0;

    // stores all of the asteroid objects in order
    public Vector<Asteroid> mDangerWillRobinson;

    public Vector<Explosion> mExplosion;

    // right to left scroll tracker for near and far BG
    private int mBGFarMoveX = 0;
    private int mBGNearMoveX = 0;
    private int mBGTwoMoveX = 0;

    // screen width, height
    private int mWidth = 720; //(int)getWidth();
    private int mHeight = 1280; //(int)getHeight();

    // how far up (close to top) jet boy can fly
    public int mJetBoyYMin = mWidth/3*2; //40;
    public int mJetBoyX = (int)mWidth/2; //0;
    public int mJetBoyY = (int)mHeight/3*2; //0;

    // this is the pixel position of the laser beam guide.
    public int mAsteroidMoveLimitX = 110;

    // how far up asteroid can be painted
    public int mAsteroidMinY = 40;

    // array to store the mute masks that are applied during game play to respond to
    // the player's hit streaks

    public static final String TAG = "JetBoy";

    private double angle = 0;  // angle of bernoulli curve


    // Direction moving
    public int MOVE_DIR = 0; // moving dir

    // Speed of BG move
    private int FarBGSpeed = 0;
    private int NearBGSpeed = 0;

    public BoxJumpGame() {
        super();
    }

    public Bitmap[] loadShip(Bitmap[] mShipFlying, Context mContext) {
        Resources mRes = mContext.getResources();
        mShipFlying[0] = BitmapFactory.decodeResource(mRes, R.drawable.box_blue); // ship2_1
        mShipFlying[1] = BitmapFactory.decodeResource(mRes, R.drawable.box_blue_90); // ship2_1
        mShipFlying[2] = BitmapFactory.decodeResource(mRes, R.drawable.box_blue_180); // ship2_1
        mShipFlying[3] = BitmapFactory.decodeResource(mRes, R.drawable.box_blue_270); // ship2_1
        // rotate or use sprite ?
        // tam thoi giu 4 sprite

        return mShipFlying;
    }

    public Bitmap[] loadBeam(Bitmap[] mBeam, Context mContext) {
        Resources mRes = mContext.getResources();
        mBeam[0] = BitmapFactory.decodeResource(mRes, R.drawable.effect_10); //intbeam_1
        mBeam[1] = BitmapFactory.decodeResource(mRes, R.drawable.effect_11);
        mBeam[2] = BitmapFactory.decodeResource(mRes, R.drawable.effect_12);
        mBeam[3] = BitmapFactory.decodeResource(mRes, R.drawable.effect_12);

        return mBeam;
    }

    public Bitmap[] loadAsteroid(Bitmap[] mAsteroids, Context mContext) {
        Resources mRes = mContext.getResources();
        mAsteroids[11] = BitmapFactory.decodeResource(mRes, R.drawable.boss1_08); // ast01
        mAsteroids[10] = BitmapFactory.decodeResource(mRes, R.drawable.boss3_08);
        mAsteroids[9] = BitmapFactory.decodeResource(mRes, R.drawable.boss5_08);
        mAsteroids[8] = BitmapFactory.decodeResource(mRes, R.drawable.boss18); // ast04
        mAsteroids[7] = BitmapFactory.decodeResource(mRes, R.drawable.boss37);
        mAsteroids[6] = BitmapFactory.decodeResource(mRes, R.drawable.boss2_08);
        mAsteroids[5] = BitmapFactory.decodeResource(mRes, R.drawable.boss26);
        mAsteroids[4] = BitmapFactory.decodeResource(mRes, R.drawable.boss66); //ast08
        mAsteroids[3] = BitmapFactory.decodeResource(mRes, R.drawable.boss6_08);
        mAsteroids[2] = BitmapFactory.decodeResource(mRes, R.drawable.boss6_08);
        mAsteroids[1] = BitmapFactory.decodeResource(mRes, R.drawable.boss6_08);
        mAsteroids[0] = BitmapFactory.decodeResource(mRes, R.drawable.boss6_08);

        return mAsteroids;
    }

    public Bitmap[] loadExplosion(Bitmap[] mExplosions, Context mContext) {
        Resources mRes = mContext.getResources();

        mExplosions[0] = BitmapFactory.decodeResource(mRes, R.drawable.effect_07);
        mExplosions[1] = BitmapFactory.decodeResource(mRes, R.drawable.effect_08);
        mExplosions[2] = BitmapFactory.decodeResource(mRes, R.drawable.effect_09);
        mExplosions[3] = BitmapFactory.decodeResource(mRes, R.drawable.effect_09);

        return mExplosions;
    }

    public void setJetPlaying(boolean bool) {
        this.mJetPlaying = bool;
    }

    public int getCanvasWidth() {
        return this.mCanvasWidth;
    }

    public void setCanvasWidth(int canvasWidth) {
        this.mCanvasWidth = canvasWidth;
    }

    public int getCanvasHeight() {
        return this.mCanvasHeight;
    }

    public void setCanvasHeight(int canvasHeight) {
        this.mCanvasHeight = canvasHeight;
    }

    public void setLastBeatTime(long time) {
        this.mLastBeatTime = time;
    }

    public int getTIMER_LIMIT() {
        return this.TIMER_LIMIT;
    }

    public int getTIMER_EVENT() {
        return this.TIMER_LIMIT;
    }

    public Random getRandom() {
        return this.mRandom;
    }

    public void setRun(boolean bl) {
        this.mRun = bl;
    }

    public boolean getRun() {
        return this.mRun;
    }

    public TimerTask getTimerTask() {
        return this.mTimerTask;
    }

    public void setTimerTask(TimerTask tmt) {
        this.mTimerTask = tmt;
    }

    public void setShipIndex(int idx) {
        this.mShipIndex = idx;
    }

    public int getShipIndex() {
        return this.mShipIndex;
    }

    public void setTimerValue(String timer) {
        this.mTimerValue = timer;
    }

    public String getTimerValue() {
        return this.mTimerValue;
    }

    public Timer getTimer() {
        return this.mTimer;
    }

    public void setTimer(Timer timer) {
        this.mTimer = timer;
    }

    public Vector getDangerWillRobinson() {
        return this.mDangerWillRobinson;
    }

    public void setDangerWillRobinson(Vector robinson) {
        this.mDangerWillRobinson = robinson;
    }

    public int getPixelMoveX() {
        return mPixelMoveX;
    }

    public void setPixelMoveX(int mPixelMoveX) {
        this.mPixelMoveX = mPixelMoveX;
    }

    public long getLastBeatTime() {
        return mLastBeatTime;
    }

    public boolean ismJetPlaying() {
        return mJetPlaying;
    }

    public boolean getJetPlaying() {
        return this.mJetPlaying;
    }

    public long getPassedTime() {
        return mPassedTime;
    }

    public void setPassedTime(long mPassedTime) {
        this.mPassedTime = mPassedTime;
    }

    public int getBeatCount() {
        return mBeatCount;
    }

    public void setBeatCount(int mBeatCount) {
        this.mBeatCount = mBeatCount;
    }
}
