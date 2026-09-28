package io.github.sjzeero.eecircuits.board;

import java.util.concurrent.atomic.LongAdder;

/** Cheap timing counters for board work, read by /voltworks boardtest perf. */
public final class BoardStats {
    public static final LongAdder MAIN_NS = new LongAdder();
    public static final LongAdder SIM_NS = new LongAdder();
    public static final LongAdder BOARD_TICKS = new LongAdder();
    public static volatile long startTick = -1;

    private BoardStats() {
    }

    public static void reset(long gameTime) {
        MAIN_NS.reset();
        SIM_NS.reset();
        BOARD_TICKS.reset();
        startTick = gameTime;
    }
}
