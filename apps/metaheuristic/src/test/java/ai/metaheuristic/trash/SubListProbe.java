/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.trash;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 3/8/2026
 * Time: 7:16 PM
 */
public class SubListProbe {
    static void main() {
        var l = List.of("1", "2", "3", "4", "5", "6", "7", "8");

        System.out.println(l.subList(0, l.size()-1));
    }
}
