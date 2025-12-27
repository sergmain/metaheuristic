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

package ai.metaheuristic.ai.yaml.communication.dispatcher;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class DispatcherCommParamsYamlUtilsV2 extends
        AbstractParamsYamlUtils<DispatcherCommParamsYamlV2, DispatcherCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherCommParamsYamlV2.class);
    }

    @Override
    public DispatcherCommParamsYaml upgradeTo(DispatcherCommParamsYamlV2 v2) {
        v2.checkIntegrity();

        DispatcherCommParamsYaml t = new DispatcherCommParamsYaml();
        DispatcherCommParamsYamlV2.DispatcherResponseV2 response = v2.response;

        v2.response.cores.stream().map(DispatcherCommParamsYamlUtilsV2::toCore).collect(Collectors.toCollection(()->t.response.cores));

        DispatcherCommParamsYaml.DispatcherResponse r = t.response;
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

        if (v2.requestLogFile!=null) {
            t.requestLogFile = new DispatcherCommParamsYaml.RequestLogFile(v2.requestLogFile.requestedOn);
        }
        t.success = v2.success;
        t.msg = v2.msg;

        t.checkIntegrity();
        return t;
    }

    private static DispatcherCommParamsYaml.AssignedTask toAssignedTask(DispatcherCommParamsYamlV2.AssignedTaskV2 src) {
        DispatcherCommParamsYaml.AssignedTask trg = new DispatcherCommParamsYaml.AssignedTask();
        trg.taskId = src.taskId;
        trg.execContextId = src.execContextId;
        trg.params = src.params;
        trg.state = src.state;
        trg.tag = src.tag;
        trg.quota = src.quota;
        return trg;
    }

    private static DispatcherCommParamsYaml.Core toCore(DispatcherCommParamsYamlV2.CoreV2 src) {
        DispatcherCommParamsYaml.Core trg = new DispatcherCommParamsYaml.Core();
        trg.code = src.code;
        trg.coreId = src.coreId;
        if (src.assignedTask!=null) {
            trg.assignedTask = toAssignedTask(src.assignedTask);
        }
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(DispatcherCommParamsYamlV2 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public DispatcherCommParamsYamlV2 to(String s) {
        final DispatcherCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
