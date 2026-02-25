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

package ai.metaheuristic.commons.yaml.task_file;

import ai.metaheuristic.commons.utils.MetaUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
class TaskFileParamsYamlMetasTest {

    @Test
    public void test_metas_v2_roundTrip() {
        // arrange
        TaskFileParamsYamlV2 v2 = new TaskFileParamsYamlV2();
        v2.task.metas.add(Map.of("processing-mode", "cc-call"));
        v2.task.metas.add(Map.of("output-format", "json"));

        // act
        String yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(v2);
        TaskFileParamsYaml result = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        // assert
        assertEquals(2, result.task.metas.size());
        assertEquals("cc-call", MetaUtils.getValue(result.task.metas, "processing-mode"));
        assertEquals("json", MetaUtils.getValue(result.task.metas, "output-format"));
    }

    @Test
    public void test_metas_currentVersion_roundTrip() {
        // arrange
        TaskFileParamsYaml params = new TaskFileParamsYaml();
        params.task.execContextId = 1L;
        params.task.metas.add(Map.of("key1", "value1"));
        params.task.metas.add(Map.of("key2", "value2", "key3", "value3"));

        // act
        String yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(params);
        TaskFileParamsYaml result = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        // assert
        assertEquals(2, result.task.metas.size());
        assertEquals("value1", MetaUtils.getValue(result.task.metas, "key1"));
        assertEquals("value2", MetaUtils.getValue(result.task.metas, "key2"));
        assertEquals("value3", MetaUtils.getValue(result.task.metas, "key3"));
    }

    @Test
    public void test_metas_v1UpgradeProducesEmptyMetas() {
        // arrange
        TaskFileParamsYamlV1 v1 = new TaskFileParamsYamlV1();
        v1.task.execContextId = 1L;

        // act
        String yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(v1);
        TaskFileParamsYaml result = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        // assert
        assertNotNull(result.task.metas);
        assertTrue(result.task.metas.isEmpty());
    }

    @Test
    public void test_metas_downgradeToV1_dropsMetasWithoutError() {
        // arrange
        TaskFileParamsYaml params = new TaskFileParamsYaml();
        params.task.execContextId = 1L;
        params.task.metas.add(Map.of("processing-mode", "cc-call"));

        // act
        String v1Yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(params, 1);
        TaskFileParamsYaml restored = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(v1Yaml);

        // assert - metas are lost after downgrade to V1 and back
        assertTrue(v1Yaml.contains("version: 1"));
        assertNotNull(restored.task.metas);
        assertTrue(restored.task.metas.isEmpty());
    }
}
