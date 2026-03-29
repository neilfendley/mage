package mage.player.ai;

import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight performance counters for profiling MCTS self-play.
 * All times in nanoseconds. Thread-safe via atomics.
 */
public class PerfStats {

    private static final Logger logger = Logger.getLogger(PerfStats.class);

    public static final AtomicLong validateStateNs = new AtomicLong();
    public static final AtomicLong validateStateCount = new AtomicLong();
    public static final AtomicLong evaluateNs = new AtomicLong();
    public static final AtomicLong evaluateCount = new AtomicLong();
    public static final AtomicLong expandNs = new AtomicLong();
    public static final AtomicLong expandCount = new AtomicLong();
    public static final AtomicLong stateEncodeNs = new AtomicLong();
    public static final AtomicLong stateEncodeCount = new AtomicLong();
    public static final AtomicLong gameTimeNs = new AtomicLong();
    public static final AtomicLong gameCount = new AtomicLong();

    public static void reset() {
        validateStateNs.set(0); validateStateCount.set(0);
        evaluateNs.set(0); evaluateCount.set(0);
        expandNs.set(0); expandCount.set(0);
        stateEncodeNs.set(0); stateEncodeCount.set(0);
        gameTimeNs.set(0); gameCount.set(0);
    }

    public static void printSummary() {
        long games = gameCount.get();
        if (games == 0) {
            logger.info("=== PERF: no games recorded ===");
            return;
        }
        long totalGameMs = gameTimeNs.get() / 1_000_000;
        long validateMs = validateStateNs.get() / 1_000_000;
        long evaluateMs = evaluateNs.get() / 1_000_000;
        long expandMs = expandNs.get() / 1_000_000;
        long encodeMs = stateEncodeNs.get() / 1_000_000;
        long accountedMs = validateMs + evaluateMs + expandMs + encodeMs;

        logger.info("=== PERFORMANCE SUMMARY ===");
        logger.info(String.format("Games: %d, Total game time: %.1fs, Avg: %.1fs/game",
                games, totalGameMs / 1000.0, totalGameMs / 1000.0 / games));
        logger.info(String.format("  validateState:  %6dms (%4.1f%%) — %d calls, %.1fms/call",
                validateMs, pct(validateMs, totalGameMs), validateStateCount.get(), avg(validateMs, validateStateCount.get())));
        logger.info(String.format("  evaluate:       %6dms (%4.1f%%) — %d calls, %.1fms/call",
                evaluateMs, pct(evaluateMs, totalGameMs), evaluateCount.get(), avg(evaluateMs, evaluateCount.get())));
        logger.info(String.format("  expand:         %6dms (%4.1f%%) — %d calls, %.1fms/call",
                expandMs, pct(expandMs, totalGameMs), expandCount.get(), avg(expandMs, expandCount.get())));
        logger.info(String.format("  stateEncode:    %6dms (%4.1f%%) — %d calls, %.1fms/call",
                encodeMs, pct(encodeMs, totalGameMs), stateEncodeCount.get(), avg(encodeMs, stateEncodeCount.get())));
        logger.info(String.format("  other/overhead: %6dms (%4.1f%%)",
                totalGameMs - accountedMs, pct(totalGameMs - accountedMs, totalGameMs)));
    }

    private static double pct(long part, long total) {
        return total > 0 ? 100.0 * part / total : 0;
    }

    private static double avg(long totalMs, long count) {
        return count > 0 ? (double) totalMs / count : 0;
    }
}
