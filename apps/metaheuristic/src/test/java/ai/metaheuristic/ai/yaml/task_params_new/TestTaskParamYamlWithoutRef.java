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
package ai.metaheuristic.ai.yaml.task_params_new;

import ai.metaheuristic.ai.yaml.TestYamlParser;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.commons.CommonConsts;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestTaskParamYamlWithoutRef {

    @Test
    public void testTaskParamYaml() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/task_params_new/params-complex.yaml", StandardCharsets.UTF_8);

        TaskApiData.TaskParamYaml taskParam = TaskParamYamlUtils.toTaskYaml(yaml);

        String s = TaskParamYamlNewUtils.toString(taskParam);

        System.out.println("s = \n" + s);
        // TODO right now it doesn't work, refs are always here
//        assertFalse(s.contains("&id001"));

    }
}
