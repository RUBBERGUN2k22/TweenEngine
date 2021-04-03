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
package dorkbox.tweenEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Timeline can be used to create complex animations made of sequences and parallel sets of Tweens.
 * <p/>
 *
 * The following example will create an animation sequence composed of 5 parts:
 * <p/>
 *
 * 1. First, opacity and scale are set to 0 (with Tween.set() calls).<br/>
 * 2. Then, opacity and scale are animated in parallel.<br/>
 * 3. Then, the animation is paused for 1s.<br/>
 * 4. Then, position is animated to x=100.<br/>
 * 5. Then, rotation is animated to 360°.
 * <p/>
 *
 * This animation will be repeated 5 times, with a 500ms delay between each
 * iteration:
 * <br/><br/>
 *
 * <pre> {@code
 * Timeline.createSequential()
 *     .push(Tween.set(myObject, OPACITY).target(0))
 *     .push(Tween.set(myObject, SCALE).target(0, 0))
 *     .beginParallel()
 *          .push(Tween.to(myObject, OPACITY, 0.5F).target(1).ease(Quad_InOut))
 *          .push(Tween.to(myObject, SCALE, 0.5F).target(1, 1).ease(Quad_InOut))
 *     .end()
 *     .pushPause(1.0F)
 *     .push(Tween.to(myObject, POSITION_X, 0.5F).target(100).ease(Quad_InOut))
 *     .push(Tween.to(myObject, ROTATION, 0.5F).target(360).ease(Quad_InOut))
 *     .repeat(5, 0.5F)
 *     .start(myManager);
 * }</pre>
 *
 * @see Tween
 * @see TweenCallback
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 * @author dorkbox, llc
 */
@SuppressWarnings({"ForLoopReplaceableByForEach", "unused"})
public final
class Timeline extends BaseTween<Timeline> {
    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "8.3";
    }

    // -------------------------------------------------------------------------
	// Attributes
	// -------------------------------------------------------------------------

	enum Mode {SEQUENTIAL, PARALLEL}


	private final List<BaseTween<?>> children = new ArrayList<>(10);
    private BaseTween<?>[] childrenArray = null;
    private int childrenSize;
    private int childrenSizeMinusOne;

    private Mode mode;

    private Timeline parent;

    // current is used for TWO things.
    //  - Tracking what to start/end during construction
    //  - Tracking WHICH tween/timeline (of the children) is currently being run.
    private BaseTween<?> current;
    private int currentIndex;

	// -------------------------------------------------------------------------
	// Setup
	// -------------------------------------------------------------------------

	Timeline(final TweenEngine animator) {
	    super(animator);
        destroy();
	}

    /**
     * Reset the tween/timeline to it's initial state. It will be as if the tween/timeline has never run before. If it was already
     * initialized, it will *not* redo the initialization.
     * <p>
     * The paused state is preserved.
     */
    @Override
    protected
    void reset() {
        super.reset();

        currentIndex = 0;
        current = childrenArray[0];

        for (int i = 0, n = childrenArray.length; i < n; i++) {
            final BaseTween<?> tween = childrenArray[i];
            // this can be a tween or a timeline.
            tween.reset();
        }
    }

	@Override
	protected
    void destroy() {
		super.destroy();

		children.clear();
        childrenArray = null;
		current = parent = null;
        currentIndex = 0;
	}

    /**
     * doesn't sync on anything.
     */
    void setup__(final Mode mode) {
		this.mode = mode;
		this.current = this;
	}

	// -------------------------------------------------------------------------
	// Common Public API
	// -------------------------------------------------------------------------

    /**
     * Adds a callback. By default, it will be fired at the completion of the timeline (event COMPLETE). If you want to change
     * this behavior use the {@link TweenCallback#TweenCallback(int)} constructor.
     *
     * Thread/Concurrent safe
     *
     * @see TweenCallback
     */
    @Override
    public final
    Timeline addCallback(final TweenCallback callback) {
        super.addCallback(callback);
        return this;
    }

    /**
     * Clears all of the callback.
     *
     * Thread/Concurrent safe
     */
    @Override
    public final
    Timeline clearCallbacks() {
        super.clearCallbacks();
        return this;
    }

    /**
     * Stops and resets the timeline, and sends it to its pool, for later reuse.
     * <p>
     * If started normally (instead of un-managed), the {@link TweenEngine} will automatically call this method once the animation is complete.
     */
    @Override
    public final
    void free() {
        // free all children tweens as well.
        BaseTween<?> tween;
        for (int i = children.size() - 1; i >= 0; i--) {
            tween = children.remove(i);

            if (tween.isAutoRemoveEnabled) {
                // only release to the pool if auto-remove is enabled (since that is the contract with tweens)
                tween.free();
            }
        }

        animator.free(this);
    }

    /**
     * Adds a start delay to the timeline in seconds.
     *
     * @param delay A duration in seconds for the delay
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline delay(final float delay) {
        super.delay(delay);
        return this;
    }

    /**
     * Repeats the timeline for a given number of times.
     *
     * @param count The number of repetitions. For infinite repetition, use {@link Tween#INFINITY} or -1.
     * @param delay A delay between each iteration, in seconds.
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline repeat(final int count, final float delay) {
        super.repeat(count, delay);
        return this;
    }

    /**
     * Repeats the timeline for a given number of times.
     * </p>
     * Once an iteration is complete, it will be played in reverse.
     *
     * @param count The number of repetitions. For infinite repetition, use {@link Tween#INFINITY} or -1.
     * @param delay A delay before each repetition, in seconds.
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline repeatAutoReverse(final int count, final float delay) {
        super.repeatAutoReverse(count, delay);
        return this;
    }

    /**
     * Sets the "start" callback, which is called when the timeline starts running, NULL to remove.
     *
     * @param startCallback this is the object that will be notified when the timeline starts running. NULL to unset.
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline setStartCallback(final UpdateAction<Timeline> startCallback) {
        super.setStartCallback(startCallback);
        return this;
    }

    /**
     * Sets the "end" callback, which is called when the timeline finishes running, NULL to remove.
     *
     * @param endCallback this is the object that will be notified when the timeline finishes running. NULL to unset.
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline setEndCallback(final UpdateAction<Timeline> endCallback) {
        super.setEndCallback(endCallback);
        return this;
    }

    /**
     * Sets the timeline to a specific point in time based on it's duration + delays. Callbacks are not notified and the change is
     * immediate. The timeline will continue in it's original direction
     * For example:
     * <ul>
     * <li> setProgress(0F, true) : set it to the starting position just after the start delay in the forward direction</li>
     * <li> setProgress(.5F, true) : set it to the middle position in the forward direction</li>
     * <li> setProgress(.5F, false) : set it to the middle position in the reverse direction</li>
     * <li> setProgress(1F, false) : set it to the end position in the reverse direction</li>
     * </ul>
     * <p>
     * Caveat: If the timeline is set to end in reverse, and it CANNOT go in reverse, then it will end up in the finished state
     * (end position). If the timeline is in repeat mode then it will end up in the same position if it was going forwards.
     *
     * @param percentage the percentage (of it's duration) from 0-1, that the timeline be set to
     */
    @Override
    public final
    Timeline setProgress(final float percentage) {
        super.setProgress(percentage);
        return this;
    }

    /**
     * Sets the timeline to a specific point in time based on it's duration + delays. Callbacks are not notified and the change is
     * immediate.
     * For example:
     * <ul>
     *     <li> setProgress(0F, true) : set it to the starting position just after the start delay in the forward direction</li>
     *     <li> setProgress(.5F, true) : set it to the middle position in the forward direction</li>
     *     <li> setProgress(.5F, false) : set it to the middle position in the reverse direction</li>
     *     <li> setProgress(1F, false) : set it to the end position in the reverse direction</li>
     * </ul>
     * <p>
     * Caveat: If the timeline is set to end in reverse, and it CANNOT go in reverse, then it will end up in the finished state
     *          (end position). If the timeline/tween is in repeat mode then it will end up in the same position if it was going forwards.
     *
     * @param percentage the percentage (of it's duration) from 0-1, that the timeline be set to
     * @param direction sets the direction of the timeline when it updates next: forwards (true) or reverse (false).
     */
    @Override
    public final
    Timeline setProgress(final float percentage, final boolean direction) {
        super.setProgress(percentage, direction);
        return this;
    }

    /**
     * Starts or restarts the timeline unmanaged. You will need to take care of its life-cycle.
     *
     * @return The current timeline
     */
    @Override
    public
    Timeline startUnmanaged() {
        animator.flushRead();

        startUnmanaged__();

        animator.flushWrite();

        return this;
    }

    @Override
    protected
    void startUnmanaged__() {
        super.startUnmanaged__();

        for (int i = 0; i < childrenSize; i++) {
            final BaseTween<?> obj = childrenArray[i];

            if (obj.repeatCountOrig < 0) {
                throw new RuntimeException("You can't push an object with infinite repetitions in a timeline");
            }

            obj.startUnmanaged__();
        }
    }

    /**
     * Convenience method to add an object to a timeline where it's life-cycle will be automatically handled .
     *
     * @return The current timeline
     */
    @Override
    public final
    Timeline start() {
        super.start();
        return this;
    }

    // -------------------------------------------------------------------------
    // User Data
    // -------------------------------------------------------------------------

    /**
     * Attaches an object to this timeline. It can be useful in order
     * to retrieve some data from a TweenCallback.
     *
     * @param data Any kind of object.
     *
     * @return The current timeline
     */
    @Override
    public
    Timeline setUserData(final Object data) {
        super.setUserData(data);
        return this;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

	/**
	 * Adds a Tween to the current timeline.
	 *
	 * @return The current timeline
	 */
	public
    Timeline push(final Tween<?> tween) {
        tween.startUnmanaged(); // calls flushRead()

        children.add(tween);

        setupTimeline__(tween);

        animator.flushWrite();
        return this;
	}

	/**
	 * Nests a Timeline in the current one.
	 *
	 * @return The current timeline
	 */
	public
    Timeline push(final Timeline timeline) {
        animator.flushRead();

        timeline.parent = this;
        children.add(timeline);

        setupTimeline__(timeline);

        animator.flushWrite();
        return this;
    }

	/**
	 * Adds a pause to the timeline. The pause may be negative if you want to overlap the preceding and following children.
	 *
	 * @param time A positive or negative duration in seconds
     *
	 * @return The current timeline
	 */
	public
    Timeline pushPause(final float time) {
        if (time < 0.0F) {
            throw new RuntimeException("You can't push a negative pause to a timeline. Just make the last entry's duration shorter or use" +
                                       " with a parallel timeline and appropriate delays in place.");
        }

        final Tween<?> tween = animator.mark__();
        animator.flushRead();

        tween.delay__(time);
        tween.startUnmanaged__();

        children.add(tween);

        setupTimeline__(tween);

        animator.flushWrite();
        return this;
	}

	/**
	 * Starts a nested timeline with a 'sequential' behavior. Don't forget to call {@link Timeline#end()} to close this nested timeline.
	 *
	 * @return The new sequential timeline
	 */
    public
    Timeline beginSequential() {
        Timeline timeline = animator.takeTimeline(); // calls flushRead()

        children.add(timeline);

        timeline.parent = this;
        timeline.mode = Mode.SEQUENTIAL;

        // keep track of which timeline we are on
        current = timeline;

        // flushWrite(); // called on end

        // our timeline info is setup when the sequenced timeline is "ended", so we can retrieve it's children
        return timeline;
	}

	/**
	 * Starts a nested timeline with a 'parallel' behavior. Don't forget to call {@link Timeline#end()} to close this nested timeline.
	 *
	 * @return The new parallel timeline
	 */
    public
    Timeline beginParallel() {
        Timeline timeline = animator.takeTimeline();
        animator.flushRead();

        children.add(timeline);

        timeline.parent = this;
        timeline.mode = Mode.PARALLEL;

        // keep track of which timeline we are on
        current = timeline;

        // flushWrite(); called on end()

        // our timeline info is setup when the sequenced timeline is "ended", so we can retrieve it's children
        return timeline;
	}

	/**
	 * Closes the last nested timeline.
	 *
	 * @return The original (parent) timeline
	 */
    public
    Timeline end() {
        if (current == this) {
            throw new RuntimeException("Nothing to end, calling end before begin!");
        }

        // flushRead();  called on begin...()

        // now prep everything (from the parent perspective), since we are now considered "done"
        parent.setupTimeline__(this);

        current = parent;

        if (current == null) {
            throw new RuntimeException("Whoops! Shouldn't be null!");
        }

        animator.flushWrite();
        return (Timeline) current;
	}

    /**
     * doesn't sync on anything.
     * <p>
     * Creates/prepares array for children. This array is used for iteration during update
     */
    private
    void setupTimeline__(BaseTween<?> tweenOrTimeline) {
        switch (mode) {
            case SEQUENTIAL:
                duration += tweenOrTimeline.getFullDuration__();
                break;

            case PARALLEL:
                duration = Math.max(duration, tweenOrTimeline.getFullDuration__());
                break;
        }

        childrenSize = children.size();
        childrenSizeMinusOne = childrenSize - 1;

        // setup our children array, so update iterations are faster
        childrenArray = new BaseTween[childrenSize];
        children.toArray(childrenArray);

        if (childrenSize > 0) {
            current = childrenArray[0];
        } else {
            throw new RuntimeException("Creating a timeline with zero children. This is likely unintended, and is not permitted.");
        }
    }

	/**
	 * Gets an unmodifiable list of the timeline children.
	 */
	public
    List<BaseTween<?>> getChildren() {
        return Collections.unmodifiableList(children);
    }

	// -------------------------------------------------------------------------
	// Overrides
	// -------------------------------------------------------------------------






    /**
     * Recursively adjust the tweens for when repeat + auto-reverse is used
     *
     * @param newDirection the new direction for all children
     */
    @Override
    protected
    void adjustForRepeat_AutoReverse(final boolean newDirection) {
        super.adjustForRepeat_AutoReverse(newDirection);

        for (int i = 0, n = childrenArray.length; i < n; i++) {
            final BaseTween<?> tween = childrenArray[i];
            tween.adjustForRepeat_AutoReverse(newDirection);
        }
    }

    /**
     * Adjust the current time (set to the start value for the tween) and change state to DELAY.
     * </p>
     * For timelines, this also changes what the current tween is (for when iterating over tweens)
     *
     * @param newDirection the new direction for all children
     */
    @Override
    protected
    void adjustForRepeat_Linear(final boolean newDirection) {
        super.adjustForRepeat_Linear(newDirection);

        for (int i = 0, n = childrenArray.length; i < n; i++) {
            final BaseTween<?> tween = childrenArray[i];
            tween.adjustForRepeat_Linear(newDirection);
        }

        // this only matters if we are a sequence, because PARALLEL operates on all of them at the same time
        if (mode == Mode.SEQUENTIAL) {
            if (newDirection) {
                currentIndex = 0;
            }
            else {
                currentIndex = childrenSize-1;
            }

            current = childrenArray[currentIndex];
        }
    }

    /**
     * Updates a timeline's children, in different orders.
     *
     * @param updateDirection what is the current direction of the update. This is used to determine what order to update the
     *                        timeline children (tweens)
     * @param delta the time in SECONDS that has elapsed since the last update
     */
    @Override
    protected
    void update(final boolean updateDirection, float delta) {
        if (mode == Mode.SEQUENTIAL) {
            // update children one at a time.

            if (updateDirection) {
                while (delta != 0.0F) {
                    delta = current.update__(delta);

                    if (current.state == FINISHED) {
                        // iterate to the next one when it's finished, but don't go beyond the last child
                        if (currentIndex < childrenSizeMinusOne) {
                            currentIndex++;
                            current = childrenArray[currentIndex];
                        } else if (this.parent != null) {
                            // keep track of implicit time "overflow", where currentTime + delta > duration.
                            // This logic is so that this is recorded only on the outermost timeline for when timelines reverse direction
                            return;
                        }
                    }
                }
            }
            else {
                while (delta != 0.0F) {
                    delta = current.update__(delta);

                    if (current.state == FINISHED) {
                        // iterate to the previous one (because we are in reverse) when it's finished, but don't go beyond the first child
                        if (currentIndex > 0) {
                            currentIndex--;
                            current = childrenArray[currentIndex];
                        } else if (this.parent != null) {
                            // keep track of implicit time "overflow", where currentTime + delta > duration.
                            // This logic is so that this is recorded only on the outermost timeline for when timelines reverse direction
                            return;
                        }
                    }
                }
            }
        }

        // update all children at once
        else {
            if (updateDirection) {
                for (int i = 0, n = childrenArray.length; i < n; i++) {
                    final BaseTween<?> tween = childrenArray[i];
                    final float returned = tween.update__(delta);

                    if (tween.state == FINISHED) {
                        // each child has to track "overflow" info to set delay's correctly when the timeline reverses
                        tween.currentTime += returned;
                    }
                }
            }
            else {
                for (int i = childrenArray.length - 1; i >= 0; i--) {
                    final BaseTween<?> tween = childrenArray[i];
                    final float returned = tween.update__(delta);

                    if (tween.state == FINISHED) {
                        // each child has to track "overflow" info to set delay's correctly when the timeline reverses
                        tween.currentTime += returned;
                    }
                }
            }
        }
    }

    /**
     * Forces a timeline/tween to have it's start/target values
     *
     * @param updateDirection direction in which the force is happening. Affects children iteration order (timelines) and start/target
     *                        values (tweens)
     * @param updateValue this is the start (true) or target (false) to set the tween to.
     */
    @Override
    protected
    void setValues(final boolean updateDirection, final boolean updateValue) {
        if (updateDirection) {
            for (int i = 0, n = childrenArray.length; i < n; i++) {
                final BaseTween<?> tween = childrenArray[i];
                tween.setValues(true, updateValue);
            }
        }
        else {
            for (int i = childrenArray.length - 1; i >= 0; i--) {
                final BaseTween<?> tween = childrenArray[i];
                tween.setValues(false, updateValue);
            }
        }
    }

	@Override
	protected final
    boolean containsTarget(final Object target) {
        for (int i = 0, n = childrenArray.length; i < n; i++) {
            final BaseTween<?> tween = childrenArray[i];
            if (tween.containsTarget(target)) {
                return true;
            }
        }
        return false;
	}

	@Override
    protected final
    boolean containsTarget(final Object target, final int tweenType) {
        for (int i = 0, n = childrenArray.length; i < n; i++) {
            final BaseTween<?> tween = childrenArray[i];
            if (tween.containsTarget(target, tweenType)) {
                return true;
            }
        }
        return false;
    }
}

