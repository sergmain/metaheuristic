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
public class ProcessorCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ProcessorCommParamsYamlV1, ProcessorCommParamsYaml, Void, Void, Void, Void> {

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
    public ProcessorCommParamsYaml upgradeTo(@NonNull ProcessorCommParamsYamlV1 src) {
        ProcessorCommParamsYaml trg = new ProcessorCommParamsYaml();

        for (ProcessorCommParamsYamlV1.ProcessorRequestV1 v1 : src.requests) {
            ProcessorCommParamsYaml.ProcessorRequest t = new ProcessorCommParamsYaml.ProcessorRequest(v1.processorCode);
            trg.requests.add(t);

            if (v1.processorCommContext !=null) {
                t.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext();
                BeanUtils.copyProperties(v1.processorCommContext, t.processorCommContext);
            }
            if (v1.requestProcessorId !=null) {
                t.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId(true);
            }
            if (v1.requestTask!=null) {
                t.requestTask = new ProcessorCommParamsYaml.RequestTask(v1.requestTask.newTask, v1.requestTask.acceptOnlySigned);
            }
            if (v1.reportTaskProcessingResult!=null) {
                t.reportTaskProcessingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
                t.reportTaskProcessingResult.results =
                        v1.reportTaskProcessingResult.results!=null
                                ? v1.reportTaskProcessingResult.results
                                .stream()
                                .map(o->new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(o.taskId, o.result))
                                .collect(Collectors.toList())
                                : new ArrayList<>();
            }
            if (v1.checkForMissingOutputResources!=null) {
                t.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources(true);
            }
            if (v1.resendTaskOutputResourceResult!=null) {
                t.resendTaskOutputResourceResult = new ProcessorCommParamsYaml.ResendTaskOutputResourceResult();
                t.resendTaskOutputResourceResult.statuses =
                        v1.resendTaskOutputResourceResult.statuses!=null
                                ? v1.resendTaskOutputResourceResult.statuses
                                .stream()
                                .map(o->new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(o.taskId, o.variableId, o.status))
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
/*
        ProcessorCommParamsYamlV3 t = new ProcessorCommParamsYamlV3();

        if (v1.processorCommContext!=null) {
            t.processorCommContext = new ProcessorCommParamsYamlV3.ProcessorCommContextV3();
            BeanUtils.copyProperties(v1.processorCommContext, t.processorCommContext);
        }
        if (v1.requestProcessorId!=null) {
            t.requestProcessorId = new ProcessorCommParamsYamlV3.RequestProcessorIdV3(true);
        }
        if (v1.reportProcessorStatus!=null) {
            t.reportProcessorStatus = new ProcessorCommParamsYamlV3.ReportProcessorStatusV3();
            BeanUtils.copyProperties(v1.reportProcessorStatus, t.reportProcessorStatus);
        }
        if (v1.reportProcessorTaskStatus!=null) {
            t.reportProcessorTaskStatus = new ProcessorCommParamsYamlV3.ReportProcessorTaskStatusV3();
            t.reportProcessorTaskStatus.statuses =
                    v1.reportProcessorTaskStatus.statuses!=null
                            ? v1.reportProcessorTaskStatus.statuses
                            .stream()
                            .map(o->new ProcessorCommParamsYamlV3.ReportProcessorTaskStatusV3.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestTask!=null) {
            t.requestTask = new ProcessorCommParamsYamlV3.RequestTaskV3();
            t.requestTask.acceptOnlySigned = v1.requestTask.acceptOnlySigned;
        }
        if (v1.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new ProcessorCommParamsYamlV3.ReportTaskProcessingResultV3();
            t.reportTaskProcessingResult.results =
                    v1.reportTaskProcessingResult.results!=null
                            ? v1.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new ProcessorCommParamsYamlV3.ReportTaskProcessingResultV3.SimpleTaskExecResult(o.taskId, o.result,
                                    o.ml!=null ? o.ml.metrics : null, null))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new ProcessorCommParamsYamlV3.CheckForMissingOutputResourcesV3(true);
        }
        if (v1.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new ProcessorCommParamsYamlV3.ResendTaskOutputResourceResultV3();
            t.resendTaskOutputResourceResult.statuses =
                    v1.resendTaskOutputResourceResult.statuses!=null
                            ? v1.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new ProcessorCommParamsYamlV3.ResendTaskOutputResourceResultV3.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v1, t);
        return t;
*/
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
