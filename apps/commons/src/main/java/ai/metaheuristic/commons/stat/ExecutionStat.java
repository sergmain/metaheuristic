/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons.stat;

import ai.metaheuristic.commons.S;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.io.PrintStream;
import java.util.*;
import java.util.function.*;

/**
 * @author Serge
 * Date: 9/27/2019
 * Time: 12:00 PM
 */
@Data
public class ExecutionStat {

    public boolean isStat = false;

    public void print(PrintStream out) {
        TreeMap<String, StatElement> map = new TreeMap<>(execStat);
        map.forEach((k, v) -> out.println(asString(k, v)));
    }

    public List<String> print() {
        List<String> result = new ArrayList<>(execStat.size()+10);
        TreeMap<String, StatElement> map = new TreeMap<>(execStat);
        map.forEach((k, v) -> result.add(asString(k, v)));
        return result;
    }

    private static String asString(String k, StatElement v) {
        return S.f("%-80s: called %10d times, total time %10d, avg time %10.2f", k, v.count, v.time, ((double) v.time) / v.count);
    }

    @Data
    public static class StatElement {
        public int count;
        public long time;

        public void add(long time) {
            this.count++;
            this.time += time;
        }

        public void add(StatElement value) {
            this.count += value.count;
            this.time += value.time;
        }
    }

    public LinkedHashMap<String, StatElement> execStat = new LinkedHashMap<>();

    public void exec(String key, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public <T> T get(String key, Supplier<T> action) {
        long start = System.currentTimeMillis();
        try {
            return action.get();
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    @Nullable
    public <T> T getNullable(String key, Supplier<T> action) {
        long start = System.currentTimeMillis();
        try {
            return action.get();
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public <T> void accept(String key, T t, Consumer<T> action) {
        long start = System.currentTimeMillis();
        try {
            action.accept(t);
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public <T, U> void accept(String key, T t, U u, BiConsumer<T, U> action) {
        long start = System.currentTimeMillis();
        try {
            action.accept(t, u);
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public <T, R> R apply(String key, T t, Function<T, R> action) {
        long start = System.currentTimeMillis();
        try {
            return action.apply(t);
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public <T, U, R> R apply(String key, T t, U u, BiFunction<T, U, R> action) {
        long start = System.currentTimeMillis();
        try {
            return action.apply(t, u);
        } finally {
            if (isStat) {
                execStat.computeIfAbsent(key, o -> new StatElement()).add(System.currentTimeMillis() - start);
            }
        }
    }

    public void merge(ExecutionStat stat) {
        execStat.putAll(stat.execStat);
    }

    public void mergeAndClear(ExecutionStat stat) {
        for (Map.Entry<String, StatElement> entry : stat.execStat.entrySet()) {
            execStat.computeIfAbsent(entry.getKey(), o -> new StatElement()).add(entry.getValue());
        }
        stat.execStat.clear();
    }

}
