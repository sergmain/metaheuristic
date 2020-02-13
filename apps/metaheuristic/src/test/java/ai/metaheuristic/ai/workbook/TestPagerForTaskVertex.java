/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.workbook;

import ai.metaheuristic.ai.launchpad.exec_context.ExecContextService;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import org.junit.Test;

import java.util.List;

import static ai.metaheuristic.api.EnumsApi.TaskExecState.NONE;
import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/25/2019
 * Time: 8:42 PM
 */
public class TestPagerForTaskVertex {

    @Test
    public void test() {
        List<WorkbookParamsYaml.TaskVertex> vertices = List.of(
                new WorkbookParamsYaml.TaskVertex(1L, NONE),
                new WorkbookParamsYaml.TaskVertex(2L, NONE),
                new WorkbookParamsYaml.TaskVertex(3L, NONE),
                new WorkbookParamsYaml.TaskVertex(4L, NONE),
                new WorkbookParamsYaml.TaskVertex(5L, NONE),
                new WorkbookParamsYaml.TaskVertex(6L, NONE),
                new WorkbookParamsYaml.TaskVertex(7L, NONE),
                new WorkbookParamsYaml.TaskVertex(8L, NONE),
                new WorkbookParamsYaml.TaskVertex(9L, NONE),
                new WorkbookParamsYaml.TaskVertex(10L, NONE)
        );

        List<Long> ids = ExecContextService.getIdsForSearch(vertices, 0, 3);
        assertNotNull(ids);
        assertEquals(3, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
        assertTrue(ids.contains(3L));

        ids = ExecContextService.getIdsForSearch(vertices, 1, 3);
        assertNotNull(ids);
        assertEquals(3, ids.size());
        assertTrue(ids.contains(4L));
        assertTrue(ids.contains(5L));
        assertTrue(ids.contains(6L));

        ids = ExecContextService.getIdsForSearch(vertices, 2, 3);
        assertNotNull(ids);
        assertEquals(3, ids.size());
        assertTrue(ids.contains(7L));
        assertTrue(ids.contains(8L));
        assertTrue(ids.contains(9L));

        ids = ExecContextService.getIdsForSearch(vertices, 3, 3);
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertTrue(ids.contains(10L));

    }
}
