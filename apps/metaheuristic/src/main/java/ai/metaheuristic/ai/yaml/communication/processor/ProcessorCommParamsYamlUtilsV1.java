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

package ai.metaheuristic.ai.yaml.communication.processor;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class ProcessorCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ProcessorCommParamsYamlV1, ProcessorCommParamsYamlV2, ProcessorCommParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ProcessorCommParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV2 upgradeTo(@NonNull ProcessorCommParamsYamlV1 src) {
        ProcessorCommParamsYamlV2 trg = new ProcessorCommParamsYamlV2();

        for (ProcessorCommParamsYamlV1.ProcessorRequestV1 v1 : src.requests) {
            ProcessorCommParamsYamlV2.ProcessorRequestV2 t = new ProcessorCommParamsYamlV2.ProcessorRequestV2(v1.processorCode);
            trg.requests.add(t);

            if (v1.processorCommContext !=null) {
                t.processorCommContext = new ProcessorCommParamsYamlV2.ProcessorCommContextV2();
                BeanUtils.copyProperties(v1.processorCommContext, t.processorCommContext);
            }
            if (v1.requestProcessorId !=null) {
                t.requestProcessorId = new ProcessorCommParamsYamlV2.RequestProcessorIdV2(true);
            }
            if (v1.requestTask!=null) {
                t.requestTask = new ProcessorCommParamsYamlV2.RequestTaskV2(v1.requestTask.newTask, v1.requestTask.acceptOnlySigned, null);
            }
            if (v1.reportTaskProcessingResult!=null) {
                t.reportTaskProcessingResult = new ProcessorCommParamsYamlV2.ReportTaskProcessingResultV2();
                t.reportTaskProcessingResult.results =
                        v1.reportTaskProcessingResult.results!=null
                                ? v1.reportTaskProcessingResult.results
                                .stream()
                                .map(o->new ProcessorCommParamsYamlV2.ReportTaskProcessingResultV2.SimpleTaskExecResult(o.taskId, o.result))
                                .collect(Collectors.toList())
                                : new ArrayList<>();
            }
            if (v1.checkForMissingOutputResources!=null) {
                t.checkForMissingOutputResources = new ProcessorCommParamsYamlV2.CheckForMissingOutputResourcesV2(true);
            }
            if (v1.resendTaskOutputResourceResult!=null) {
                t.resendTaskOutputResourceResult = new ProcessorCommParamsYamlV2.ResendTaskOutputResourceResultV2();
                t.resendTaskOutputResourceResult.statuses =
                        v1.resendTaskOutputResourceResult.statuses!=null
                                ? v1.resendTaskOutputResourceResult.statuses
                                .stream()
                                .map(o->new ProcessorCommParamsYamlV2.ResendTaskOutputResourceResultV2.SimpleStatus(o.taskId, o.variableId, o.status))
                                .collect(Collectors.toList())
                                : new ArrayList<>();
            }
            t.processorCode = v1.processorCode;
        }
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void v1) {
        return null;
    }

    @Override
    public ProcessorCommParamsYamlUtilsV2 nextUtil() {
        return (ProcessorCommParamsYamlUtilsV2) ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ProcessorCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV1 to(@NonNull String s) {
        final ProcessorCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
