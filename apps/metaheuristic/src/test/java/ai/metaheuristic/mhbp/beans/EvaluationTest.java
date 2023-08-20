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

package ai.metaheuristic.mhbp.beans;

import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Sergio Lissner
 * Date: 4/16/2023
 * Time: 11:48 PM
 */
public class EvaluationTest {

    @Test
    public void test_KbIdsConverter() {
        Evaluation.ChapterIdsConverter converter = new Evaluation.ChapterIdsConverter();

        List<String> list = converter.convertToEntityAttribute("11");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("11", list.get(0));


        list = converter.convertToEntityAttribute("11,42");
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("11", list.get(0));
        assertEquals("42", list.get(1));
    }
}
