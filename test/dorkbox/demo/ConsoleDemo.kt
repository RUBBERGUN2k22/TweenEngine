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
 */
package dorkbox.demo

import dorkbox.tweenEngine.*
import java.util.*

class ConsoleDemo(delta: Int, delay: Int, isAutoReverse: Boolean, reverseCount: Int) {
    init {
        var delta: Int = delta
        val terminalwidth = 50
        val bugs: Array<Bugtest>

        bugs = arrayOf(
                Bugtest('a'),
                Bugtest('b'))

        val timeline: Timeline = ConsoleDemo.tweenEngine.createSequential() // callback text is ABOVE the line that it applies to
                .addCallback(ConsoleDemo.buildCallback<Timeline>("TL", TweenCallback.Events.ANY))
                .delay(delay.toFloat())
                .push(bugs[0].t)
                .beginParallel()
                .push(bugs[1].t) ////                                    .beginSequence()
                ////                                        .push(bugs[2].t) // third tween not even needed
                ////                                        .end()
                .end() //                                    .push(bugs[2].t)

        if (isAutoReverse) {
            timeline.repeatAutoReverse(reverseCount, 500f)
        } else {
            timeline.repeat(reverseCount, 500f)
        }
        timeline.start()


        val permitFlip = false
        var flipped = false
        do {
            if (permitFlip && !flipped && timeline.getCurrentTime() > 0.5f) {
                flipped = true
                delta = -delta
            }
            drawConsole(timeline, terminalwidth, bugs)
            timeline.update(delta.toFloat())
            try {
                Thread.sleep(30)
            } catch (ignored: Throwable) {
            }
        } while (!timeline.isFinished)
    }

    internal class A : TweenAccessor<Bugtest> {
        override fun getValues(b: Bugtest, m: Int, `val`: FloatArray): Int {
            `val`[0] = b.`val`
            return 1
        }

        override fun setValues(b: Bugtest, m: Int, `val`: FloatArray) {
            b.`val` = `val`[0]
        }
    }

    internal class Bugtest(var name: Char) {
        var `val` = 0f // tweened
        var t: Tween<Bugtest>

        init {
            t = tweenEngine.to<Bugtest>(this, 0, 1000f)
                    .target(1f)
                    .addCallback(buildCallback("" + name, TweenCallback.Events.ANY))
        }
    }

    companion object {

        private val tweenEngine = TweenEngine.create()
                .unsafe()
                .setWaypointsLimit(10)
                .setCombinedAttributesLimit(3)
                .registerAccessor(Bugtest::class.java, A())
                .build()

        @JvmStatic
        fun main(args: Array<String>) {
            // Tests
            RunConsole()
        }

        private fun <T : BaseTween<T>> buildCallback(name: String, triggers: Int): TweenCallback<T> {
            return object : TweenCallback<T>(triggers) {
                override fun onEvent(type: Int, source: T) {
                    val t: String
                    t = if (type == Events.BEGIN) {
                        "BEGIN        "
                    } else if (type == Events.START) {
                        "START        "
                    } else if (type == Events.END) {
                        "END          "
                    } else if (type == Events.COMPLETE) {
                        "COMPLETE     "
                    } else if (type == Events.BACK_BEGIN) {
                        "BACK_BEGIN   "
                    } else if (type == Events.BACK_START) {
                        "BACK_START   "
                    } else if (type == Events.BACK_END) {
                        "BACK_END     "
                    } else if (type == Events.BACK_COMPLETE) {
                        "BACK_COMPLETE"
                    } else {
                        "???"
                    }
                    val str = String.format(Locale.US, "%s %s   lt %3f", name, t, source!!.getCurrentTime())
                    println(str)
                }
            }
        }

        private fun RunConsole() {
            val delta = 50
            //        int delta = 51;

//        ConsoleDemo(delta, 250, false, 0);
//        ConsoleDemo(delta, 250, false, 1);
            ConsoleDemo(delta, 250, false, 2)
            //        ConsoleDemo(delta, 250, false, Tween.INFINITY);
//
//        ConsoleDemo(delta, 250, true, 1);
//        ConsoleDemo(delta, 250, true, 2);
//        ConsoleDemo(delta, 250, true, 4);
//        ConsoleDemo(delta, 250, true, Tween.INFINITY);
        }

        private fun drawConsole(timeline: Timeline, terminalWidth: Int, bugs: Array<Bugtest>) {
            val prog = CharArray(terminalWidth + 1)

            //just for drawing
            for (i in 0..terminalWidth) {
                prog[i] = '-'
            }
            for (i in bugs.indices) {
                val bug = bugs[i]
                val i1 = (bug.`val` * terminalWidth).toInt()
                prog[i1] = bug.name
            }

            print(prog)

            print(String.format(Locale.US, "\t%s:%.1f %s",
                    if (timeline.getDirection()) "F" else "R",
                    timeline.getCurrentTime(),
                    if (timeline.isFinished) "don" else "run"))
            for (i in bugs.indices) {
                val bug = bugs[i]

                print("\t\t" + String.format(Locale.US, "%s: %.1f %s",
                        if (bug.t.getDirection()) "F" else "R",
                        bug.t.getCurrentTime(),
                        if (timeline.isFinished) "don" else "run"))
            }
            println()
        }
    }
}
