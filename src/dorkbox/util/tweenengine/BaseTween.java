/*
 * Copyright 2012 Aurelien Ribon
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
 *
 *
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
 * BaseTween is the base class of Tween and Timeline. It defines the
 * iteration engine used to play animations for any number of times, and in
 * any direction, at any speed.
 * <p/>
 * <p/>
 * It is responsible for calling the different callbacks at the right moments,
 * and for making sure that every callbacks are triggered, even if the update
 * engine gets a big delta time at once.
 * <p/>
 * <p/>
 * Additionally, floating-point operations are always slower than integer ops at
 * same data size, so internally we use INTEGER, since we want consistent
 * timelines & events, as floats will drift (they are approximations)
 *  <p/>
 *  from: http://nicolas.limare.net/pro/notes/2014/12/12_arit_speed/
 *        https://software.intel.com/en-us/forums/watercooler-catchall/topic/306267
 *
 * @see Tween
 * @see Timeline
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 * @author dorkbox, llc
 */
@SuppressWarnings("unused")
public
abstract class BaseTween<T> {
    private enum State {
        DELAY, START, RUN, END,

        /** FINISHED means EVERYTHING (including repetitions) is done */
        FINISHED
    }


    // we are a simple state machine...
    private State state = null;

	// General
	private int repeatCountOrig;
	private int repeatCount;

	private boolean canAutoReverse;
    private boolean isPaused;
    private boolean isKilled;

	// Timings
    private int startDelay;  // this is the initial delay at the start of a timeline/tween (only happens once). (doesn't change)
	private int repeatDelay; // this is the delay when a timeline/tween is repeating (doesn't change)

	protected int duration; // how long the timeline/tween runs (doesn't change)


    // represents the amount of time spent in the current iteration or delay
    // package local because our timeline has to be able to adjust for delays when initially building the system.
    // when FORWARDS - if < 0, it is a delay
    // when REVERSE - if > 0, it is a delay
    protected int currentTime;

    // Direction state
    private static final boolean FORWARDS = true;
    private static final boolean REVERSE = false;
    private boolean direction = FORWARDS; // default state is forwards

    /** Depending on the state, sometimes we trigger begin events */
    private boolean canTriggerBeginEvent;
    private boolean isInAutoReverse;

	// Misc
    private volatile long lightSyncObject = System.currentTimeMillis();
	private List<TweenCallback> callbacks = new CopyOnWriteArrayList<TweenCallback>();
	private Object userData;

    private static final TweenAction NULL_ACTION = new TweenAction<Object>() {
        @Override
        public
        void action(final Object tween) {
        }
    };

	// Package access
	boolean isAutoRemoveEnabled;
	boolean isAutoStartEnabled;
    private TweenAction updateStartEvent = NULL_ACTION;
    private TweenAction updateEndEvent = NULL_ACTION;


    // -------------------------------------------------------------------------

    protected
    void reset() {
        repeatCount = repeatCountOrig = 0;

        state = null;

        duration = repeatDelay = currentTime = 0;
        isPaused = isKilled = isInAutoReverse = false;
        canTriggerBeginEvent = true;

        callbacks.clear();
        userData = null;

        isAutoRemoveEnabled = isAutoStartEnabled = true;
        flushWrite();
    }

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

    /**
     * Flushes the visibility of all tween fields from the cache for access/use from different threads.
     * <p>
     * This does not block and does not prevent race conditions.
     *
     * @return the last time (in millis) that the field modifications were flushed
     */
    public final long flushRead() {
        return lightSyncObject;
    }

    /**
     * Flushes the visibility of all tween field modifications from the cache for access/use from different threads.
     * <p>
     * This does not block and does not prevent race conditions.
     */
    public final void flushWrite() {
        lightSyncObject = System.currentTimeMillis();
    }


    /**
     * Adds a callback. By default, it will be fired at the completion of the
     * tween or timeline (event COMPLETE). If you want to change this behavior
     * use the {@link TweenCallback#setTriggers(int)} method.
     *
     * @see TweenCallback
     */
    @SuppressWarnings("unchecked")
    public
    T addCallback(final TweenCallback callback) {
        // thread safe
        this.callbacks.add(callback);
        return (T) this;
    }


	/**
	 * Builds and validates the object. Only needed if you want to finalize a
	 * tween or timeline without starting it, since a call to ".start()" also
	 * calls this method.
	 *
	 * @return The current object, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T build() {
		return (T) this;
	}


    /**
     * Adjusts the startDelay of the tween/timeline during initialization
     * @param startDelay how many milliSeconds to adjust the start delay
     */
    protected void adjustStartDelay(final int startDelay) {
        this.startDelay += startDelay;
    }


    /**
     * Clears all of the callback.
     */
    @SuppressWarnings("unchecked")
    public
    T clearCallbacks() {
        // thread safe
        this.callbacks.clear();
        return (T) this;
    }

	/**
	 * Adds a start delay to the tween or timeline in MilliSeconds.
	 *
	 * @param delay A duration in MilliSeconds
     *
	 * @return The current object, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
    public
    T delay(final int delay) {
        flushRead();

        if (state != null) {
            throw new RuntimeException("You can't modify the delay if it is already started");
        }

        this.startDelay += delay;
        flushWrite();

        return (T) this;
    }

    /**
	 * Kills the tween or timeline. If you are using a TweenManager, this object
	 * will be removed automatically.
	 */
	public
    void kill() {
        isKilled = true;
        flushWrite();
	}

	/**
	 * Stops and resets the tween or timeline, and sends it to its pool, for
	 * later reuse.
     * <p>
     * If you use a {@link TweenManager}, this method is automatically called
     * once the animation is complete.
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
        flushWrite();
	}

	/**
	 * Resumes the tween or timeline to it's previous state. Has no effect is it was not already paused.
	 */
	public
    void resume() {
        isPaused = false;
        flushWrite();
	}

	/**
	 * Repeats the tween or timeline for a given number of times.
     *
	 * @param count The number of repetitions. For infinite repetition,
	 *              use {@link Tween#INFINITY} or -1.
	 * @param delayMilliSeconds A delay between each iteration, in MILLI-SECONDS.
     *
	 * @return The current tween or timeline, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T repeat(final int count, final int delayMilliSeconds) {
        flushRead();

        if (state != null) {
            throw new RuntimeException("You can't change the repetitions of a tween or timeline once it is started");
        }

        if (count < -1) {
            throw new RuntimeException("Count " + count + " is an invalid option. It must be -1 for infinite or > 0 for finite.");
        }

        repeatCountOrig = count;
        repeatCount = repeatCountOrig;
        repeatDelay = delayMilliSeconds;
        canAutoReverse = false;

        flushWrite();
        return (T) this;
	}

	/**
	 * Repeats the tween or timeline for a given number of times.
     * </p>
	 * Once an iteration is complete, it will be played in reverse.
	 *
	 * @param count The number of repetitions. For infinite repetition,
	 *              use {@link Tween#INFINITY} or -1.
	 * @param delayMilliSeconds A delay before each repetition, in MILLI-SECONDS.
     *
	 * @return The current tween or timeline, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T repeatAutoReverse(final int count, final int delayMilliSeconds) {
        repeat(count, delayMilliSeconds);

        canAutoReverse = true;

        flushWrite();
        return (T) this;
	}

	/**
	 * Attaches an object to this tween or timeline. It can be useful in order
	 * to retrieve some data from a TweenCallback.
	 *
	 * @param data Any kind of object.
	 * @return The current tween or timeline, for chaining instructions.
	 */
    @SuppressWarnings("unchecked")
	public
    T setUserData(final Object data) {
		userData = data;
        flushWrite();
		return (T) this;
	}

    /**
     * Starts or restarts the object unmanaged. You will need to take care of
     * its life-cycle. If you want the tween to be managed for you, use a
     * {@link TweenManager}.
     *
     * @return The current object, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public
    T start() {
        build();
        // initialize all of the starting values
        currentTime = 0;
        initialize();

        canTriggerBeginEvent = true;
        currentTime = -startDelay;

        // goto delay or start
        if (startDelay > 0) {
            state = State.DELAY;
        }
        else {
            state = State.START;
        }

        flushWrite();
        return (T) this;
    }

    /**
     * Convenience method to add an object to a manager. Its life-cycle will be
     * handled for you. Relax and enjoy the animation.
     *
     * @return The current object, for chaining instructions.
     */
    @SuppressWarnings("unchecked")
    public
    T start(final TweenManager manager) {
        manager.add(this);
        return (T) this;
    }

	// -------------------------------------------------------------------------
	// Getters
	// -------------------------------------------------------------------------

    /**
     * Gets the current time point of a Timeline/Tween.
     */
    public
    int getCurrentTime() {
        flushRead();
        return currentTime;
    }

	/**
	 * Gets the delay of the tween or timeline. Nothing will happen before
	 * this delay.
	 */
	public
    int getStartDelay() {
        flushRead();
        return startDelay;
	}

	/**
	 * Gets the duration of a single iteration.
	 */
	public
    int getDuration() {
        flushRead();
        return duration;
	}

    /**
     * Returns the complete duration, including initial delay and repetitions.
     * <p>
     * The formula is as follows:
     * <pre>
     * fullDuration = delay + duration + ((repeatDelay + duration) * repeatCount)
     * </pre>
     */
    public
    int getFullDuration() {
        flushRead();
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
        flushRead();
        return repeatCountOrig;
	}

	/**
	 * Gets the delay occurring between two iterations.
	 */
	public
    int getRepeatDelay() {
        flushRead();
        return repeatDelay;
	}

	/**
	 * Gets the attached data, or null if none.
	 */
	@SuppressWarnings("unchecked")
    public
    T getUserData() {
        flushRead();
        return (T) userData;
	}

    /**
     * Returns the direction the tween/timeline currently is in.
     *  <p/>
     * Reverse direction can be impacted by a negative value for {@link #update(float)}
     * or {@link #update(int), or via a tween reversing direction because
     * of {@link #repeatAutoReverse(int, int)}
     *
     * @return true if the current tween stage is in the forwads direction, false if reverse (or Backwards)
     */
    public final
    boolean getDirection() {
        flushRead();
        return direction;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * @return true if the timeline/tween is waiting inside of a delay.
     */
    public final
    boolean isInDelay() {
        flushRead();
        return state == State.DELAY;
    }

    /**
     * @return true if the timeline/tween is in waiting inside of a delay.
     */
    public final
    boolean isInAutoReverse() {
        flushRead();

        return isInAutoReverse;
    }


    /**
	 * Returns true if the tween or timeline has been started.
	 */
	public
    boolean isStarted() {
        flushRead();
        return state != null;
	}

	/**
	 * Returns true if the tween is finished (i.e. if the tween has reached
	 * its end or has been killed). A tween may be restarted by a timeline
     * when there is a direction change in the timeline.
     *
     * If you don't use a TweenManager, you may want to call
     * {@link BaseTween#free()} to reuse the object later.
	 */
	public
    boolean isFinished() {
        flushRead();
        return state == State.FINISHED || isKilled;
	}

	/**
	 * Returns true if the tween automatically reverse when complete.
	 */
	public
    boolean canAutoReverse() {
        flushRead();
        return canAutoReverse;
	}

	/**
	 * Returns true if the tween or timeline is currently paused.
	 */
	public
    boolean isPaused() {
        flushRead();
        return isPaused;
	}

	// -------------------------------------------------------------------------
	// Abstract API
	// -------------------------------------------------------------------------

    public abstract
    T onUpdateStart(final TweenAction<T> action);

    public abstract
    T onUpdateEnd(final TweenAction<T> action);

    protected abstract
    boolean containsTarget(final Object target);

    protected abstract
    boolean containsTarget(final Object target, final int tweenType);

    /**
     * When done with all the adjustments and notifications, update the object
     *
     * If a tween is before start (reverse) or past end (forwards), this it will "snap" to the start/end endpoints
     * @param animationDirection direction that the animation is going. FORWARDS(true) or REVERSE(false)
     * @param delta we always pass in the unmodified delta values, so the children have accurate, internal adjustments
     */
    protected abstract
    void doUpdate(final boolean animationDirection, final int delta);

	// -------------------------------------------------------------------------
	// Protected API
	// -------------------------------------------------------------------------

    // used by the build process to correct start delay for parent timeline
    protected
    void resetStartDelay() {
        this.startDelay = 0;
    }

	protected
    void initialize() {
	}

	@SuppressWarnings("Convert2streamapi")
    protected
    void callCallbacks(final int type) {
        for (int i = 0, n = callbacks.size(); i < n; i++) {
            final TweenCallback callback = callbacks.get(i);
            if ((callback.triggers & type) > 0) {
                callback.onEvent(type, this);
            }
        }
    }

    protected
    void killTarget(final Object target) {
        if (containsTarget(target)) {
            kill();
        }
    }


    protected
    void killTarget(final Object target, final int tweenType) {
        if (containsTarget(target, tweenType)) {
            kill();
        }
    }

    protected
    void setUpdateStartEvent(final TweenAction<T> updateStartEvent) {
        this.updateStartEvent = updateStartEvent;
        flushWrite();
    }

    protected
    void setUpdateEndEvent(final TweenAction<T> updateEndEvent) {
        this.updateEndEvent = updateEndEvent;
        flushWrite();
    }

    // used to recursively adjust the current time for children in a timeline (or self, if a tween)
    protected
    void adjustTime(final int offset, final boolean direction) {
        currentTime += offset;
        this.direction = direction;
    }

    // -------------------------------------------------------------------------
	// Update engine
	// -------------------------------------------------------------------------

    /**
     * Updates the tween or timeline state. <b>You may want to use a
     * TweenManager to update objects for you.</b>
     * <p>
     * Slow motion, fast motion and backward play can be easily achieved by
     * tweaking this delta time.
     * <p>
     * Multiply it by -1 to play the animation backward, or by 0.5
     * to play it twice-as-slow than its normal speed.
     * <p>
     * <p>
     * <b>THIS IS NOT PREFERRED</b>
     *
     * @param delta A delta time in SECONDS between now and the last call.
     */
    public
    void update(final float delta) {
        // from: http://nicolas.limare.net/pro/notes/2014/12/12_arit_speed/
        //       https://software.intel.com/en-us/forums/watercooler-catchall/topic/306267
        // Floating-point operations are always slower than integer ops at same data size.
        // internally we also want to use INTEGER, since we want consistent timelines, as floats will drift (they are approximations)
        final int deltaMilliSeconds = (int) (delta * 1000F);

        update(deltaMilliSeconds);
    }

    /**
	 * Updates the tween or timeline state. <b>You may want to use a
	 * TweenManager to update objects for you.</b>
	 * <p>
	 * Slow motion, fast motion and backward play can be easily achieved by
	 * tweaking this delta time.
     * <p>
     * Multiply it by -1 to play the animation backward, or by 0.5
     * to play it twice-as-slow than its normal speed.
	 *
	 * @param delta A delta time in MILLI-SECONDS between the previous call and this call.
	 */
	@SuppressWarnings({"Duplicates", "unchecked"})
    public
    void update(int delta) {
        // redone by dorkbox, llc
        flushRead();

        if (isPaused || state == null || isKilled) {
            // null state means we didn't properly start (so calling updated before start)
            return;
        }

        updateStartEvent.action(this);

        // by DEFAULT, 0 means we are going FORWARDS
        if (delta == 0) {
            doUpdate(FORWARDS, delta);

            flushWrite();
            updateEndEvent.action(this);
            return;
        }

        if (isInAutoReverse) {
            delta = -delta;
        }

        // the INITIAL, incoming delta from the app, will be positive or negative.
        boolean direction = delta > 0;
        this.direction = direction;

        final int duration = this.duration;

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
         */

        // FORWARDS: 0 >= time < duration
        // REVERSE:  0 > time <= duration   (reverse always goes from duration -> 0)


        // canAutoReverse - only present with repeatDelay, and will cause an animation to reverse once iteration + repeatDelay is complete

        /* DELAY: endpoints are
         * delay = 0-5, the delay is over when time>5
         *
         *    0           5    6         9
         *    v           v    v         v
         *    [---DELAY---]->>-[XXXXXXXXX]
         */


        // first we have to fire all of our events and adjust our state. Once done adjusting state and firing events to our callbacks
        // it will break from this loop. If we are finished, it will run update directly (and then return, instead of breaking from loop)

        // STATES
        // - we are in start delay (already handled)
        // - we are finished
        // - we are inside a repeat delay
        // - we are running

        // TRANSITIONS
        //  startDelay/repeatDelay -> running
        //  running -> finished
        //  running -> repeat (current direction) + repeatDelay
        //  running -> repeat (opposite direction) + repeatDelay
        //  finished -> running (forceRestart, when repeats/reverse occurs)


        do {
            int newTime = currentTime + delta;

            if (direction) {
                // {FORWARDS}
                // <editor-fold>

                // FORWARDS:  0 >= time < duration
                switch (state) {
                    case DELAY: {
                        // stay in delay, or goto next state?
                        if (newTime < 0) {
                            // still in delay
                            currentTime = newTime;
                            doUpdate(FORWARDS, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        } else {
                            // fallthrough to next state. Adjust state, currentTime, newTime, delta
                            state = State.START;

                            // FALLTHROUGH
                        }
                    }
                    case START: {
                        currentTime = 0; // just for callbacks

                        if (canTriggerBeginEvent) {
                            canTriggerBeginEvent = false;
                            callCallbacks(TweenCallback.Events.BEGIN);
                        }

                        callCallbacks(TweenCallback.Events.START);

                        currentTime = newTime;

                        // always goto next state (it will determine weather to stay or not)
                        state = State.RUN;

                        // FALLTHROUGH
                    }
                    case RUN: {
                        // stay in running forwards (inside update cycle), or continue to next state?
                        if (newTime < duration) {
                            // still in running forwards
                            currentTime = newTime;
                            doUpdate(FORWARDS, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        } else {
                            // goto end state
                            state = State.END;
                        }

                        // FALLTHROUGH
                    }
                    case END: {
                        currentTime = duration;
                        doUpdate(FORWARDS, delta);

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = newTime - duration;
                        callCallbacks(TweenCallback.Events.END);

                        final int repeatCountStack = repeatCount;
                        ////////////////////////////////////////////
                        ////////////////////////////////////////////
                        // 1) we are done running completely
                        // 2) we flip to auto-reverse repeat mode
                        // 3) we are in linear repeat mode
                        if (repeatCountStack == 0) {
                            // {FORWARDS}{FINISHED}
                            state = State.FINISHED;

                            callCallbacks(TweenCallback.Events.COMPLETE);

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = false;
                            currentTime = newTime;

                            // nothing left to do
                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        }
                        else if (canAutoReverse) {
                            // {FORWARDS}{AUTO_REVERSE}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            callCallbacks(TweenCallback.Events.COMPLETE);

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = !isInAutoReverse; // if we are NOT in autoReverse, then "isInAutoReverse" is true if we reverse
                            currentTime = newTime;

                            // make sure any checks after this returns accurately reflect the correct REVERSE direction
                            direction = REVERSE;
                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustTime(repeatDelay - delta, REVERSE);
                            delta = -delta;

                            // always goto next state (it will determine weather to stay or not)
                            if (repeatDelay > 0) {
                                state = State.DELAY;
                            } else {
                                state = State.START;
                            }

                            // loop to new state
                            continue;
                        }
                        else {
                            // {FORWARDS}{LINEAR}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            isInAutoReverse = false;
                            currentTime = newTime;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustTime(-newTime - repeatDelay, FORWARDS);

                            // always goto next state (it will determine weather to stay or not)
                            if (repeatDelay > 0) {
                                state = State.DELAY;
                            } else {
                                state = State.START;
                            }

                            // loop to new state
                            continue;
                        }
                    }
                    case FINISHED: {
                        if (newTime < 0 || newTime >= duration) {
                            // still in the "finished" state, and haven't been reversed somewhere
                            currentTime = newTime;
                            doUpdate(FORWARDS, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        }

                        // restart the timeline, since we've had our time adjusted to a point where we are running again.
                        state = State.START;

                        // loop to new state
                        continue;
                    }
                    default: {
                        throw new RuntimeException("Unexpected state!! " + state);
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

                // REVERSE:  0 > time <= duration   (reverse always goes from duration -> 0)
                switch (state) {
                    case DELAY: {
                        // stay in delay, or goto next state?
                        if (newTime > duration) {
                            // still in delay
                            currentTime = newTime;
                            doUpdate(REVERSE, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        } else {
                            // fallthrough to next state. Adjust state, currentTime, newTime, delta
                            state = State.START;

                            // FALLTHROUGH
                        }
                    }
                    case START: {
                        currentTime = duration;

                        if (canTriggerBeginEvent) {
                            canTriggerBeginEvent = false;
                            callCallbacks(TweenCallback.Events.BACK_BEGIN);
                        }

                        callCallbacks(TweenCallback.Events.BACK_START);

                        currentTime = newTime;

                        // always goto next state (it will determine weather to stay or not)
                        state = State.RUN;

                        // FALLTHROUGH
                    }
                    case RUN: {
                        // stay in running reverse (inside update cycle), or continue to next state?
                        if (newTime > 0) {
                            // still in running reverse
                            currentTime = newTime;
                            doUpdate(REVERSE, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        } else {
                            // goto end state
                            state = State.END;
                        }

                        // FALLTHROUGH
                    }
                    case END: {
                        currentTime = 0;
                        doUpdate(REVERSE, delta);

                        // adjust the delta so that it is shifted based on the length of (previous) iteration
                        delta = newTime;
                        callCallbacks(TweenCallback.Events.BACK_END);

                        final int repeatCountStack = repeatCount;
                        ////////////////////////////////////////////
                        ////////////////////////////////////////////
                        // 1) we are done running completely
                        // 2) we flip to auto-reverse
                        // 3) we are in linear repeat mode
                        if (repeatCountStack == 0) {
                            // {REVERSE}{FINISHED}
                            state = State.FINISHED;

                            callCallbacks(TweenCallback.Events.BACK_COMPLETE);

                            // we're done going reverse
                            canTriggerBeginEvent = true;
                            isInAutoReverse = false;
                            currentTime = newTime;

                            // nothing left to do
                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        }
                        else if (canAutoReverse) {
                            // {REVERSE}{AUTO_REVERSE}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            callCallbacks(TweenCallback.Events.BACK_COMPLETE);

                            // we're done going forwards
                            canTriggerBeginEvent = true;
                            isInAutoReverse = !isInAutoReverse; // if we are NOT in autoReverse, then "isInAutoReverse" is true if we reverse
                            currentTime = newTime;

                            // make sure any checks after this returns accurately reflect the correct REVERSE direction
                            direction = true;
                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustTime(-repeatDelay - delta, FORWARDS);
                            delta = -delta;

                            // always goto next state (it will determine weather to stay or not)
                            if (repeatDelay > 0) {
                                state = State.DELAY;
                            } else {
                                state = State.START;
                            }

                            // loop to new state
                            continue;
                        }
                        else {
                            // {REVERSE}{LINEAR}
                            if (repeatCountStack > 0) {
                                // -1 means repeat forever
                                repeatCount--;
                            }

                            isInAutoReverse = false;
                            currentTime = newTime;

                            // any extra time (what's left in delta) will be applied/calculated on the next loop around
                            adjustTime(-newTime + duration + repeatDelay, REVERSE);

                            // always goto next state (it will determine weather to stay or not)
                            if (repeatDelay > 0) {
                                state = State.DELAY;
                            } else {
                                state = State.START;
                            }

                            // loop to new state
                            continue;
                        }
                    }
                    case FINISHED: {
                        if (newTime <= 0 || newTime > duration) {
                            // still in the "finished" state, and haven't been reversed somewhere
                            currentTime = newTime;
                            doUpdate(REVERSE, delta);

                            flushWrite();
                            updateEndEvent.action(this);
                            return;
                        }

                        // restart the timeline, since we've had our time adjusted to a point where we are running again.
                        state = State.START;

                        // loop to new state
                        continue;
                    }
                    default: {
                        throw new RuntimeException("Unexpected state!! " + state);
                    }
                }

                // </editor-fold>
            }
        } while (true);
    }
}
