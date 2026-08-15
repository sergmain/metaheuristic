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

import java.util.ArrayList;
import java.util.List;

/**
 * The last N examples of one rejection reason, in a fixed-size buffer that overwrites in place.
 *
 * <p>A count tells an admin that something is wrong; an exemplar tells them WHAT. The 2026-07-24
 * incident is the case: a level on {@code interpreter_is_undefined} says a Processor is refusing work,
 * but the offending {@code env} string is what ends the investigation.
 *
 * <p>❗ Entries hold COPIED SCALARS only — never a reference to a {@code TaskParamsYaml}, a Task, or
 * console output. This is diagnostic furniture that lives for the process's lifetime: holding a
 * reference would pin the whole object graph behind it, and the offending value is the only part
 * anybody reads.
 *
 * <p>Not thread-safe on its own; the owner synchronises.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class ExemplarRing {

    /** How much of an offending value is kept. Enough to identify it, bounded because it is unbounded. */
    public static final int MAX_VALUE_LEN = 200;

    public record Exemplar(long atMills, @Nullable Long taskId, @Nullable String functionCode,
                           @Nullable Long processorId, @Nullable String offendingValue) {}

    private final Exemplar[] buffer;
    private int next = 0;
    private int size = 0;

    public ExemplarRing(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("01.324.020 capacity must be at least 1, was " + capacity);
        }
        this.buffer = new Exemplar[capacity];
    }

    public void add(long atMills, @Nullable Long taskId, @Nullable String functionCode,
                    @Nullable Long processorId, @Nullable String offendingValue) {
        buffer[next] = new Exemplar(atMills, taskId, functionCode, processorId, truncate(offendingValue));
        next = (next + 1) % buffer.length;
        if (size < buffer.length) {
            size++;
        }
    }

    /** Newest first, so a reader sees what is happening now without scrolling. */
    public List<Exemplar> newestFirst() {
        final List<Exemplar> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int idx = Math.floorMod(next - 1 - i, buffer.length);
            out.add(buffer[idx]);
        }
        return out;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return buffer.length;
    }

    @Nullable
    private static String truncate(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_VALUE_LEN ? value : value.substring(0, MAX_VALUE_LEN) + "...";
    }
}
