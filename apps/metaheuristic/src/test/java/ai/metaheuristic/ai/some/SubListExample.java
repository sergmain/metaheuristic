/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.utils.CollectionUtils;

import java.util.List;

/**
 * @author Serge
 * Date: 12/15/2020
 * Time: 7:03 PM
 */
public class SubListExample {

    public static final List<String> list = List.of("1", "2", "3", "4", "5", "6", "7");
    private static final int PAGE = 6;

    public static void main(String[] args) {
        List<List<String>> pages = CollectionUtils.parseAsPages(list, PAGE);
        for (List<String> page : pages) {
            System.out.println(page);
        }
    }
}
