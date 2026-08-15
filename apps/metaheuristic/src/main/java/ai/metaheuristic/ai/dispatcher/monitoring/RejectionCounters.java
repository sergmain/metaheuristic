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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
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
 * <p>Thread-safe by construction, and lock-free on the increment path — see {@link #increment}.
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
    private final ConcurrentHashMap<String, LongAdder>[] slots;
    private final AtomicLongArray slotMinute;

    @SuppressWarnings("unchecked")
    public RejectionCounters(int buckets, int maxKeys) {
        if (buckets < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("01.325.020 buckets and maxKeys must be at least 1");
        }
        this.buckets = buckets;
        this.maxKeys = maxKeys;
        this.slots = new ConcurrentHashMap[buckets];
        this.slotMinute = new AtomicLongArray(buckets);
        for (int i = 0; i < buckets; i++) {
            slots[i] = new ConcurrentHashMap<>();
            slotMinute.set(i, Long.MIN_VALUE);
        }
    }

    /**
     * ❗ Lock-free on the common path, and that is the point of the class. This runs once per rejection
     * evaluation — on the order of 10^4/s — so a lock here would serialise the hottest loop in the
     * dispatcher behind the instrumentation measuring it. An established key costs one array read, one
     * volatile read and a {@code LongAdder} increment, which is exactly what {@code LongAdder}'s striped
     * cells exist for; nothing is allocated.
     *
     * <p>⚠️ Two threads crossing a minute boundary together can lose or double a count at the boundary.
     * Accepted deliberately: this answers "is this reason at a level", and a rate estimate does not
     * change because one increment of ten thousand landed in the neighbouring bucket. Paying a lock on
     * every call to remove that would be the wrong trade by three orders of magnitude.
     */
    public void increment(String key, long nowMillis) {
        final long minute = nowMillis / BUCKET_MILLIS;
        final int idx = (int) Math.floorMod(minute, (long) buckets);

        // reusing this slot for a new minute: whatever it held belonged to a minute a full window ago.
        // One thread wins the CAS and clears; the others simply carry on into the cleared slot.
        final long stamped = slotMinute.get(idx);
        if (stamped != minute && slotMinute.compareAndSet(idx, stamped, minute)) {
            slots[idx].clear();
        }
        final ConcurrentHashMap<String, LongAdder> slot = slots[idx];

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
            final long stamped = slotMinute.get(i);
            if (stamped >= oldestMinute && stamped <= newestMinute) {
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
            final long stamped = slotMinute.get(i);
            if (stamped >= oldestMinute && stamped <= newestMinute) {
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
            final long stamped = slotMinute.get(i);
            if (stamped < oldestMinute || stamped > newestMinute) {
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
