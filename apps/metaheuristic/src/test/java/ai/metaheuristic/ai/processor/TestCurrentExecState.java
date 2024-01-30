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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 3/6/2022
 * Time: 11:28 PM
 */
public class TestCurrentExecState {

    @Test
    public void test() {
        ProcessorAndCoreData.DispatcherUrl url = new ProcessorAndCoreData.DispatcherUrl("aaa");
        Long execContextId = 2L;
        CurrentExecState es = new CurrentExecState();
        assertTrue(es.isState(url, execContextId, EnumsApi.ExecContextState.UNKNOWN));

        assertFalse(es.isState(url, execContextId, EnumsApi.ExecContextState.DOESNT_EXIST));
        assertTrue(es.isState(url, execContextId, EnumsApi.ExecContextState.DOESNT_EXIST, EnumsApi.ExecContextState.UNKNOWN));
        assertFalse(es.isState(url, execContextId, EnumsApi.ExecContextState.STARTED, EnumsApi.ExecContextState.STOPPED));
    }

}
