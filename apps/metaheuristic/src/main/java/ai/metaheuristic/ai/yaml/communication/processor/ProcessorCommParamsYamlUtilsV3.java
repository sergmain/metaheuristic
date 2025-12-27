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

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 9:54 PM
 */
public class ProcessorCommParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<ProcessorCommParamsYamlV3, ProcessorCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ProcessorCommParamsYamlV3.class);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYaml upgradeTo(@NonNull ProcessorCommParamsYamlV3 src) {
        ProcessorCommParamsYaml trg = new ProcessorCommParamsYaml();

        ProcessorCommParamsYaml.ProcessorRequest t = trg.request;

        if (src.request.processorCommContext !=null) {
            t.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(src.request.processorCommContext.processorId, src.request.processorCommContext.sessionId);
        }
        if (src.request.requestProcessorId !=null) {
            t.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId(true);
        }
        if (src.request.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new ProcessorCommParamsYaml.ReportTaskProcessingResult();
            t.reportTaskProcessingResult.results =
                    src.request.reportTaskProcessingResult.results!=null
                            ? src.request.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(o.taskId, o.result))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (src.request.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources(true);
        }
        if (src.request.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new ProcessorCommParamsYaml.ResendTaskOutputResourceResult();
            t.resendTaskOutputResourceResult.statuses =
                    src.request.resendTaskOutputResourceResult.statuses!=null
                            ? src.request.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(o.taskId, o.variableId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        trg.request.currentQuota = src.request.currentQuota;

        for (ProcessorCommParamsYamlV3.CoreV3 coreV3 : src.request.cores) {
            ProcessorCommParamsYaml.Core core = new ProcessorCommParamsYaml.Core();
            core.code = coreV3.code;
            core.coreId = coreV3.coreId;
            if (coreV3.requestTask!=null) {
                core.requestTask = new ProcessorCommParamsYaml.RequestTask(coreV3.requestTask.acceptOnlySigned, coreV3.requestTask.taskIds);
            }
            trg.request.cores.add(core);
        }

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
    public String toString(@NonNull ProcessorCommParamsYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV3 to(@NonNull String s) {
        final ProcessorCommParamsYamlV3 p = getYaml().load(s);
        return p;
    }

}
