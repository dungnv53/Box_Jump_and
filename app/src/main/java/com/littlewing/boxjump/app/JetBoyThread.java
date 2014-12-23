package com.littlewing.boxjump.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.JetPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * Created by dungnv on 11/6/14.
 */
// JET info: the JetBoyThread receives all the events from the JET player
// JET info: through the OnJetEventListener interface.
class JetBoyThread extends Thread implements JetPlayer.OnJetEventListener {

    // View attr
    // the number of asteroids that must be destroyed
    public static final int mSuccessThreshold = 50;

    // hit animation
    private Bitmap[] mExplosions = new Bitmap[4];

    protected static BoxJumpGame boxjump = new BoxJumpGame();

    /** The drawable to use as the far background of the animation canvas */
    private Bitmap mBackgroundImageFar;

    /** The drawable to use as the close background of the animation canvas */
    private Bitmap mBackgroundImageNear;

    /** The 2nd drawable to use as the close background of the animation canvas */
    private Bitmap mBackgroundImageTwo;

    // our intrepid space boy
    private Bitmap[] mShipFlying = new Bitmap[4];

    // the twinkly bit
    private Bitmap[] mBeam = new Bitmap[4];

    // the things you are trying to hit
    private Bitmap[] mAsteroids = new Bitmap[12];

    private Bitmap mTimerShell;

    private Bitmap mLaserShot;

    /** Message handler used by thread to interact with TextView */
    private Handler mHandler;

    // JET info: the star of our show, a reference to the JetPlayer object.
    private JetPlayer mJet = null;

    private Craft angel; // Angel instance

    Resources mRes;

    /** Handle to the surface manager object we interact with */
    private SurfaceHolder mSurfaceHolder;

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;

    private boolean muteMask[][] = new boolean[9][32];

    private double angle = 0;  // angle of bernoulli curve

    // how many frames per beat? The basic animation can be changed for
    // instance to 3/4 by changing this to 3.
    // untested is the impact on other parts of game logic for non 4/4 time.
    private static final int ANIMATION_FRAMES_PER_BEAT = 4;

    // JET info: event IDs within the JET file.
    // JET info: in this game 80 is used for sending asteroid across the screen
    // JET info: 82 is used as game time for 1/4 note beat.
    private final byte NEW_ASTEROID_EVENT = 80;

    public JetBoyThread(SurfaceHolder surfaceHolder, Context context, Handler handler) {

        mSurfaceHolder = surfaceHolder;
        mHandler = handler;
        mContext = context;
        mRes = context.getResources();

        SoundTrack myTrack = new SoundTrack();         // Call soundtrack class to init mute mask

        // always set state to start, ensure we come in from front door if
        // app gets tucked into background
        boxjump.mState = boxjump.STATE_START;

        setInitialGameState();

        boxjump.mTitleBG = BitmapFactory.decodeResource(mRes, R.drawable.bg1); //title_hori

        mBackgroundImageFar = BitmapFactory.decodeResource(mRes, R.drawable.background0_00); // bg_a

        mLaserShot = BitmapFactory.decodeResource(mRes, R.drawable.laser);

        mBackgroundImageNear = BitmapFactory.decodeResource(mRes, R.drawable.background2_09); // bg_b
        mBackgroundImageTwo = BitmapFactory.decodeResource(mRes, R.drawable.background2_07); // bg_b

        mShipFlying = boxjump.loadShip(mShipFlying, mContext);
        mBeam = boxjump.loadBeam(mBeam, mContext);

        mTimerShell = BitmapFactory.decodeResource(mRes, R.drawable.cmyk);

        // I wanted them to rotate in a certain way
        // so I loaded them backwards from the way created.
        mAsteroids = boxjump.loadAsteroid(mAsteroids, mContext);

        mExplosions = boxjump.loadExplosion(mExplosions, mContext);

        angel = new Craft(480, 640, mAsteroids);
    }

    /**
     * Does the grunt work of setting up initial jet requirements
     */
    private void initializeJetPlayer() {

        // JET info: let's create our JetPlayer instance using the factory.
        // JET info: if we already had one, the same singleton is returned.
        mJet = JetPlayer.getJetPlayer();

        boxjump.setJetPlaying(false);

        // JET info: make sure we flush the queue,
        // JET info: otherwise left over events from previous gameplay can hang around.
        // JET info: ok, here we don't really need that but if you ever reuse a JetPlayer
        // JET info: instance, clear the queue before reusing it, this will also clear any
        // JET info: trigger clips that have been triggered but not played yet.
        mJet.clearQueue();

        // JET info: we are going to receive in this example all the JET callbacks
        // JET info: inthis animation thread object.
        mJet.setEventListener(this);

        Log.d(boxjump.TAG, "opening jet file");

        // JET info: load the actual JET content the game will be playing,
        // JET info: it's stored as a raw resource in our APK, and is labeled "level1"
        mJet.loadJetFile(mContext.getResources().openRawResourceFd(R.raw.level1));
        // JET info: if our JET file was stored on the sdcard for instance, we would have used
        // JET info: mJet.loadJetFile("/sdcard/level1.jet");

        Log.d(boxjump.TAG, "opening jet file DONE");

        boxjump.mCurrentBed = 0;
        byte sSegmentID = 0;

        Log.d(boxjump.TAG, " start queuing jet file");

        // JET info: now we're all set to prepare queuing the JET audio segments for the game.
        // JET info: in this example, the game uses segment 0 for the duration of the game play,
        // JET info: and plays segment 1 several times as the "outro" music, so we're going to
        // JET info: queue everything upfront, but with more complex JET compositions, we could
        // JET info: also queue the segments during the game play.

        // JET info: this is the main game play music
        // JET info: it is located at segment 0
        // JET info: it uses the first DLS lib in the .jet resourfindce, which is at index 0
        // JET info: index -1 means no DLS
        mJet.queueJetSegment(0, 0, 0, 0, 0, sSegmentID);

        // JET info: end game music, loop 4 times normal pitch
        mJet.queueJetSegment(1, 0, 4, 0, 0, sSegmentID);

        // JET info: end game music loop 4 times up an octave
        mJet.queueJetSegment(1, 0, 4, 1, 0, sSegmentID);

        // JET info: set the mute mask as designed for the beginning of the game, when the
        // JET info: the player hasn't scored yet.
        mJet.setMuteArray(muteMask[0], true);

        Log.d(boxjump.TAG, " start queuing jet file DONE");

    }


    private void doDraw(Canvas canvas) {

        if (boxjump.mState == boxjump.STATE_RUNNING) {
            doDrawRunning(canvas);
        } else if (boxjump.mState == boxjump.STATE_START) {
            doDrawReady(canvas);
        } else if (boxjump.mState == boxjump.STATE_PLAY || boxjump.mState == boxjump.STATE_LOSE) {
            if (boxjump.mTitleBG2 == null) {
                boxjump.mTitleBG2 = BitmapFactory.decodeResource(mRes, R.drawable.intro_00_720); //title_bg_hori
            }
            doDrawPlay(canvas);
        }// end state play block
    }


    /**
     * Draws current state of the game Canvas.
     */
    private void doDrawRunning(Canvas canvas) {
        // if we have scrolled all the way, reset to start
        canvas.drawBitmap(mBackgroundImageFar, 0, 0, null);

        // same story different image...
        // TODO possible method call

        canvas.drawBitmap(mBackgroundImageNear, 0, 0, null);

        // same story different image...

        canvas.drawBitmap(mBackgroundImageTwo, 0, 720, null);  // 720 la hard fix

        canvas.drawBitmap(mBeam[boxjump.getShipIndex()], boxjump.getCanvasWidth()/2 - 51, boxjump.getCanvasHeight()/2 + 320, null);  // y old code use m-num ?

        boxjump.setShipIndex(boxjump.getShipIndex() +1);

        if (boxjump.getShipIndex() == 4)
            boxjump.setShipIndex(0);

        canvas.drawBitmap(mShipFlying[boxjump.getShipIndex()], boxjump.mJetBoyX, boxjump.getCanvasHeight() - 181, null);

        if (boxjump.mLaserOn) {
            canvas.drawBitmap(mLaserShot, boxjump.mJetBoyX, boxjump.mJetBoyY + 32, null);
            Log.d(boxjump.TAG, " drawing shot " + boxjump.mJetBoyX + " at " + boxjump.mJetBoyY);
        }

        // tick tock
        canvas.drawBitmap(mTimerShell, 0, 0, null); // mCanvasWidth - mTimerShell.getWidth()

    }

    private void setInitialGameState() {
        boxjump.mTimerLimit = boxjump.TIMER_LIMIT;

        boxjump.mJetBoyY = boxjump.mJetBoyYMin;

        // set up jet stuff
        initializeJetPlayer();

        boxjump.setTimer(new Timer());

        boxjump.mDangerWillRobinson = new Vector<Asteroid>();

        boxjump.mExplosion = new Vector<Explosion>();

        boxjump.mInitialized = true;

        boxjump.mHitStreak = 0;
        boxjump.mHitTotal = 0;
    }

    private void doAsteroidAnimation(Canvas canvas) {
        if ((boxjump.getDangerWillRobinson() == null | boxjump.getDangerWillRobinson().size() == 0)
                && (boxjump.mExplosion != null && boxjump.mExplosion.size() == 0))
            return;

        // Compute what percentage through a beat we are and adjust
        // animation and position based on that. This assumes 140bpm(428ms/beat).
        // This is just inter-beat interpolation, no game state is updated
        long frameDelta = System.currentTimeMillis() - boxjump.getLastBeatTime();

        int animOffset = (int)(ANIMATION_FRAMES_PER_BEAT * frameDelta / 428);

        for (int i = (boxjump.getDangerWillRobinson().size() - 1); i >= 0; i--) {
            Vector dangerRobinson = boxjump.getDangerWillRobinson();
            Asteroid asteroid = (Asteroid)dangerRobinson.elementAt(i);

            if (!asteroid.mMissed)
                boxjump.mJetBoyY = asteroid.mDrawY;

            // Log.d(TAG, " drawing asteroid " + ii + " at " +
            // asteroid.mDrawX );

            canvas.drawBitmap(
                    mAsteroids[(asteroid.mAniIndex + animOffset) % mAsteroids.length],
                    asteroid.mDrawX, asteroid.mDrawY, null);
        }

        for (int i = (boxjump.mExplosion.size() - 1); i >= 0; i--) {
            Explosion ex = boxjump.mExplosion.elementAt(i);

            canvas.drawBitmap(mExplosions[(ex.mAniIndex + animOffset) % mExplosions.length],
                    ex.mDrawX, ex.mDrawY, null);
        }

        this.angle += 0.1;
        this.angel.bernoulliMove(angle);

        canvas.drawBitmap(angel.getBossImage(), angel.getDonaldX(), angel.getDonaldY(), null);

    }

    private void doDrawReady(Canvas canvas) {
        canvas.drawBitmap(boxjump.mTitleBG, 0, 0, null);
    }

    private void doDrawPlay(Canvas canvas) {
        canvas.drawBitmap(boxjump.mTitleBG2, 0, 0, null);
    }


    /**
     * the heart of the worker bee
     */
    public void run() {
        // while running do stuff in this loop...bzzz!
        while (boxjump.getRun()) {
            Canvas c = null;

            if (boxjump.mState == boxjump.STATE_RUNNING) {
                // Process any input and apply it to the game state
                updateGameState();

                if (!boxjump.getJetPlaying()) {

                    boxjump.mInitialized = false;
                    Log.d(boxjump.TAG, "------> STARTING JET PLAY");

                    Log.d(boxjump.TAG, "----> " + boxjump.mJetBoyX + "<---------" + boxjump.mJetBoyY);
                    mJet.play();

                    boxjump.setJetPlaying(true);

                }

                boxjump.setPassedTime(System.currentTimeMillis());

                // kick off the timer task for counter update if not already
                // initialized
                if (boxjump.getTimerTask() == null) {
                    boxjump.setTimerTask(new TimerTask() {
                        public void run() {
                            doCountDown();
                        }
                    });

                    boxjump.getTimer().schedule(boxjump.getTimerTask(), boxjump.mTaskIntervalInMillis);

                }// end of TimerTask init block

            }// end of STATE_RUNNING block
            else if (boxjump.mState == boxjump.STATE_PLAY && !boxjump.mInitialized)
            {
                setInitialGameState();
            } else if (boxjump.mState == boxjump.STATE_LOSE) {
                boxjump.mInitialized = false;
            }

            try {
                c = mSurfaceHolder.lockCanvas(null);
                // synchronized (mSurfaceHolder) {
                doDraw(c);
                // }
            } finally {
                // do this in a finally so that if an exception is thrown
                // during the above, we don't leave the Surface in an
                // inconsistent state
                if (c != null) {
                    mSurfaceHolder.unlockCanvasAndPost(c);
                }
            }// end finally block
        }// end while mrun block
    }


    /**
     * This method handles updating the model of the game state. No
     * rendering is done here only processing of inputs and update of state.
     * This includes positons of all game objects (asteroids, player,
     * explosions), their state (animation frame, hit), creation of new
     * objects, etc.
     */
    protected void updateGameState() {
        // Process any game events and apply them
        while (true) {
            GameEvent event = boxjump.mEventQueue.poll();
            if (event == null)
                break;

            // Log.d(TAG,"*** EVENT = " + event);

            // Process keys tracking the input context to pass in to later
            // calls
            if (event instanceof KeyGameEvent) {
                // Process the key for affects other then asteroid hits
                boxjump.mKeyContext = processKeyEvent((KeyGameEvent)event, boxjump.mKeyContext);

                // Update laser state. Having this here allows the laser to
                // be triggered right when the key is
                // pressed. If we comment this out the laser will only be
                // turned on when updateLaser is called
                // when processing a timer event below.
                updateLaser(boxjump.mKeyContext);

            }
            // JET events trigger a state update
            else if (event instanceof JetGameEvent) {
                JetGameEvent jetEvent = (JetGameEvent)event;

                // Only update state on a timer event
                if (jetEvent.value == boxjump.getTIMER_EVENT()) {
                    // Note the time of the last beat
                    boxjump.setLastBeatTime(System.currentTimeMillis());

                    // Update laser state, turning it on if a key has been
                    // pressed or off if it has been
                    // on for too long.
                    updateLaser(boxjump.mKeyContext);

                    // Update explosions before we update asteroids because
                    // updateAsteroids may add
                    // new explosions that we do not want updated until next
                    // frame
                    updateExplosions(boxjump.mKeyContext);

                    // Update asteroid positions, hit status and animations
                    updateAsteroids(boxjump.mKeyContext);
                }

                processJetEvent(jetEvent.player, jetEvent.segment, jetEvent.track,
                        jetEvent.channel, jetEvent.controller, jetEvent.value);
            }
        }
    }


    /**
     * This method handles the state updates that can be caused by key press
     * events. Key events may mean different things depending on what has
     * come before, to support this concept this method takes an opaque
     * context object as a parameter and returns an updated version. This
     * context should be set to null for the first event then should be set
     * to the last value returned for subsequent events.
     */
    protected Object processKeyEvent(KeyGameEvent event, Object context) {
        // Log.d(TAG, "key code is " + event.keyCode + " " + (event.up ?
        // "up":"down"));

        // If it is a key up on the fire key make sure we mute the
        // associated sound
        if (event.up) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                return null;
            }
        }
        // If it is a key down on the fire key start playing the sound and
        // update the context
        // to indicate that a key has been pressed and to ignore further
        // presses
        else {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && (context == null)) {
                return event;
            }
        }

        // Return the context unchanged
        return context;
    }


    /**
     * This method updates the laser status based on user input and shot
     * duration
     */
    protected void updateLaser(Object inputContext) {
        // Lookup the time of the fire event if there is one
        long keyTime = inputContext == null ? 0 : ((GameEvent)inputContext).eventTime;

        // Log.d(TAG,"keyTime delta = " +
        // (System.currentTimeMillis()-keyTime) + ": obj = " +
        // inputContext);

        // If the laser has been on too long shut it down
        if (boxjump.mLaserOn && System.currentTimeMillis() - boxjump.mLaserFireTime > 400) {
//                mLaserOn = false;  // tat laser
        }

        // trying to tune the laser hit timing
        else if (System.currentTimeMillis() - boxjump.mLaserFireTime > 300) {
            // JET info: the laser sound is on track 23, we mute it (true) right away (false)
            mJet.setMuteFlag(23, true, false);

        }

        // Now check to see if we should turn the laser on. We do this after
        // the above shutdown
        // logic so it can be turned back on in the same frame it was turned
        // off in. If we want
        // to add a cooldown period this may change.
        if (!boxjump.mLaserOn && System.currentTimeMillis() - keyTime <= 400) {

            boxjump.mLaserOn = true;
            boxjump.mLaserFireTime = keyTime;

            // JET info: unmute the laser track (false) right away (false)
            mJet.setMuteFlag(23, false, false);
        }
    }

    /**
     * Update asteroid state including position and laser hit status.
     */
    protected void updateAsteroids(Object inputContext) {
        if (boxjump.mDangerWillRobinson == null | boxjump.mDangerWillRobinson.size() == 0)
            return;

        for (int i = (boxjump.mDangerWillRobinson.size() - 1); i >= 0; i--) {
            Asteroid asteroid = boxjump.mDangerWillRobinson.elementAt(i);

            // If the asteroid is within laser range but not already missed
            // check if the key was pressed close enough to the beat to make a hit
            if (asteroid.mDrawX <= boxjump.mAsteroidMoveLimitX + 20 && !asteroid.mMissed)
            {
                // If the laser was fired on the beat destroy the asteroid
                if (boxjump.mLaserOn) {
                    // Track hit streak for adjusting music
                    boxjump.mHitStreak++;
                    boxjump.mHitTotal++;

                    // replace the asteroid with an explosion
                    Explosion ex = new Explosion();
                    ex.mAniIndex = 0;
                    ex.mDrawX = asteroid.mDrawX;
                    ex.mDrawY = asteroid.mDrawY;
                    boxjump.mExplosion.add(ex);

                    mJet.setMuteFlag(24, false, false);

                    boxjump.getDangerWillRobinson().removeElementAt(i);

                    // This asteroid has been removed process the next one
                    continue;
                } else {
                    // Sorry, timing was not good enough, mark the asteroid
                    // as missed so on next frame it cannot be hit even if it is still
                    // within range
                    asteroid.mMissed = true;

                    boxjump.mHitStreak = boxjump.mHitStreak - 1;

                    if (boxjump.mHitStreak < 0)
                        boxjump.mHitStreak = 0;

                }
            }

            // Update the asteroids position, even missed ones keep moving
            asteroid.mDrawX -= boxjump.getPixelMoveX();
            asteroid.mDrawY += boxjump.getPixelMoveX(); // dungnv add. asteroid move down

            // Update asteroid animation frame
            asteroid.mAniIndex = (asteroid.mAniIndex + ANIMATION_FRAMES_PER_BEAT)
                    % mAsteroids.length;

            // if we have scrolled off the screen
            if (asteroid.mDrawX < 0) {
                boxjump.getDangerWillRobinson().removeElementAt(i);
            }
        }
    }

    /**
     * This method updates explosion animation and removes them once they
     * have completed.
     */
    protected void updateExplosions(Object inputContext) {
        if (boxjump.mExplosion == null | boxjump.mExplosion.size() == 0)
            return;

        for (int i = boxjump.mExplosion.size() - 1; i >= 0; i--) {
            Explosion ex = boxjump.mExplosion.elementAt(i);

            ex.mAniIndex += ANIMATION_FRAMES_PER_BEAT;

            // When the animation completes remove the explosion
            if (ex.mAniIndex > 3) {
                mJet.setMuteFlag(24, true, false);
                mJet.setMuteFlag(23, true, false);

                boxjump.mExplosion.removeElementAt(i);
            }
        }
    }

    /**
     * This method handles the state updates that can be caused by JET
     * events.
     */
    protected void processJetEvent(JetPlayer player, short segment, byte track, byte channel,
                                   byte controller, byte value) {

        //Log.d(TAG, "onJetEvent(): seg=" + segment + " track=" + track + " chan=" + channel
        //        + " cntrlr=" + controller + " val=" + value);

        // Check for an event that triggers a new asteroid
        if (value == NEW_ASTEROID_EVENT) {
            doAsteroidCreation();
        }

        boxjump.setBeatCount(boxjump.getBeatCount() +1);

        if (boxjump.getBeatCount() > 4) {
            boxjump.setBeatCount(1);

        }

        // Scale the music based on progress

        // it was a game requirement to change the mute array on 1st beat of
        // the next measure when needed
        // and so we track beat count, after that we track hitStreak to
        // determine the music "intensity"
        // if the intensity has go gone up, call a corresponding trigger clip, otherwise just
        // execute the rest of the music bed change logic.
        if (boxjump.getBeatCount() == 1) {

            // do it back wards so you fall into the correct one
            if (boxjump.mHitStreak > 28) {

                // did the bed change?
                if (boxjump.mCurrentBed != 7) {
                    // did it go up?
                    if (boxjump.mCurrentBed < 7) {
                        mJet.triggerClip(7);
                    }

                    boxjump.mCurrentBed = 7;
                    // JET info: change the mute mask to update the way the music plays based
                    // JET info: on the player's skills.
                    mJet.setMuteArray(muteMask[7], false);

                }
            } else if (boxjump.mHitStreak > 24) {
                if (boxjump.mCurrentBed != 6) {
                    if (boxjump.mCurrentBed < 6) {
                        // JET info: quite a few asteroids hit, trigger the clip with the guy's
                        // JET info: voice that encourages the player.
                        mJet.triggerClip(6);
                    }

                    boxjump.mCurrentBed = 6;
                    mJet.setMuteArray(muteMask[6], false);
                }
            } else if (boxjump.mHitStreak > 20) {
                if (boxjump.mCurrentBed != 5) {
                    if (boxjump.mCurrentBed < 5) {
                        mJet.triggerClip(5);
                    }

                    boxjump.mCurrentBed = 5;
                    mJet.setMuteArray(muteMask[5], false);
                }
            } else if (boxjump.mHitStreak > 16) {
                if (boxjump.mCurrentBed != 4) {

                    if (boxjump.mCurrentBed < 4) {
                        mJet.triggerClip(4);
                    }
                    boxjump.mCurrentBed = 4;
                    mJet.setMuteArray(muteMask[4], false);
                }
            } else if (boxjump.mHitStreak > 12) {
                if (boxjump.mCurrentBed != 3) {
                    if (boxjump.mCurrentBed < 3) {
                        mJet.triggerClip(3);
                    }
                    boxjump.mCurrentBed = 3;
                    mJet.setMuteArray(muteMask[3], false);
                }
            } else if (boxjump.mHitStreak > 8) {
                if (boxjump.mCurrentBed != 2) {
                    if (boxjump.mCurrentBed < 2) {
                        mJet.triggerClip(2);
                    }

                    boxjump.mCurrentBed = 2;
                    mJet.setMuteArray(muteMask[2], false);
                }
            } else if (boxjump.mHitStreak > 4) {
                if (boxjump.mCurrentBed != 1) {

                    if (boxjump.mCurrentBed < 1) {
                        mJet.triggerClip(1);
                    }

                    mJet.setMuteArray(muteMask[1], false);

                    boxjump.mCurrentBed = 1;
                }
            }
        }
    }

    private void doAsteroidCreation() {
        // Log.d(TAG, "asteroid created");

        Asteroid _as = new Asteroid();

        int drawIndex = boxjump.getRandom().nextInt(4);

        // TODO Remove hard coded value
        _as.mDrawY = boxjump.mAsteroidMinY + (drawIndex * 63);

        _as.mDrawX = (boxjump.getCanvasWidth() - mAsteroids[0].getWidth());

        _as.mStartTime = System.currentTimeMillis();

        boxjump.mDangerWillRobinson.add(_as);
    }

    /**
     * Used to signal the thread whether it should be running or not.
     * Passing true allows the thread to run; passing false will shut it
     * down if it's already running. Calling start() after this was most
     * recently called with false will result in an immediate shutdown.
     *
     * @param b true to run, false to shut down
     */
    public void setRunning(boolean b) {
        boxjump.setRun(b);

        if (boxjump.getRun() == false) {
            if (boxjump.getTimerTask() != null)
                boxjump.getTimerTask().cancel();
        }
    }

    /**
     * returns the current int value of game state as defined by state
     * tracking constants
     *
     * @return
     */
    public int getGameState() {
        synchronized (mSurfaceHolder) {
            return boxjump.mState;
        }
    }

    /**
     * Sets the game mode. That is, whether we are running, paused, in the
     * failure state, in the victory state, etc.
     *
     * @see # setState (int, CharSequence)
     * @param mode one of the STATE_* constants
     */
    public void setGameState(int mode) {
        synchronized (mSurfaceHolder) {
            setGameState(mode, null);
        }
    }

    /**
     * Sets state based on input, optionally also passing in a text message.
     *
     * @param state
     * @param message
     */
    public void setGameState(int state, CharSequence message) {

        synchronized (mSurfaceHolder) {

            // change state if needed
            if (boxjump.mState != state) {
                boxjump.mState = state;
            }

            if (boxjump.mState == boxjump.STATE_PLAY) {
                Resources res = mContext.getResources();
                mBackgroundImageFar = BitmapFactory
                        .decodeResource(res, R.drawable.background0_00); //background_a

                // don't forget to resize the background image
                mBackgroundImageFar = Bitmap.createScaledBitmap(mBackgroundImageFar,
                        boxjump.getCanvasWidth() * 2, boxjump.getCanvasHeight(), true);

                mBackgroundImageNear = BitmapFactory.decodeResource(res,
                        R.drawable.background_near); //background_b

                // don't forget to resize the background image
                mBackgroundImageNear = Bitmap.createScaledBitmap(mBackgroundImageNear,
                        boxjump.getCanvasWidth() * 2, boxjump.getCanvasHeight(), true);

            } else if (boxjump.mState == boxjump.STATE_RUNNING) {
                // When we enter the running state we should clear any old
                // events in the queue
                boxjump.mEventQueue.clear();

                // And reset the key state so we don't think a button is pressed when it isn't
                boxjump.mKeyContext = null;
            }

        }
    }

    /**
     * Add key press input to the GameEvent queue
     */
    public boolean doKeyDown(int keyCode, KeyEvent msg) {
        boxjump.mEventQueue.add(new KeyGameEvent(keyCode, false, msg));

        return true;
    }

    /**
     * Add key press input to the GameEvent queue
     */
    public boolean doKeyUp(int keyCode, KeyEvent msg) {
        boxjump.mEventQueue.add(new KeyGameEvent(keyCode, true, msg));

        return true;
    }

    public boolean onTouch(MotionEvent event) {
        int x = (int)event.getX();
        int y = (int)event.getY();
        Log.d("x = ", x+" \n -*-");
        Log.d("y = ", y+ "\n -*-");

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (inRange(x, 220, 120) && inRange(y, 860, 260)) { // tap on left side TODO hardcode
                    boxjump.MOVE_DIR = boxjump.DIR_LEFT; // move to left, set DIR
                    heroMove(12, boxjump.LEFT);
                } else if (inRange(x, 0, 120) && inRange(y, 860, 260)) { // tap on right side 170-50 for center
                    boxjump.MOVE_DIR = boxjump.DIR_RIGHT; // move to right, set DIR
                    heroMove(12, boxjump.RIGHT);
                }
                // Move up n down
                if(inRange(x, 120, 100) && inRange(y, 840, 100)) {
                    heroMove(12, boxjump.UP);
                }
                if(inRange(x, 120, 100) && inRange(y, 1020, 100)) {
                    heroMove(12, boxjump.DOWN);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
        }
        return true;
    }

    // Tap on movePad to move hero left, right, up n down
    public void tapMove(int tapX, int tapY, int topX, int topY, int width, int height, int direction) {
        // TODO cho topx,y width height thanh class Rectangle va Point
        if(inRange(tapX, topX, width) && inRange(tapY, topY, height)) {
            heroMove(12, direction);
        }
    }

    // Check if number x in a range
    public boolean inRange(int xCheck, int x, int range) {
        if((xCheck < x+range) && (xCheck > x)) {
            return true;
        }
        return false;
    }

    public void heroMove(int delta, int direction) {
        switch(direction) {
            // TODO set boundary by screen W-H ratio
            case 0:                // move left
                if(inRange(boxjump.mJetBoyX, 150, 410)) {
                    boxjump.mJetBoyX += delta;
                } else {
                    boxjump.mJetBoyX=300;
                }
                heroMotion();
                break;
            case 1:               // move right
                if(inRange(boxjump.mJetBoyX, 150, 410)) {
                    boxjump.mJetBoyX -= delta;
                } else {
                    boxjump.mJetBoyX = 300;
                }
                heroMotion();
                break;
            case 2:               // move up
                if(inRange(boxjump.mJetBoyX, 360, 235)) {
                    boxjump.mJetBoyX -= delta;
                } else {
                    boxjump.mJetBoyX  = 470;
                }
                heroMotion();
                break;
            case 3:               // move down
                if(inRange(boxjump.mJetBoyY, 360, 235)) {
                    boxjump.mJetBoyY += delta;
                } else {
                    boxjump.mJetBoyY = 480;
                }
                heroMotion();
                break;

        }
    }

    // Use sprite image to animate hero move
    public void heroMotion() {
        if (boxjump.getShipIndex() < 2) {
            boxjump.setShipIndex(boxjump.getShipIndex()+1);
            if (boxjump.getShipIndex() == 2) boxjump.setShipIndex(0);
            else if (boxjump.getShipIndex() == 0) boxjump.setShipIndex(1);
        } else {
            boxjump.setShipIndex(1);
            boxjump.setShipIndex(boxjump.getShipIndex()+1);
            if (boxjump.getShipIndex() == 2) boxjump.setShipIndex(0);
            else if (boxjump.getShipIndex() == 0) boxjump.setShipIndex(1);
        }
    }

    /* Callback invoked when the surface dimensions change. */
    public void setSurfaceSize(int width, int height) {
        // synchronized to make sure these all change atomically
        synchronized (mSurfaceHolder) {
            boxjump.setCanvasWidth(width);
            boxjump.setCanvasHeight(height);

            // don't forget to resize the background image
            mBackgroundImageFar = Bitmap.createScaledBitmap(mBackgroundImageFar, width * 2,
                    height, true);

            // don't forget to resize the background image
            mBackgroundImageNear = Bitmap.createScaledBitmap(mBackgroundImageNear, width * 2,
                    height, true);
        }
    }

    public void pause() {
        synchronized (mSurfaceHolder) {
            if (boxjump.mState == boxjump.STATE_RUNNING)
                setGameState(boxjump.STATE_PAUSE);
            if (boxjump.getTimerTask() != null) {
                boxjump.getTimerTask().cancel();
            }

            if (mJet != null) {
                mJet.pause();
            }
        }
    }

    private void doCountDown() {
        //Log.d(TAG,"Time left is " + mTimerLimit);

        boxjump.mTimerLimit = boxjump.mTimerLimit - 1;
        try {
            //subtract one minute and see what the result is.
            int moreThanMinute = boxjump.mTimerLimit - 60;

            if (moreThanMinute >= 0) {

                if (moreThanMinute > 9) {
                    boxjump.setTimerValue("1:" + moreThanMinute);

                }
                //need an extra '0' for formatting
                else {
                    boxjump.setTimerValue("1:0" + moreThanMinute);
                }
            } else {
                if (boxjump.mTimerLimit > 9) {
                    boxjump.setTimerValue("0:" + boxjump.mTimerLimit);
                } else {
                    boxjump.setTimerValue("0:0" + boxjump.mTimerLimit);
                }
            }
        } catch (Exception e1) {
            Log.e(boxjump.TAG, "doCountDown threw " + e1.toString());
        }

        Message msg = mHandler.obtainMessage();

        Bundle b = new Bundle();
        b.putString("text", boxjump.getTimerValue());

        //time's up
        if (boxjump.mTimerLimit == 0) {
            b.putString("STATE_LOSE", "" + boxjump.STATE_LOSE);
            boxjump.setTimerTask(null);

            boxjump.mState = boxjump.STATE_LOSE;

        } else {
            boxjump.setTimerTask(new TimerTask() {
                public void run() {
                    doCountDown();
                }
            });

            boxjump.getTimer().schedule(boxjump.getTimerTask(), boxjump.mTaskIntervalInMillis);
        }

        //this is how we send data back up to the main JetBoyView thread.
        //if you look in constructor of JetBoyView you will see code for
        //Handling of messages. This is borrowed directly from lunar lander.
        //Thanks again!
        msg.setData(b);
        mHandler.sendMessage(msg);

    }


    // JET info: JET event listener interface implementation:
    /**
     * required OnJetEventListener method. Notifications for queue updates
     *
     * @param player
     * @param nbSegments
     */
    public void onJetNumQueuedSegmentUpdate(JetPlayer player, int nbSegments) {
        //Log.i(TAG, "onJetNumQueuedUpdate(): nbSegs =" + nbSegments);

    }

    // JET info: JET event listener interface implementation:
    /**
     * The method which receives notification from event listener.
     * This is where we queue up events 80 and 82.
     *
     * Most of this data passed is unneeded for JetBoy logic but shown
     * for code sample completeness.
     *
     * @param player
     * @param segment
     * @param track
     * @param channel
     * @param controller
     * @param value
     */
    public void onJetEvent(JetPlayer player, short segment, byte track, byte channel,
                           byte controller, byte value) {
        //events fire outside the animation thread. This can cause timing issues.
        //put in queue for processing by animation thread.
        boxjump.mEventQueue.add(new JetGameEvent(player, segment, track, channel, controller, value));
    }

    // JET info: JET event listener interface implementation:
    public void onJetPauseUpdate(JetPlayer player, int paused) {
        //Log.i(TAG, "onJetPauseUpdate(): paused =" + paused);

    }

    // JET info: JET event listener interface implementation:
    public void onJetUserIdUpdate(JetPlayer player, int userId, int repeatCount) {
        //Log.i(TAG, "onJetUserIdUpdate(): userId =" + userId + " repeatCount=" + repeatCount);

    }

    public static BoxJumpGame getBoxjump() {
        return boxjump;
    }

}//end thread class
