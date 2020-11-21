/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
    public DispatcherCommParamsYaml upgradeTo(@NonNull DispatcherCommParamsYamlV1 v1, Long ... vars) {
        DispatcherCommParamsYaml t = new DispatcherCommParamsYaml();

        if( v1.dispatcherCommContext !=null ) {
            t.dispatcherCommContext = new DispatcherCommParamsYaml.DispatcherCommContext();
            t.dispatcherCommContext.chunkSize = v1.dispatcherCommContext.chunkSize;
            t.dispatcherCommContext.processorCommVersion = v1.dispatcherCommContext.processorCommVersion;
        }
        if (v1.functions !=null) {
            t.functions = new DispatcherCommParamsYaml.Functions();
            t.functions.infos.addAll( v1.functions.infos
                            .stream()
                            .map(o->new DispatcherCommParamsYaml.Functions.Info (o.code, o.sourcing))
                            .collect(Collectors.toList())
                    );
        }
        if (v1.assignedTask!=null) {
            t.assignedTask = new DispatcherCommParamsYaml.AssignedTask();
            BeanUtils.copyProperties(v1.assignedTask, t.assignedTask);
        }
        if (v1.assignedProcessorId !=null) {
            t.assignedProcessorId = new DispatcherCommParamsYaml.AssignedProcessorId();
            BeanUtils.copyProperties(v1.assignedProcessorId, t.assignedProcessorId);
        }
        if (v1.reAssignedProcessorId !=null) {
            t.reAssignedProcessorId = new DispatcherCommParamsYaml.ReAssignProcessorId();
            BeanUtils.copyProperties(v1.reAssignedProcessorId, t.reAssignedProcessorId);
        }
        if (v1.reportResultDelivering!=null) {
            t.reportResultDelivering = new DispatcherCommParamsYaml.ReportResultDelivering();
            t.reportResultDelivering.ids =
                    v1.reportResultDelivering.ids!=null ? new ArrayList<>(v1.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (v1.resendTaskOutputs!=null) {
            t.resendTaskOutputs = new DispatcherCommParamsYaml.ResendTaskOutputs();
            v1.resendTaskOutputs.resends.stream().map(o -> new DispatcherCommParamsYaml.ResendTaskOutput(o.taskId, o.variableId)).collect(Collectors.toCollection(() -> t.resendTaskOutputs.resends));
        }
        if (v1.requestLogFile!=null) {
            t.requestLogFile = new DispatcherCommParamsYaml.RequestLogFile(v1.requestLogFile.requestedOn);
        }

        BeanUtils.copyProperties(v1, t);
        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
/*
        DispatcherCommParamsYamlV1 t = new DispatcherCommParamsYamlV1();

        if( yaml.dispatcherCommContext!=null ) {
            t.dispatcherCommContext = new DispatcherCommParamsYamlV1.DispatcherCommContextV1();
            t.dispatcherCommContext.chunkSize = yaml.dispatcherCommContext.chunkSize;
        }
        if (yaml.assignedTask!=null) {
            t.assignedTask = new DispatcherCommParamsYamlV1.AssignedTaskV1();
            BeanUtils.copyProperties(yaml.assignedTask, t.assignedTask);
        }
        if (yaml.assignedProcessorId!=null) {
            t.assignedProcessorId = new DispatcherCommParamsYamlV1.AssignedProcessorIdV1();
            BeanUtils.copyProperties(yaml.assignedProcessorId, t.assignedProcessorId);
        }
        if (yaml.reAssignedProcessorId!=null) {
            t.reAssignedProcessorId = new DispatcherCommParamsYamlV1.ReAssignProcessorIdV1();
            BeanUtils.copyProperties(yaml.reAssignedProcessorId, t.reAssignedProcessorId);
        }
        if (yaml.reportResultDelivering!=null) {
            t.reportResultDelivering = new DispatcherCommParamsYamlV1.ReportResultDeliveringV1();
            t.reportResultDelivering.ids =
                    yaml.reportResultDelivering.ids!=null ? new ArrayList<>(yaml.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (yaml.execContextStatus !=null) {
            t.execContextStatus = new DispatcherCommParamsYamlV1.ExecContextStatusV1();
            t.execContextStatus.statuses =
                    yaml.execContextStatus.statuses!=null
                            ? yaml.execContextStatus.statuses
                            .stream()
                            .map(o->new DispatcherCommParamsYamlV1.ExecContextStatusV1.SimpleStatus(o.execContextId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (yaml.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new DispatcherCommParamsYamlV1.ResendTaskOutputResourceV1();
            t.resendTaskOutputResource.taskIds =
                    yaml.resendTaskOutputResource.taskIds!=null ? new ArrayList<>(yaml.resendTaskOutputResource.taskIds) : new ArrayList<>();
        }

        BeanUtils.copyProperties(yaml, t);
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
    public String toString(@NonNull DispatcherCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherCommParamsYamlV1 to(@NonNull String s) {
        final DispatcherCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
