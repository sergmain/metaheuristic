/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.monitoring;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Rejection counts per key, over a sliding window of one-minute buckets.
 *
 * <p>Buckets rather than a running total because the question is "what is happening NOW". A total
 * since startup cannot distinguish a reason that fired a thousand times last Tuesday from one firing
 * continuously this minute, and it is the second that means something is broken.
 *
 * <p>❗ The key set is CAPPED. Keys include a function code, which is attacker- and accident-
 * controlled: an ExecContext generating unique codes would otherwise grow this map without bound in a
 * component nobody is watching. Past the cap, counts go to {@code other} — the level stays truthful
 * even though the breakdown stops being complete, which is the right way round.
 *
 * <p>Not thread-safe on its own; the owner synchronises.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class RejectionCounters {

    public static final long BUCKET_MILLIS = 60_000L;

    /** Where counts go once {@link #maxKeys} distinct keys exist. */
    public static final String OTHER_KEY = "other";

    private final int buckets;
    private final int maxKeys;

    /** index -> (key -> count). A bucket's start minute, so a stale bucket can be recognised and cleared. */
    private final Map<String, LongAdder>[] slots;
    private final long[] slotMinute;

    @SuppressWarnings("unchecked")
    public RejectionCounters(int buckets, int maxKeys) {
        if (buckets < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("01.325.020 buckets and maxKeys must be at least 1");
        }
        this.buckets = buckets;
        this.maxKeys = maxKeys;
        this.slots = new Map[buckets];
        this.slotMinute = new long[buckets];
        for (int i = 0; i < buckets; i++) {
            slots[i] = new HashMap<>();
            slotMinute[i] = Long.MIN_VALUE;
        }
    }

    public void increment(String key, long nowMillis) {
        final long minute = nowMillis / BUCKET_MILLIS;
        final int idx = (int) Math.floorMod(minute, (long) buckets);
        final Map<String, LongAdder> slot = slots[idx];

        // reusing this slot for a new minute: whatever it held belonged to a minute a full window ago
        if (slotMinute[idx] != minute) {
            slot.clear();
            slotMinute[idx] = minute;
        }
        final LongAdder existing = slot.get(key);
        if (existing != null) {
            existing.increment();
            return;
        }
        if (slot.size() >= maxKeys) {
            slot.computeIfAbsent(OTHER_KEY, k -> new LongAdder()).increment();
            return;
        }
        slot.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /** Total for a key across every bucket still inside the window ending at {@code nowMillis}. */
    public long windowSum(String key, long nowMillis) {
        final long newestMinute = nowMillis / BUCKET_MILLIS;
        final long oldestMinute = newestMinute - buckets + 1;
        long sum = 0;
        for (int i = 0; i < buckets; i++) {
            if (slotMinute[i] >= oldestMinute && slotMinute[i] <= newestMinute) {
                final LongAdder adder = slots[i].get(key);
                if (adder != null) {
                    sum += adder.sum();
                }
            }
        }
        return sum;
    }

    /** How many of the buckets in the window have a non-zero count for this key. */
    public int bucketsPresent(String key, long nowMillis) {
        final long newestMinute = nowMillis / BUCKET_MILLIS;
        final long oldestMinute = newestMinute - buckets + 1;
        int present = 0;
        for (int i = 0; i < buckets; i++) {
            if (slotMinute[i] >= oldestMinute && slotMinute[i] <= newestMinute) {
                final LongAdder adder = slots[i].get(key);
                if (adder != null && adder.sum() > 0) {
                    present++;
                }
            }
        }
        return present;
    }

    /** Every key with a non-zero count in the window, and its total. */
    public Map<String, Long> windowTotals(long nowMillis) {
        final long newestMinute = nowMillis / BUCKET_MILLIS;
        final long oldestMinute = newestMinute - buckets + 1;
        final Map<String, Long> out = new HashMap<>();
        for (int i = 0; i < buckets; i++) {
            if (slotMinute[i] < oldestMinute || slotMinute[i] > newestMinute) {
                continue;
            }
            for (Map.Entry<String, LongAdder> e : slots[i].entrySet()) {
                out.merge(e.getKey(), e.getValue().sum(), Long::sum);
            }
        }
        return out;
    }

    public int bucketCount() {
        return buckets;
    }
}
