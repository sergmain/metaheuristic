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

package ai.metaheuristic.ai.yaml.communication.dispatcher;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class DispatcherCommParamsYamlUtilsV1 extends
        AbstractParamsYamlUtils<DispatcherCommParamsYamlV1, DispatcherCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherCommParamsYamlV1.class);
    }

    @NonNull
    @Override
    public DispatcherCommParamsYaml upgradeTo(@NonNull DispatcherCommParamsYamlV1 v1) {
        v1.checkIntegrity();

        DispatcherCommParamsYaml t = new DispatcherCommParamsYaml();

        for (DispatcherCommParamsYamlV1.DispatcherResponseV1 response : v1.responses) {
            DispatcherCommParamsYaml.DispatcherResponse r = new DispatcherCommParamsYaml.DispatcherResponse(response.processorCode);
            t.responses.add(r);

            if (response.assignedTask!=null) {
                r.assignedTask = to(response.assignedTask);
            }
            if (response.assignedProcessorId !=null) {
                r.assignedProcessorId = new DispatcherCommParamsYaml.AssignedProcessorId(response.assignedProcessorId.assignedProcessorId, response.assignedProcessorId.assignedSessionId);
            }
            if (response.reAssignedProcessorId !=null) {
                r.reAssignedProcessorId = new DispatcherCommParamsYaml.ReAssignProcessorId(
                        response.reAssignedProcessorId.reAssignedProcessorId, response.reAssignedProcessorId.sessionId);
            }
            if (response.reportResultDelivering!=null) {
                r.reportResultDelivering = new DispatcherCommParamsYaml.ReportResultDelivering();
                r.reportResultDelivering.ids =
                        response.reportResultDelivering.ids!=null ? new ArrayList<>(response.reportResultDelivering.ids) : new ArrayList<>();
            }
            if (response.resendTaskOutputs!=null) {
                r.resendTaskOutputs = new DispatcherCommParamsYaml.ResendTaskOutputs();
                response.resendTaskOutputs.resends.stream().map(o -> new DispatcherCommParamsYaml.ResendTaskOutput(o.taskId, o.variableId)).collect(Collectors.toCollection(() -> r.resendTaskOutputs.resends));
            }
        }
        if (v1.requestLogFile!=null) {
            t.requestLogFile = new DispatcherCommParamsYaml.RequestLogFile(v1.requestLogFile.requestedOn);
        }
        t.success = v1.success;
        t.msg = v1.msg;

        t.checkIntegrity();
        return t;
    }

    private static DispatcherCommParamsYaml.AssignedTask to(DispatcherCommParamsYamlV1.AssignedTaskV1 src) {
        DispatcherCommParamsYaml.AssignedTask trg = new DispatcherCommParamsYaml.AssignedTask();
        trg.taskId = src.taskId;
        trg.execContextId = src.execContextId;
        trg.params = src.params;
        trg.state = src.state;
        trg.tag = src.tag;
        trg.quota = src.quota;
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull DispatcherCommParamsYamlV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherCommParamsYamlV1 to(@NonNull String s) {
        final DispatcherCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
