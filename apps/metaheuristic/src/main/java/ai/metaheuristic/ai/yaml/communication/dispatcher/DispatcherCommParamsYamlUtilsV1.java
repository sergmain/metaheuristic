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

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherCommParamsYamlV1.class);
    }

    @Override
    public DispatcherCommParamsYaml upgradeTo(DispatcherCommParamsYamlV1 v1, Long ... vars) {
        DispatcherCommParamsYaml t = new DispatcherCommParamsYaml();

        if( v1.dispatcherCommContext !=null ) {
            t.dispatcherCommContext = new DispatcherCommParamsYaml.DispatcherCommContext();
            t.dispatcherCommContext.chunkSize = v1.dispatcherCommContext.chunkSize;
            t.dispatcherCommContext.stationCommVersion = v1.dispatcherCommContext.stationCommVersion;
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
        if (v1.assignedStationId!=null) {
            t.assignedStationId = new DispatcherCommParamsYaml.AssignedStationId();
            BeanUtils.copyProperties(v1.assignedStationId, t.assignedStationId);
        }
        if (v1.reAssignedStationId!=null) {
            t.reAssignedStationId = new DispatcherCommParamsYaml.ReAssignStationId();
            BeanUtils.copyProperties(v1.reAssignedStationId, t.reAssignedStationId);
        }
        if (v1.reportResultDelivering!=null) {
            t.reportResultDelivering = new DispatcherCommParamsYaml.ReportResultDelivering();
            t.reportResultDelivering.ids =
                    v1.reportResultDelivering.ids!=null ? new ArrayList<>(v1.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (v1.execContextStatus !=null) {
            t.execContextStatus = new DispatcherCommParamsYaml.ExecContextStatus();
            t.execContextStatus.statuses =
                    v1.execContextStatus.statuses!=null
                            ? v1.execContextStatus.statuses
                            .stream()
                            .map(o->new DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus(o.execContextId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new DispatcherCommParamsYaml.ResendTaskOutputResource();
            t.resendTaskOutputResource.taskIds =
                    v1.resendTaskOutputResource.taskIds!=null ? new ArrayList<>(v1.resendTaskOutputResource.taskIds) : new ArrayList<>();
        }

        BeanUtils.copyProperties(v1, t);
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
        if (yaml.assignedStationId!=null) {
            t.assignedStationId = new DispatcherCommParamsYamlV1.AssignedStationIdV1();
            BeanUtils.copyProperties(yaml.assignedStationId, t.assignedStationId);
        }
        if (yaml.reAssignedStationId!=null) {
            t.reAssignedStationId = new DispatcherCommParamsYamlV1.ReAssignStationIdV1();
            BeanUtils.copyProperties(yaml.reAssignedStationId, t.reAssignedStationId);
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
    public String toString(DispatcherCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public DispatcherCommParamsYamlV1 to(String s) {
        final DispatcherCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
