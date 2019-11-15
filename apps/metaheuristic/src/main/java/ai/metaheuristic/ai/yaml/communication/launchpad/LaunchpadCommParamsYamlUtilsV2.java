/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
public class LaunchpadCommParamsYamlUtilsV2 extends AbstractParamsYamlUtils<
        LaunchpadCommParamsYamlV2, LaunchpadCommParamsYaml, Void,
        LaunchpadCommParamsYamlV1, LaunchpadCommParamsYamlUtilsV1, LaunchpadCommParamsYaml> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(LaunchpadCommParamsYamlV2.class);
    }

    @Override
    public LaunchpadCommParamsYaml upgradeTo(LaunchpadCommParamsYamlV2 v2, Long ... vars) {
        LaunchpadCommParamsYaml t = new LaunchpadCommParamsYaml();

        if( v2.launchpadCommContext!=null ) {
            t.launchpadCommContext = new LaunchpadCommParamsYaml.LaunchpadCommContext();
            t.launchpadCommContext.chunkSize = v2.launchpadCommContext.chunkSize;
            t.launchpadCommContext.stationCommVersion = v2.launchpadCommContext.stationCommVersion;
        }
        if (v2.snippets!=null) {
            t.snippets = new LaunchpadCommParamsYaml.Snippets();
            t.snippets.infos.addAll( v2.snippets.infos
                            .stream()
                            .map(o->new LaunchpadCommParamsYaml.Snippets.Info (o.code, o.sourcing))
                            .collect(Collectors.toList())
                    );
        }
        if (v2.assignedTask!=null) {
            t.assignedTask = new LaunchpadCommParamsYaml.AssignedTask();
            BeanUtils.copyProperties(v2.assignedTask, t.assignedTask);
        }
        if (v2.assignedStationId!=null) {
            t.assignedStationId = new LaunchpadCommParamsYaml.AssignedStationId();
            BeanUtils.copyProperties(v2.assignedStationId, t.assignedStationId);
        }
        if (v2.reAssignedStationId!=null) {
            t.reAssignedStationId = new LaunchpadCommParamsYaml.ReAssignStationId();
            BeanUtils.copyProperties(v2.reAssignedStationId, t.reAssignedStationId);
        }
        if (v2.reportResultDelivering!=null) {
            t.reportResultDelivering = new LaunchpadCommParamsYaml.ReportResultDelivering();
            t.reportResultDelivering.ids =
                    v2.reportResultDelivering.ids!=null ? new ArrayList<>(v2.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (v2.workbookStatus!=null) {
            t.workbookStatus = new LaunchpadCommParamsYaml.WorkbookStatus();
            t.workbookStatus.statuses =
                    v2.workbookStatus.statuses!=null
                            ? v2.workbookStatus.statuses
                            .stream()
                            .map(o->new LaunchpadCommParamsYaml.WorkbookStatus.SimpleStatus(o.workbookId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v2.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new LaunchpadCommParamsYaml.ResendTaskOutputResource();
            t.resendTaskOutputResource.taskIds =
                    v2.resendTaskOutputResource.taskIds!=null ? new ArrayList<>(v2.resendTaskOutputResource.taskIds) : new ArrayList<>();
        }

        BeanUtils.copyProperties(v2, t);
        return t;
    }

    @Override
    public LaunchpadCommParamsYamlV1 downgradeTo(LaunchpadCommParamsYaml yaml) {
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
        if (yaml.workbookStatus!=null) {
            t.workbookStatus = new LaunchpadCommParamsYamlV1.WorkbookStatusV1();
            t.workbookStatus.statuses =
                    yaml.workbookStatus.statuses!=null
                            ? yaml.workbookStatus.statuses
                            .stream()
                            .map(o->new LaunchpadCommParamsYamlV1.WorkbookStatusV1.SimpleStatus(o.workbookId, o.state))
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
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public LaunchpadCommParamsYamlUtilsV1 prevUtil() {
        return (LaunchpadCommParamsYamlUtilsV1)LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
    }

    public String toString(LaunchpadCommParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    public LaunchpadCommParamsYamlV2 to(String s) {
        final LaunchpadCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
