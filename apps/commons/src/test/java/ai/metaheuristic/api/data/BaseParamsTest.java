/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParams;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYaml;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYamlUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYaml;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYamlUtils;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYaml;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYamlUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 8/23/2022
 * Time: 8:10 PM
 */
public class BaseParamsTest {

    private static final List<Pair<BaseYamlUtils<? extends BaseParams>, Class>> cls = List.of(
            Pair.of(BatchItemMappingYamlUtils.BASE_YAML_UTILS, BatchItemMappingYaml.class),
            Pair.of(BatchItemMappingYamlUtils.BASE_YAML_UTILS, BatchItemMappingYaml.class),
            Pair.of(DispatcherEventYamlUtils.BASE_YAML_UTILS, DispatcherEventYaml.class),
            Pair.of(EnvParamsYamlUtils.BASE_YAML_UTILS, EnvParamsYaml.class),
            Pair.of(FittingYamlUtils.BASE_YAML_UTILS, FittingYaml.class),
            Pair.of(FunctionConfigListYamlUtils.BASE_YAML_UTILS, FunctionConfigListYaml.class),
            Pair.of(FunctionConfigYamlUtils.BASE_YAML_UTILS, FunctionConfigYaml.class),
            Pair.of(TaskFileParamsYamlUtils.BASE_YAML_UTILS, TaskFileParamsYaml.class),
            Pair.of(VariableArrayParamsYamlUtils.BASE_YAML_UTILS, VariableArrayParamsYaml.class)
    );

    @Test
    public void test_1() throws Exception {
        for (Pair<BaseYamlUtils<? extends BaseParams>, Class> cl : cls) {
            BaseYamlUtils<? extends BaseParams> utils = cl.getLeft();
            Class entityCl = cl.getRight();

            int lastVersion = utils.getDefault().getVersion();

            final Constructor constructor = getConstructor(entityCl);
            Object entity = constructor.newInstance();
            assertTrue( entity instanceof BaseParams);
            assertEquals(lastVersion, ((BaseParams) entity).getVersion(), entity.getClass().getName());

            for (int i = 1; i <= lastVersion; i++) {
                final AbstractParamsYamlUtils forVersion = utils.getForVersion(i);
                assertNotNull(forVersion);
                assertEquals(i, forVersion.getVersion());

                Class vClass = Class.forName(entityCl.getName()+'V'+i);
                final Constructor enConstructor = getConstructor(vClass);
                Object entityObj = enConstructor.newInstance();
                assertTrue( entityObj instanceof BaseParams);
                assertEquals(i, ((BaseParams) entityObj).getVersion(), entityObj.getClass().getName());
            }
        }
    }

    private static Constructor getConstructor(Class entityCl) {
        Constructor[] entityConstructors = entityCl.getConstructors();
        final Constructor constructor = Arrays.stream(entityConstructors).filter(o -> o.getParameterTypes().length == 0).findFirst().orElseThrow();
        return constructor;
    }
}
