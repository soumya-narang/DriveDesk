package util;

import java.util.HashMap;
import java.util.Map;

/** Hands out ids like I001, D001, B001, X001. static: counters are shared by the whole program. */
public class IdGenerator {
    private static final Map<String, Integer> counters = new HashMap<>();

    private IdGenerator() {}

    public static synchronized String next(String prefix) {
        int n = counters.merge(prefix, 1, Integer::sum);
        return String.format("%s%03d", prefix, n);
    }

    public static String nextItemId()         { return next("I"); }
    public static String nextDonorId()        { return next("D"); }
    public static String nextBeneficiaryId()  { return next("B"); }
    public static String nextDistributionId() { return next("X"); }

    /** Current counter for a prefix, so a failed operation can hand its ids back with {@link #reset}. */
    public static synchronized int mark(String prefix) {
        return counters.getOrDefault(prefix, 0);
    }

    /** Rolls a counter back after a rejected request, so ids stay gap-free (I013, I014, I015, ...). */
    public static synchronized void reset(String prefix, int mark) {
        counters.put(prefix, mark);
    }

    /** Called while loading from disk so new ids continue after the highest existing one. */
    public static synchronized void observe(String id) {
        if (id == null || id.length() < 2) return;
        try {
            int n = Integer.parseInt(id.substring(1));
            counters.merge(id.substring(0, 1), n, Math::max);
        } catch (NumberFormatException ignored) {
            // not one of our generated ids
        }
    }
}
