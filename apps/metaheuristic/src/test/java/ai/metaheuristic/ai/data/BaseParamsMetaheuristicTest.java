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

package ai.metaheuristic.ai.data;

import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultTaskParamsYamlUtils;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYamlUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYamlUtils;
import ai.metaheuristic.ai.yaml.series.SeriesParamsYaml;
import ai.metaheuristic.ai.yaml.series.SeriesParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParams;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 8/23/2022
 * Time: 9:15 PM
 */
public class BaseParamsMetaheuristicTest {

    private static final List<Pair<BaseYamlUtils<? extends BaseParams>, Class>> cls = List.of(
            Pair.of(BatchParamsYamlUtils.BASE_YAML_UTILS, BatchParamsYaml.class),
            Pair.of(CompanyParamsYamlUtils.BASE_YAML_UTILS, CompanyParamsYaml.class),
            Pair.of(CoreStatusYamlUtils.BASE_YAML_UTILS, CoreStatusYaml.class),
            Pair.of(DispatcherCommParamsYamlUtils.BASE_YAML_UTILS, DispatcherCommParamsYaml.class),
            Pair.of(DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS, DispatcherLookupParamsYaml.class),
            Pair.of(DispatcherParamsYamlUtils.BASE_YAML_UTILS, DispatcherParamsYaml.class),
            Pair.of(ExecContextGraphParamsYamlUtils.BASE_YAML_UTILS, ExecContextGraphParamsYaml.class),
            Pair.of(ExecContextParamsYamlUtils.BASE_YAML_UTILS, ExecContextParamsYaml.class),
            Pair.of(ExecContextTaskStateParamsYamlUtils.BASE_YAML_UTILS, ExecContextTaskStateParamsYaml.class),
            Pair.of(ExperimentParamsYamlUtils.BASE_YAML_UTILS, ExperimentParamsYaml.class),
            Pair.of(ExperimentResultTaskParamsYamlUtils.BASE_YAML_UTILS, ExperimentResultTaskParams.class),
            Pair.of(FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS, FunctionDownloadStatusYaml.class),
            Pair.of(KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS, KeepAliveRequestParamYaml.class),
            Pair.of(MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS, MetadataAggregateFunctionParamsYaml.class),
            Pair.of(MetadataParamsYamlUtils.BASE_YAML_UTILS, MetadataParamsYaml.class),
            Pair.of(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS, ProcessorCommParamsYaml.class),
            Pair.of(ProcessorStatusYamlUtils.BASE_YAML_UTILS, ProcessorStatusYaml.class),
            Pair.of(ReduceVariablesConfigParamsYamlUtils.BASE_YAML_UTILS, ReduceVariablesConfigParamsYaml.class),
            Pair.of(SeriesParamsYamlUtils.BASE_YAML_UTILS, SeriesParamsYaml.class),
            Pair.of(SourceCodeParamsYamlUtils.BASE_YAML_UTILS, SourceCodeParamsYaml.class),
            Pair.of(SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS, SourceCodeStoredParamsYaml.class)
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
