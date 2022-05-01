/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
public class ProcessorCommParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ProcessorCommParamsYamlV2, ProcessorCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ProcessorCommParamsYamlV2.class);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYaml upgradeTo(@NonNull ProcessorCommParamsYamlV2 src) {
        ProcessorCommParamsYaml trg = new ProcessorCommParamsYaml();

        for (ProcessorCommParamsYamlV2.ProcessorRequestV2 v2 : src.requests) {
            ProcessorCommParamsYaml.ProcessorRequest t = new ProcessorCommParamsYaml.ProcessorRequest(v2.processorCode);
            trg.requests.add(t);

            if (v2.processorCommContext !=null) {
                t.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext();
                BeanUtils.copyProperties(v2.processorCommContext, t.processorCommContext);
            }
            if (v2.requestProcessorId !=null) {
                t.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId(true);
            }
            if (v2.requestTask!=null) {
                t.requestTask = new ProcessorCommParamsYaml.RequestTask(v2.requestTask.newTask, v2.requestTask.acceptOnlySigned, v2.requestTask.taskIds);
            }
            if (v2.reportTaskProcessingResult!=null) {
                t.reportTaskProcessingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
                t.reportTaskProcessingResult.results =
                        v2.reportTaskProcessingResult.results!=null
                                ? v2.reportTaskProcessingResult.results
                                .stream()
                                .map(o->new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(o.taskId, o.result))
                                .collect(Collectors.toList())
                                : new ArrayList<>();
            }
            if (v2.checkForMissingOutputResources!=null) {
                t.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources(true);
            }
            if (v2.resendTaskOutputResourceResult!=null) {
                t.resendTaskOutputResourceResult = new ProcessorCommParamsYaml.ResendTaskOutputResourceResult();
                t.resendTaskOutputResourceResult.statuses =
                        v2.resendTaskOutputResourceResult.statuses!=null
                                ? v2.resendTaskOutputResourceResult.statuses
                                .stream()
                                .map(o->new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(o.taskId, o.variableId, o.status))
                                .collect(Collectors.toList())
                                : new ArrayList<>();
            }
            t.processorCode = v2.processorCode;
        }
        trg.quotas.current = src.quotas.current;
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void v2) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ProcessorCommParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV2 to(@NonNull String s) {
        final ProcessorCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
