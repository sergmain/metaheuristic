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

package ai.metaheuristic.ai.some;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 6/9/2022
 * Time: 5:27 AM
 */
public class StreamTest {

    @AllArgsConstructor
    @Data
    public static class Aaa {
        public String s;
        public int i;
    }

    private static final String ALPHA_NUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Random RANDOM = new Random();

    public static String randomAlphanumeric(final int count) {
        final StringBuilder strBuilder = new StringBuilder(count);
        final int anLen = ALPHA_NUMERIC.length();
        for (int i = 0; i < count; i++) {
            strBuilder.append(ALPHA_NUMERIC.charAt(RANDOM.nextInt(anLen)));
        }
        return strBuilder.toString();
    }

    private static List<Aaa> createList(int count, boolean isArray) {
        List<Aaa> list = isArray ? new ArrayList<>(count) : new LinkedList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Aaa(randomAlphanumeric(10), RANDOM.nextInt(100)));
        }
        return Collections.unmodifiableList(list);
    }

    @Test
    public void test() {

        List<Aaa> aaas = createList(1000, true);

        List<Aaa> nums;
        nums = aaas.stream().sorted(Comparator.comparingInt(o -> o.i)).toList();
        nums = aaas.stream().sorted(Comparator.comparingInt(o -> o.i)).collect(Collectors.toList());

        long mills;
        long endMills;
        mills = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            nums = aaas.parallelStream().sorted(Comparator.comparing(o -> o.s)).toList();
        }
        endMills = System.currentTimeMillis();
        System.out.println("Stream.toList(): " + (endMills-mills));

        mills = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            nums = aaas.parallelStream().sorted(Comparator.comparingInt(o -> o.i)).collect(Collectors.toList());
        }
        endMills = System.currentTimeMillis();
        System.out.println("Stream.collect(Collectors.toList()): " + (endMills-mills));

        mills = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            nums = aaas.parallelStream().sorted(Comparator.comparing(o -> o.s)).toList();
        }
        endMills = System.currentTimeMillis();
        System.out.println("Stream.toList(): " + (endMills-mills));

        mills = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            nums = aaas.parallelStream().sorted(Comparator.comparingInt(o -> o.i)).collect(Collectors.toList());
        }
        endMills = System.currentTimeMillis();
        System.out.println("Stream.collect(Collectors.toList()): " + (endMills-mills));

    }

    @BenchmarkMode(Mode.All)
    @Fork(1)
    @State(Scope.Thread)
    @Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS, batchSize = 1000)
    @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS, batchSize = 1000)
    public static class CollectorsVsStreamToList {

        public static List<Aaa> aaaArray = createList(1000, true);
        public static List<Aaa> aaaLinked = createList(1000, false);

        @Benchmark
        public List<Aaa> viaCollectorWithSortArray() {
            return aaaArray.stream().sorted(Comparator.comparing(o -> o.s)).collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaCollectorWithSortLinked() {
            return aaaLinked.stream().sorted(Comparator.comparing(o -> o.s)).collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaStreamWithSortArray() {
            return aaaArray.stream().sorted(Comparator.comparing(o -> o.s)).toList();
        }

        @Benchmark
        public List<Aaa> viaStreamWithSortLinked() {
            return aaaLinked.stream().sorted(Comparator.comparing(o -> o.s)).toList();
        }

        ///

        @Benchmark
        public List<Aaa> viaCollectorsParallelWithSortArray() {
            return aaaArray.parallelStream().sorted(Comparator.comparing(o -> o.s)).collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaCollectorsParallelWithSortLinked() {
            return aaaLinked.parallelStream().sorted(Comparator.comparing(o -> o.s)).collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaStreamParallelWithSortArray() {
            return aaaLinked.parallelStream().sorted(Comparator.comparing(o -> o.s)).toList();
        }

        @Benchmark
        public List<Aaa> viaStreamParallelWithSortLinked() {
            return aaaLinked.parallelStream().sorted(Comparator.comparing(o -> o.s)).toList();
        }

        ////

        @Benchmark
        public List<Aaa> viaCollectorArray() {
            return aaaArray.stream().collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaCollectorLinked() {
            return aaaLinked.stream().collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaStreamArray() {
            return aaaArray.stream().toList();
        }

        @Benchmark
        public List<Aaa> viaStreamLinked() {
            return aaaLinked.stream().toList();
        }

        ////

        @Benchmark
        public List<Aaa> viaCollectorsParallelArray() {
            return aaaArray.parallelStream().collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaCollectorsParallelLinked() {
            return aaaLinked.parallelStream().collect(Collectors.toList());
        }

        @Benchmark
        public List<Aaa> viaStreamParallelArray() {
            return aaaArray.parallelStream().toList();
        }

        @Benchmark
        public List<Aaa> viaStreamParallelLinked() {
            return aaaLinked.parallelStream().toList();
        }

        public static void main(String[] args) throws Exception {
            org.openjdk.jmh.Main.main(args);
        }
    }
}
