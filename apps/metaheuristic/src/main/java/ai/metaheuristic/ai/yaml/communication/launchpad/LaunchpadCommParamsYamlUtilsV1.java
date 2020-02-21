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

package ai.metaheuristic.ai.yaml.communication.launchpad;

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
public class LaunchpadCommParamsYamlUtilsV1 extends
        AbstractParamsYamlUtils<LaunchpadCommParamsYamlV1, LaunchpadCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(LaunchpadCommParamsYamlV1.class);
    }

    @Override
    public LaunchpadCommParamsYaml upgradeTo(LaunchpadCommParamsYamlV1 v1, Long ... vars) {
        LaunchpadCommParamsYaml t = new LaunchpadCommParamsYaml();

        if( v1.launchpadCommContext!=null ) {
            t.launchpadCommContext = new LaunchpadCommParamsYaml.LaunchpadCommContext();
            t.launchpadCommContext.chunkSize = v1.launchpadCommContext.chunkSize;
            t.launchpadCommContext.stationCommVersion = v1.launchpadCommContext.stationCommVersion;
        }
        if (v1.functions !=null) {
            t.functions = new LaunchpadCommParamsYaml.Functions();
            t.functions.infos.addAll( v1.functions.infos
                            .stream()
                            .map(o->new LaunchpadCommParamsYaml.Functions.Info (o.code, o.sourcing))
                            .collect(Collectors.toList())
                    );
        }
        if (v1.assignedTask!=null) {
            t.assignedTask = new LaunchpadCommParamsYaml.AssignedTask();
            BeanUtils.copyProperties(v1.assignedTask, t.assignedTask);
        }
        if (v1.assignedStationId!=null) {
            t.assignedStationId = new LaunchpadCommParamsYaml.AssignedStationId();
            BeanUtils.copyProperties(v1.assignedStationId, t.assignedStationId);
        }
        if (v1.reAssignedStationId!=null) {
            t.reAssignedStationId = new LaunchpadCommParamsYaml.ReAssignStationId();
            BeanUtils.copyProperties(v1.reAssignedStationId, t.reAssignedStationId);
        }
        if (v1.reportResultDelivering!=null) {
            t.reportResultDelivering = new LaunchpadCommParamsYaml.ReportResultDelivering();
            t.reportResultDelivering.ids =
                    v1.reportResultDelivering.ids!=null ? new ArrayList<>(v1.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (v1.execContextStatus !=null) {
            t.execContextStatus = new LaunchpadCommParamsYaml.ExecContextStatus();
            t.execContextStatus.statuses =
                    v1.execContextStatus.statuses!=null
                            ? v1.execContextStatus.statuses
                            .stream()
                            .map(o->new LaunchpadCommParamsYaml.ExecContextStatus.SimpleStatus(o.execContextId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new LaunchpadCommParamsYaml.ResendTaskOutputResource();
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
        LaunchpadCommParamsYamlV1 t = new LaunchpadCommParamsYamlV1();

        if( yaml.launchpadCommContext!=null ) {
            t.launchpadCommContext = new LaunchpadCommParamsYamlV1.LaunchpadCommContextV1();
            t.launchpadCommContext.chunkSize = yaml.launchpadCommContext.chunkSize;
        }
        if (yaml.assignedTask!=null) {
            t.assignedTask = new LaunchpadCommParamsYamlV1.AssignedTaskV1();
            BeanUtils.copyProperties(yaml.assignedTask, t.assignedTask);
        }
        if (yaml.assignedStationId!=null) {
            t.assignedStationId = new LaunchpadCommParamsYamlV1.AssignedStationIdV1();
            BeanUtils.copyProperties(yaml.assignedStationId, t.assignedStationId);
        }
        if (yaml.reAssignedStationId!=null) {
            t.reAssignedStationId = new LaunchpadCommParamsYamlV1.ReAssignStationIdV1();
            BeanUtils.copyProperties(yaml.reAssignedStationId, t.reAssignedStationId);
        }
        if (yaml.reportResultDelivering!=null) {
            t.reportResultDelivering = new LaunchpadCommParamsYamlV1.ReportResultDeliveringV1();
            t.reportResultDelivering.ids =
                    yaml.reportResultDelivering.ids!=null ? new ArrayList<>(yaml.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (yaml.execContextStatus !=null) {
            t.execContextStatus = new LaunchpadCommParamsYamlV1.ExecContextStatusV1();
            t.execContextStatus.statuses =
                    yaml.execContextStatus.statuses!=null
                            ? yaml.execContextStatus.statuses
                            .stream()
                            .map(o->new LaunchpadCommParamsYamlV1.ExecContextStatusV1.SimpleStatus(o.execContextId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (yaml.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new LaunchpadCommParamsYamlV1.ResendTaskOutputResourceV1();
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
    public String toString(LaunchpadCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public LaunchpadCommParamsYamlV1 to(String s) {
        final LaunchpadCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
