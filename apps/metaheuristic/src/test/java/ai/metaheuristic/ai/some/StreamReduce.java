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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 9/24/2023
 * Time: 1:44 PM
 */
public class StreamReduce {

    public static final int INITIAL_CAPACITY = 1_000;

    public static void main(String[] args) {

        int sizeH1 = 10;
        sizeH1 -= 20;
        System.out.println(sizeH1);

        List<Integer> l = new ArrayList<>(INITIAL_CAPACITY);
        for (int i = 1; i <= INITIAL_CAPACITY; i++) {
            l.add(i);
        }
        int i1;
        i1 = l.stream().reduce(0, Integer::sum);
        i1 = l.stream().reduce(0, Integer::sum);

        int i2;
        i2 = l.stream().mapToInt(Integer::intValue).sum();
        i2 = l.stream().mapToInt(Integer::intValue).sum();

        int i3;
        int i4;

        long mills = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            i1 = l.stream().reduce(0, Integer::sum);
        }
        System.out.println(System.currentTimeMillis()-mills);

        mills = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            i2 = l.stream().mapToInt(Integer::intValue).sum();
        }
        System.out.println(System.currentTimeMillis()-mills);

        mills = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            i3 = sum(l);
        }
        System.out.println(System.currentTimeMillis()-mills);

        mills = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            i4 = sum1(l);
        }
        System.out.println(System.currentTimeMillis()-mills);

        int i5;
        mills = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            i5 = l.parallelStream().mapToInt(Integer::intValue).sum();
        }
        System.out.println(System.currentTimeMillis()-mills);

    }

    private static int sum(List<Integer> l) {
        int sum = 0;
        for (int i = 0; i <l.size(); i++) {
            sum += l.get(i);
        }
        return sum;
    }

    private static int sum1(List<Integer> l) {
        int sum = 0;
        for (Integer integer : l) {
            sum += integer;
        }
        return sum;
    }
}
