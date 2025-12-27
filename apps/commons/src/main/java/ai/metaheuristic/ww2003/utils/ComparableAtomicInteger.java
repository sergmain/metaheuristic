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

package ai.metaheuristic.ww2003.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergio Lissner
 * Date: 9/3/2022
 * Time: 4:11 PM
 */
public class ComparableAtomicInteger extends AtomicInteger implements Comparable<ComparableAtomicInteger> {
    public ComparableAtomicInteger(int initialValue) {
        super(initialValue);
    }

    public ComparableAtomicInteger() {
    }

    @Override
    public int compareTo(ComparableAtomicInteger o) {
        return Integer.compare(this.get(), o.get());
    }
}
