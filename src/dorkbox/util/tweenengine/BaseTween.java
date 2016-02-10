/*
 * Copyright 2012 Aurelien Ribon
 * Copyright 2015 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.util.tweenengine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BaseTween is the base class of Tween and Timeline. It defines the iteration engine used to play animations for any number of times,
 * and in any direction, at any speed.
 * <p/>
 * <p/>
 * It is responsible for calling the different callbacks at the right moments, and for making sure that every callbacks are triggered,
 * even if the update engine gets a big delta time at once.
 * <p/>
 * <p/>
 * WARNING: <p/>
 * Individual tweens and timelines are NOT THREAD SAFE. Do not access any part of them outside of the render (or animation) thread.
 * <p/>
 * For object visibility in different threads, use {@link Tween#flushWrite()} inside an endCallback (so the objects are flushed), and
 * then before you access those objects, call {@link Tween#flushRead()} which will then correctly make those objects (which were
 * changed by the tween engine), visible to your thread.
 * <p/>
 * A "heavyweight" synchronization technique is not required in a modern JVM, flushWrite/Read is sufficient
 *
 * @see Tween
 * @see Timeline
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 * @author dorkbox, llc
 */
@SuppressWarnings("unused")
public
abstract class BaseTween<T> {
    // if there is a DELAY, the tween will remain inside "START" until it's finished with the delay
    protected static final int START = 1;
    protected static final int RUN = 2;
    protected static final int FINISHED = 3;

    public static final UpdateAction NULL_ACTION = new UpdateAction<Object>() {
        @Override
        public
        void onEvent(final Object tween) {
        }
    };
    protected static final UpdateAction FLUSH_READ_ACTION = new UpdateAction<Object>() {
        @Override
        public
        void onEvent(final Object tween) {
            Tween.flushRead();
        }
    };
    protected static final UpdateAction FLUSH_WRITE_ACTION = new UpdateAction<Object>() {
        @Override
        public
        void onEvent(final Object tween) {
            Tween.flushWrite();
        }
    };

    // we are a simple state machine...
    protected int state = 0;

	// General
	private int repeatCountOrig;
	private int repeatCount;

	private boolean canAutoReverse;
    private boolean isPaused;

    /** Used by tween */
    protected boolean isKilled;

    /** Used by tween */
    protected boolean isInitialized;

	// Timings
    private float startDelay;  // this is the initial delay at the start of a timeline/tween (only happens once). (doesn't change)
	private float repeatDelay; // this is the delay when a timeline/tween is repeating (doesn't change)

    /** Used by tween */
    protected float duration; // how long the timeline/tween runs (doesn't change)


    // represents the amount of time spent in the current iteration or delay
    // protected because our timeline has to be able to adjust for delays when initially building the system.
    // when FORWARDS - if <= 0, it is a delay
    // when REVERSE - if >= duration, it is a delay
    /** Used by timeline */
    protected float currentTime;

    private static final boolean START_VALUES = true;
    private static final boolean TARGET_VALUES = false;

    // Direction state
    private static final boolean FORWARDS = true;
    private static final boolean REVERSE = false;
    private boolean direction = FORWARDS; // default state is forwards

    /** Depending on the state, sometimes we trigger begin events */
    private boolean canTriggerBeginEvent;
    private boolean isInAutoReverse;

    /** Used by tween manager */
    protected boolean isDuringUpdate;

	// Misc
	private Object userData;

    /** Used by tween manager */
    protected boolean isAutoRemoveEnabled;
    protected boolean isAutoStartEnabled;

    private UpdateAction startEventCallback = NULL_ACTION;
    private UpdateAction endEventCallback = NULL_ACTION;

    // callbacks (optimized for fast call w/ many callbacks). Verification for multiple triggers is on add.
    private final List<TweenCallback> forwards_Begin = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> forwards_Start = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> forwards_End = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> forwards_Complete = new CopyOnWriteArrayList<TweenCallback>();

    private final List<TweenCallback> reverse_Begin = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> reverse_Start = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> reverse_End = new CopyOnWriteArrayList<TweenCallback>();
    private final List<TweenCallback> reverse_Complete = new CopyOnWriteArrayList<TweenCallback>();


    // -------------------------------------------------------------------------

    protected
    void reset() {
        repeatCount = repeatCountOrig = 0;
        state = 0;

        duration = startDelay = repeatDelay = currentTime = 0.0F;
        isPaused = isKilled = isInAutoReverse = isDuringUpdate = isInitialized = false;
        canTriggerBeginEvent = true;

        clearCallbacks();
        userData = null;
        endEventCallback = startEventCallback = NULL_ACTION;

        isAutoRemoveEnabled = isAutoStartEnabled = true;
    }

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

    /**
     * Clears all of the callback.
     */
    @SuppressWarnings("unchecked")
    public
    T clearCallbacks() {
        // thread safe
        forwards_Begin.clear();
        forwards_Start.clear();
        forwards_End.clear();
        forwards_Complete.clear();

        reverse_Begin.clear();
        reverse_Start.clear();
        reverse_End.clear();
        reverse_Complete.clear();

        return (T) this;
    }

    /**
     * Adds a callback. By default, it will be fired at the completion of the tween or timeline (event COMPLETE). If you want to change
     * this behavior use the {@link TweenCallback#TweenCallback(int)} constructor.
     *
     * @see TweenCallback
     */
    @SuppressWarnings("unchecked")
    public final
    T addCallback(final TweenCallback callback) {
        // thread safe
        int triggers = callback.triggers;

        if ((triggers & TweenCallback.Events.BEGIN) == TweenCallback.Events.BEGIN) {
            forwards_Begin.add(callback);
        }
        if ((triggers & TweenCallback.Events.START) == TweenCallback.Events.START) {
            forwards_Start.add(callback);
        }
        if ((triggers & TweenCallback.Events.END) == TweenCallback.Events.END) {
            forwards_End.add(callback);
        }
        if ((triggers & TweenCallback.Events.COMPLETE) == TweenCallback.Events.COMPLETE) {
            forwards_Complete.add(callback);
        }

        if ((triggers & TweenCallback.Events.BACK_BEGIN) == TweenCallback.Events.BACK_BEGIN) {
            reverse_Begin.add(callback);
        }
        if ((triggers & TweenCallback.Events.BACK_START) == TweenCallback.Events.BACK_START) {
            reverse_Start.add(callback);
        }
        if ((triggers & TweenCallback.Events.BACK_END) == TweenCallback.Events.BACK_END) {
            reverse_End.add(callback);
        }
        if ((triggers & TweenCallback.Events.BACK_COMPLETE) == TweenCallback.Events.BACK_COMPLETE) {
            reverse_Complete.add(callback);
        }

        return (T) this;
    }

	/**
	 * Adds a start delay to the tween or timeline in seconds.
	 *
	 * @param delay A duration in seconds
     *
	 * @return The current object, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
    public
    T delay(final float delay) {
        this.startDelay += delay;
        this.currentTime -= delay;
        return (T) this;
    }

    /**
	 * Kills the tween or timeline. If you are using a TweenManager, this object will be removed automatically.
	 */
	public
    void kill() {
        isKilled = true;
	}

	/**
	 * Stops and resets the tween or timeline, and sends it to its pool, for later reuse.
     * <p>
     * If you use a {@link TweenManager}, this method is automatically called once the animation is complete.
	 */
	public
    void free() {
	}

	/**
	 * Pauses the tween or timeline. Further update calls won't have any effect.
	 */
	public
    void pause() {
        isPaused = true;
	}

	/**
	 * Resumes the tween or timeline to it's previous state. Has no effect is it was not already paused.
	 */
	public
    void resume() {
        isPaused = false;
	}

	/**
	 * Repeats the tween or timeline for a given number of times.
     *
	 * @param count The number of repetitions. For infinite repetition, use {@link Tween#INFINITY} or -1.
	 * @param delay A delay between each iteration, in seconds.
     *
	 * @return The current tween or timeline, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T repeat(final int count, final float delay) {
        if (count < -1) {
            throw new RuntimeException("Count " + count + " is an invalid option. It must be -1 (Tween.INFINITY) for infinite or > 0 for " +
                                       "finite.");
        }

        repeatCountOrig = count;
        repeatCount = repeatCountOrig;
        repeatDelay = delay;
        canAutoReverse = false;

        return (T) this;
	}

	/**
	 * Repeats the tween or timeline for a given number of times.
     * </p>
	 * Once an iteration is complete, it will be played in reverse.
	 *
	 * @param count The number of repetitions. For infinite repetition, use {@link Tween#INFINITY} or -1.
	 * @param delay A delay before each repetition, in seconds.
     *
	 * @return The current tween or timeline, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T repeatAutoReverse(final int count, final float delay) {
        repeat(count, delay);

        canAutoReverse = true;

        return (T) this;
	}

    /**
     * Sets the "start" callback, which is called when the tween/timeline starts running.
     *
     * @param startCallback this is the object that will be notified when the tween/timeline starts running
     *
     * @return The current tween or timeline, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public final
    T setStartCallback(final UpdateAction<T> startCallback) {
        if (startCallback == null) {
            throw new RuntimeException("Callback cannot be null! Use BaseTween.NULL_ACTION if you wish to 'unset' the callback");
        }

        this.startEventCallback = startCallback;
        return (T) this;
    }

    /**
     * Sets the "end" callback, which is called when the tween/timeline finishes running.
     *
     * @param endCallback this is the object that will be notified when the tween/timeline finishes running
     *
     * @return The current tween or timeline, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public final
    T setEndCallback(final UpdateAction<T> endCallback) {
        if (endCallback == null) {
            throw new RuntimeException("Callback cannot be null! Use BaseTween.NULL_ACTION if you wish to 'unset' the callback");
        }

        this.endEventCallback = endCallback;
        return (T) this;
    }

    /**
     * Prepares the state of the tween before running (or initializing)
     */
    protected
    void setup() {
        canTriggerBeginEvent = true;
        state = START;
    }


    /**
     * Starts or restarts the object unmanaged. You will need to take care of its life-cycle. If you want the tween to be managed for you,
     * use a {@link TweenManager}.
     *
     * @return The current object, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public
    T start() {
        setup();
        return (T) this;
    }

    /**
     * Convenience method to add an object to a manager. Its life-cycle will be handled for you. Relax and enjoy the animation.
     *
     * @return The current object, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public
    T start(final TweenManager manager) {
        manager.add(this);
        return (T) this;
    }

    /**
     * Necessary only if another thread can modify your tweens/timelines. You must call {@link Tween#flushWrite()} in the other thread,
     * so the tween/timelines can be aware of the changes.
     * </p>
     * This sets the {@link BaseTween#setEndCallback(UpdateAction)}, so if you implement your own, you should call
     * {@link Tween#flushRead()} in your callback implementation.
     * </p>
     * <b>This is only necessary to set on ONE tween/timeline/manager, as all threads and tween objects will be correct after this call
     *    is complete.</b>
     *
     * @return The manager, for instruction chaining.
     */
    @SuppressWarnings("unchecked")
    public T syncOnStart() {
        startEventCallback = FLUSH_READ_ACTION;
        return (T) this;
    }

    /**
     * Necessary if another thread will be reading the values set by these tweens/timelines. You must call {@link Tween#flushRead()}
     * in the other thread, before accessing the target object's values.
     * </p>
     * This sets the {@link BaseTween#setEndCallback(UpdateAction)}, so if you implement your own, you should call
     * {@link Tween#flushWrite()} in your callback implementation.
     * </p>
     * <b>This is only necessary to set on ONE tween/timeline/manager, as all threads and tween objects will be correct after this call
     *    is complete.</b>
     *
     * @return The manager, for instruction chaining.
     */
    @SuppressWarnings("unchecked")
    public T syncOnEnd() {
        endEventCallback = FLUSH_WRITE_ACTION;
        return (T) this;
    }

	// -------------------------------------------------------------------------
	// Getters
	// -------------------------------------------------------------------------

    /**
     * Gets the current time point of a Timeline/Tween in seconds
     */
    public
    float getCurrentTime() {
        return currentTime;
    }

	/**
	 * Gets the delay of the Timeline/Tween in seconds. Nothing will happen until this delay is complete.
	 */
	public
    float getStartDelay() {
        return startDelay;
	}

	/**
	 * Gets the duration of a Timeline/Tween "single iteration" (not counting repeats) in seconds
	 */
	public
    float getDuration() {
        return duration;
	}

    /**
     * Returns the complete duration, including initial delay and repetitions in seconds
     * <p>
     * The formula is as follows:
     * <pre>
     * fullDuration = delay + duration + ((repeatDelay + duration) * repeatCount)
     * </pre>
     */
    public
    float getFullDuration() {
        if (repeatCountOrig < 0) {
            return -1;
        }
        return startDelay + duration + ((repeatDelay + duration) * repeatCountOrig);
    }

	/**
	 * Gets the number of iterations that will be played.
	 */
	public
    int getRepeatCount() {
        return repeatCountOrig;
	}

	/**
	 * Gets the delay occurring between two iterations in seconds
	 */
	public
    float getRepeatDelay() {
        return repeatDelay;
	}

    /**
     * Returns the direction the tween/timeline currently is in.
     * <p/>
     * Reverse direction can be impacted by a negative value for {@link #update(float)} or via a tween reversing direction because of
     * {@link #repeatAutoReverse(int, float)}
     *
     * @return true if the current tween stage is in the forwards direction, false if reverse (or Backwards)
     */
    public final
    boolean getDirection() {
        return direction;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * @return true if the Timeline/Tween is waiting inside of a delay.
     */
    public final
    boolean isInDelay() {
        return state == START;
    }

    /**
     * @return true if the timeline/tween is currently "auto-reversing" in it's direction.
     */
    public final
    boolean isInAutoReverse() {
        return isInAutoReverse;
    }


    /**
	 * Returns true if the Timeline/Tween has been initialized. This is the most accurate method to determine if a Timeline/Tween has
     * been started.
	 */
	public
    boolean isInitialized() {
        return isInitialized;
	}

	/**
	 * Returns true if the Timeline/Tween is finished (i.e. if the tween has reached its end or has been killed). A tween may be restarted
     * by a timeline when there is a direction change in the timeline.
     * </p>
     * If you don't use a TweenManager, you may want to call {@link BaseTween#free()} to reuse the object later.
	 */
	public
    boolean isFinished() {
        return state == FINISHED || isKilled;
	}

	/**
	 * Returns true if the tween automatically reverse when complete.
	 */
	public
    boolean canAutoReverse() {
        return canAutoReverse;
	}

	/**
	 * Returns true if the tween or timeline is currently paused.
	 */
	public
    boolean isPaused() {
        return isPaused;
	}

    // -------------------------------------------------------------------------
    // User Data
    // -------------------------------------------------------------------------

    /**
     * Attaches an object to this tween or timeline. It can be useful in order
     * to retrieve some data from a TweenCallback.
     *
     * @param data Any kind of object.
     *
     * @return The current tween or timeline, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public
    T setUserData(final Object data) {
        userData = data;
        return (T) this;
    }

    /**
     * Gets the attached data, or null if none.
     */
    @SuppressWarnings("unchecked")
    public
    T getUserData() {
        return (T) userData;
    }

	// -------------------------------------------------------------------------
	// Abstract API
	// -------------------------------------------------------------------------

    protected abstract
    boolean containsTarget(final Object target);

    protected abstract
    boolean containsTarget(final Object target, final int tweenType);

    /**
     * Updates a timeline's children. Only called during State.RUN
     */
    protected abstract
    void update(final boolean updateDirection, final float delta);

    /**
     * Forces a Timeline/Tween to have it's start/target values
     *
     * @param updateDirection direction in which the force is happening. Affects children iteration order (timelines) and start/target
     *                        values (tweens)
     * @param updateValue this is the start (true) or target (false) to set the tween to.
     */
    protected abstract
    void setValues(final boolean updateDirection, final boolean updateValue);

	// -------------------------------------------------------------------------
	// Protected API
	// -------------------------------------------------------------------------

    protected
    void initializeValues() {
    }

    /**
     * Kills every tweens associated to the given target. Will also kill every timelines containing a tween associated to the given target.
     *
     * @return true if the target was killed, false if we do not contain the target, and it was not killed
     */
    protected
    boolean killTarget(final Object target) {
        if (containsTarget(target)) {
            kill();
            return true;
        }

        return false;
    }

    /**
     * Kills every tweens associated to the given target and tween type. Will also kill every timelines containing a tween associated to
     * the given target and tween type.
     *
     * @return true if the target was killed, false if we do not contain the target, and it was not killed
     */
    protected
    boolean killTarget(final Object target, final int tweenType) {
        if (containsTarget(target, tweenType)) {
            kill();
            return true;
        }

        return false;
    }

    /**
     * Adjust the tween for when repeat + auto-reverse is used
     *
     * @param updateDirection the future direction for all children
     */
    protected
    void adjustForRepeat_AutoReverse(final boolean updateDirection) {
        direction = updateDirection;
        state = START;

        if (updateDirection) {
            currentTime = 0;
        }
        else {
            currentTime = duration;
        }
    }

    /**
     * Adjust the current time (set to the start value for the tween) and change state to DELAY.
     * </p>
     * For timelines, this also changes what the current tween is (for when iterating over tweens)
     *
     * @param updateDirection the future direction for all children
     */
    protected
    void adjustForRepeat_Linear(final boolean updateDirection) {
        direction = updateDirection;
        state = START;

        if (direction) {
            currentTime = 0;
        }
        else {
            currentTime = duration;
        }
    }


    // -------------------------------------------------------------------------
	// Update engine
	// -------------------------------------------------------------------------

    /**
     * Updates the tween or timeline state and values.
     * <p>
     * <b>You may want to use a TweenManager to update objects for you.</b>
     * <p>
     * Slow motion, fast motion and backward play can be easily achieved by tweaking this delta time.
     * <p>
     * Multiply it by -1 to play the animation backward, or by 0.5 to play it twice-as-slow than its normal speed.
     * <p>
     * <p>
     * The tween manager doesn't call this method, it correctly calls updateState + updateValues on timeline/tweens
     * </p>
     * Copyright dorkbox, llc
     *
     * @param delta the time (in seconds) that has elapsed since the last update
     *
     * @return true if this tween/timeline is finished (STATE = FINISHED)
     */
    @SuppressWarnings({"unchecked", "Duplicates"})
    public
    float update(float delta) {
        isDuringUpdate = true;

        if (isPaused || isKilled) {
            return delta;
        }

        if (isInAutoReverse) {
            delta = -delta;
        }

        // the INITIAL, incoming delta from the app, will be positive or negative.
        boolean direction = delta >= -0.0F;
        this.direction = direction;

        final float duration = this.duration;

        /*
         * DELAY - (delay) initial start delay, only happens once, during init
         * R.DELAY - (repeatDelay) delay between repeat iterations, if there are more than one.
         *
         * there are two modes for repeat. LINEAR and AUTO_REVERSE
         *
         * LINEAR:
         *                BEGIN                                     COMPLETE
         *                START      END                 START      END
         *                v          v                   v          v
         * |---[DELAY]----[XXXXXXXXXX]->>-[R.DELAY]-->>--[XXXXXXXXXX]
         *
         *
         * AUTO_REVERSE
         *                BEGIN      COMPLETE
         *                START      END
         *                v          v
         * |---[DELAY]----[XXXXXXXXXX]──────────-─────╮
         *            ╭╴  [XXXXXXXXXX]-<<-[R.DELAY] <─╯
         *            │   ^          ^
         *            │   bEND       bSTART
         *            │   bCOMPLETE  bBEGIN
         *            │
         *            ╰╴> [R.DELAY]->>-[XXXXXXXXXX]  ╶╮
         *            ╭╴  [XXXXXXXXXX]-<<-[R.DELAY] <─╯
         *            ╰╴> [R.DELAY]->>-[XXXXXXXXXX]  ...
         *
         * Time must "cross the finish line" in order for the tween to be considered finished.
         */

        // The LAST tween (in a timeline) that was modified is what keeps track of "overflow" of time, which is when an animation runs
        // longer that the tween duration. This is necessary in order to accurately reverse the animation and have the correct delays

        // FORWARDS: 0 > time <= duration
        // REVERSE:  0 >= time < duration   (reverse always goes from duration -> 0)

        startEventCallback.onEvent(this);

        do {
            float newTime = currentTime + delta;

            if (direction) {
                // {FORWARDS}
                // <editor-fold>

                // FORWARDS: 0 > time <= duration
                switch (state) {
                    case START: {
                        if (newTime <= 0.0F) {
                            // still in delay
                            currentTime = newTime;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        currentTime = 0.0F;

                        if (canTriggerBeginEvent) {
                            canTriggerBeginEvent = false;

                            // initialize during start (but after delay), so that it's at the same point in either direction
                            if (!isInitialized) {
                                isInitialized = true;
                                initializeValues();
                            }

                            final List<TweenCallback> callbacks = this.forwards_Begin;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.BEGIN, this);
                            }
                        }

                        final List<TweenCallback> callbacks = this.forwards_Start;
                        for (int i = 0, n = callbacks.size(); i < n; i++) {
                            callbacks.get(i).onEvent(TweenCallback.Events.START, this);
                        }

                        // goto next state
                        state = RUN;

                        // -- update is REVERSE so that the FIRST tween data takes priority, if there are
                        //    multiple tweens that have the same target
                        setValues(REVERSE, START_VALUES);

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = newTime;

                        // FALLTHROUGH
                    }
                    case RUN: {
                        if (newTime <= duration) {
                            // still in running forwards
                            currentTime = newTime;

                            update(FORWARDS, delta);

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        state = FINISHED;
                        currentTime = duration;

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = newTime - duration;

                        final int repeatCountStack = repeatCount;
                        ////////////////////////////////////////////
                        ////////////////////////////////////////////
                        // 1) we are done running completely
                        // 2) we flip to auto-reverse repeat mode
                        // 3) we are in linear repeat mode
                        if (repeatCountStack == 0) {
                            // {FORWARDS}{FINISHED}

                            // -- update is REVERSE so that the FIRST tween data takes priority, if there are
                            //    multiple tweens that have the same target

                            // "instant" tweens (duration 0) cannot trigger a set-to-startpoint (since they are always [enabled] while
                            // running). They are [disabled] by their parent timeline when the parent reaches the end of it's duration
                            // in the FORWARDS direction, this doesn't matter, but in REVERSE, it does.
                            setValues(REVERSE, TARGET_VALUES);


                            final List<TweenCallback> callbacks = this.forwards_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.END, this);
                            }

                            final List<TweenCallback> callbacks2 = this.forwards_Complete;
                            for (int i = 0, n = callbacks2.size(); i < n; i++) {
                                callbacks2.get(i).onEvent(TweenCallback.Events.COMPLETE, this);
                            }

                            // don't do this, because it will xfer to the next tween (if a timeline), or will get added in the FINISHED
                            // case (if not a timeline, to record "overflow" of time)
                            //    currentTime = newTime;

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = false;

                            // have to reset our repeat count, so outside repeats will start us in the correct state
                            repeatCount = repeatCountOrig;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return delta;
                        }
                        else if (canAutoReverse) {
                            // {FORWARDS}{AUTO_REVERSE}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            final List<TweenCallback> callbacks = this.forwards_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.END, this);
                            }

                            final List<TweenCallback> callbacks2 = this.forwards_Complete;
                            for (int i = 0, n = callbacks2.size(); i < n; i++) {
                                callbacks2.get(i).onEvent(TweenCallback.Events.COMPLETE, this);
                            }

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = !isInAutoReverse; // if we are NOT in autoReverse, then "isInAutoReverse" is true if we reverse

                            // make sure any checks after this returns accurately reflect the correct REVERSE direction
                            direction = REVERSE;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustForRepeat_AutoReverse(REVERSE);
                            currentTime += repeatDelay;
                            delta = -delta;

                            // loop to new state
                            continue;
                        }
                        else {
                            // {FORWARDS}{LINEAR}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            final List<TweenCallback> callbacks = this.forwards_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.END, this);
                            }

                            isInAutoReverse = false;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustForRepeat_Linear(FORWARDS);
                            currentTime = -repeatDelay + delta;

                            // loop to new state
                            continue;
                        }
                    }
                    case FINISHED: {
                        if (newTime <= 0.0F || newTime > duration) {
                            // still in the "finished" state, and haven't been reversed somewhere
                            currentTime = newTime;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        // restart the timeline, since we've had our time adjusted to a point where we are running again.
                        state = START;

                        update(FORWARDS, delta);

                        // loop to new state
                        continue;
                    }
                    default: {
                        throw new RuntimeException("Unexpected state!! '" + state + "'");
                    }
                }

                // </editor-fold>
            }
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            else {
                // {REVERSE}
                // <editor-fold>

                // REVERSE:  0 >= time < duration   (reverse always goes from duration -> 0)
                switch (state) {
                    case START: {
                        if (newTime >= duration) {
                            // still in delay
                            currentTime = newTime;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        currentTime = duration;

                        if (canTriggerBeginEvent) {
                            canTriggerBeginEvent = false;

                            // initialize during start (but after delay), so that it's at the same point in either direction
                            if (!isInitialized) {
                                isInitialized = true;
                                initializeValues();
                            }

                            final List<TweenCallback> callbacks = this.reverse_Begin;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.BACK_BEGIN, this);
                            }
                        }

                        final List<TweenCallback> callbacks = this.reverse_Start;
                        for (int i = 0, n = callbacks.size(); i < n; i++) {
                            callbacks.get(i).onEvent(TweenCallback.Events.BACK_START, this);
                        }

                        // goto next state
                        state = RUN;

                        // -- update is FORWARDS so that the LAST tween data takes priority, if there are
                        //    multiple tweens that have the same target
                        // this is opposite of the logic in FORWARDS.START
                        setValues(FORWARDS, TARGET_VALUES);

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = -(duration - newTime);

                        // FALLTHROUGH
                    }
                    case RUN: {
                        // stay in running reverse
                        if (newTime >= 0.0F) {
                            // still in running reverse
                            currentTime = newTime;

                            update(REVERSE, delta);

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        state = FINISHED;
                        currentTime = 0.0F;

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = newTime;

                        final int repeatCountStack = repeatCount;
                        ////////////////////////////////////////////
                        ////////////////////////////////////////////
                        // 1) we are done running completely
                        // 2) we flip to auto-reverse
                        // 3) we are in linear repeat mode
                        if (repeatCountStack == 0) {
                            // {REVERSE}{FINISHED}

                            // set the "start" values, backwards because values are relative to forwards

                            // -- update is FORWARDS so that the LAST tween data takes priority, if there are
                            //    multiple tweens that have the same target
                            if (duration <= 0.000001F) {
                                // "instant" tweens (duration 0) cannot trigger a set-to-startpoint (since they are always [enabled] while
                                // running). They are [disabled] by their parent timeline when the parent reaches the end of it's duration
                                // This is why it's always set to target value (even though it's reverse)
                                setValues(FORWARDS, TARGET_VALUES);
                            }
                            else {
                                // set the "start" values, flipped because we are in reverse
                                setValues(FORWARDS, START_VALUES);
                            }

                            final List<TweenCallback> callbacks = this.reverse_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.BACK_END, this);
                            }

                            final List<TweenCallback> callbacks2 = this.reverse_Complete;
                            for (int i = 0, n = callbacks2.size(); i < n; i++) {
                                callbacks2.get(i).onEvent(TweenCallback.Events.BACK_COMPLETE, this);
                            }

                            // don't do this, because it will xfer to the next tween (if a timeline), or will get added in the FINISHED
                            // case (if not a timeline, to record "overflow" of time)
                            //    currentTime = newTime;

                            // we're done going reverse
                            canTriggerBeginEvent = true;
                            isInAutoReverse = false;

                            // have to reset our repeat count, so outside repeats will start us in the correct state
                            repeatCount = repeatCountOrig;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return delta;
                        }
                        else if (canAutoReverse) {
                            // {REVERSE}{AUTO_REVERSE}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            final List<TweenCallback> callbacks = this.reverse_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.BACK_END, this);
                            }

                            final List<TweenCallback> callbacks2 = this.reverse_Complete;
                            for (int i = 0, n = callbacks2.size(); i < n; i++) {
                                callbacks2.get(i).onEvent(TweenCallback.Events.BACK_COMPLETE, this);
                            }

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = !isInAutoReverse; // if we are NOT in autoReverse, then "isInAutoReverse" is true if we reverse

                            // make sure any checks after this returns accurately reflect the correct FORWARDS direction
                            direction = FORWARDS;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustForRepeat_AutoReverse(FORWARDS);
                            currentTime -= repeatDelay;
                            delta = -delta;

                            // loop to new state
                            continue;
                        }
                        else {
                            // {REVERSE}{LINEAR}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            final List<TweenCallback> callbacks = this.forwards_End;
                            for (int i = 0, n = callbacks.size(); i < n; i++) {
                                callbacks.get(i).onEvent(TweenCallback.Events.END, this);
                            }

                            isInAutoReverse = false;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustForRepeat_Linear(REVERSE);
                            currentTime = newTime + repeatDelay;

                            // loop to new state
                            continue;
                        }
                    }
                    case FINISHED: {
                        if (newTime < 0.0F || newTime >= duration) {
                            // still in the "finished" state, and haven't been reversed somewhere
                            currentTime = newTime;

                            isDuringUpdate = false;
                            endEventCallback.onEvent(this);
                            return 0.0F;
                        }

                        // restart the timeline, since we've had our time adjusted to a point where we are running again.
                        state = START;

                        update(REVERSE, delta);

                        // loop to new state
                        continue;
                    }
                    default: {
                        throw new RuntimeException("Unexpected state!! '" + state + "'");
                    }
                }

                // </editor-fold>
            }
        } while (true);
    }
}
