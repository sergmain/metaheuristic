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

package ai.metaheuristic.ai.some;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 8:34 PM
 */
public class TestLinkedList {

/*
    @Test
    public void testLinkedList_1() {

        LinkedList<Long> ids = new LinkedList<>(List.of(1L, 2L, 3L, 4L, 5L));
        Consumer<Long> s = ids::remove;
        for (Long id : ids) {
            System.out.println("id = " + id);
            if (id==3) {
//                assertThrows(ConcurrentModificationException.class, ()->s.accept(id));
                s.accept(id);
            }
        }
        System.out.println("ids = " + ids);
    }

    @Test
    public void testLinkedList_2() {

        LinkedList<Long> ids = new LinkedList<>(List.of(1L, 2L, 3L, 4L, 5L));
        for (Long id : ids) {
            System.out.println("id = " + id);
            if (id==3) {
                ids.remove(id);
            }
        }
        System.out.println("ids = " + ids);
    }
*/
}
