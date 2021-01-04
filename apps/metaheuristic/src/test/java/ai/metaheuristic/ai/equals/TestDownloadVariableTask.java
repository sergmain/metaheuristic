/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.equals;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 11/26/2020
 * Time: 4:42 PM
 */
public class TestDownloadVariableTask {

    @Test
    public void test() {

        ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(
                "proc-code-1", "proc-id-1", new ProcessorAndCoreData.DispatcherUrl("url"));
        DownloadVariableTask o1 = new DownloadVariableTask(ref, "111", EnumsApi.VariableContext.local, 42L, new File("."), new DispatcherLookupParamsYaml.DispatcherLookup(), false);
        DownloadVariableTask o2 = new DownloadVariableTask(ref, "111", EnumsApi.VariableContext.local, 42L, new File("."), new DispatcherLookupParamsYaml.DispatcherLookup(), true);

        assertEquals(o1, o2);
    }
}
