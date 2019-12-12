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
 * Date: 8/29/2019
 * Time: 6:02 PM
 */
public class LaunchpadCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<LaunchpadCommParamsYamlV1, LaunchpadCommParamsYamlV2, LaunchpadCommParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(LaunchpadCommParamsYamlV1.class);
    }

    @Override
    public LaunchpadCommParamsYamlV2 upgradeTo(LaunchpadCommParamsYamlV1 v1, Long ... vars) {
        LaunchpadCommParamsYamlV2 t = new LaunchpadCommParamsYamlV2();

        if( v1.launchpadCommContext!=null ) {
            t.launchpadCommContext = new LaunchpadCommParamsYamlV2.LaunchpadCommContextV2();
            t.launchpadCommContext.chunkSize = v1.launchpadCommContext.chunkSize;
        }
        if (v1.assignedTask!=null) {
            t.assignedTask = new LaunchpadCommParamsYamlV2.AssignedTaskV2();
            BeanUtils.copyProperties(v1.assignedTask, t.assignedTask);
        }
        if (v1.assignedStationId!=null) {
            t.assignedStationId = new LaunchpadCommParamsYamlV2.AssignedStationIdV2();
            BeanUtils.copyProperties(v1.assignedStationId, t.assignedStationId);
        }
        if (v1.reAssignedStationId!=null) {
            t.reAssignedStationId = new LaunchpadCommParamsYamlV2.ReAssignStationIdV2();
            BeanUtils.copyProperties(v1.reAssignedStationId, t.reAssignedStationId);
        }
        if (v1.reportResultDelivering!=null) {
            t.reportResultDelivering = new LaunchpadCommParamsYamlV2.ReportResultDeliveringV2();
            t.reportResultDelivering.ids =
                    v1.reportResultDelivering.ids!=null ? new ArrayList<>(v1.reportResultDelivering.ids) : new ArrayList<>();
        }
        if (v1.workbookStatus!=null) {
            t.workbookStatus = new LaunchpadCommParamsYamlV2.WorkbookStatusV2();
            t.workbookStatus.statuses =
                    v1.workbookStatus.statuses!=null
                            ? v1.workbookStatus.statuses
                            .stream()
                            .map(o->new LaunchpadCommParamsYamlV2.WorkbookStatusV2.SimpleStatus(o.workbookId, o.state))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.resendTaskOutputResource!=null) {
            t.resendTaskOutputResource = new LaunchpadCommParamsYamlV2.ResendTaskOutputResourceV2();
            t.resendTaskOutputResource.taskIds =
                    v1.resendTaskOutputResource.taskIds!=null ? new ArrayList<>(v1.resendTaskOutputResource.taskIds) : new ArrayList<>();
        }

        BeanUtils.copyProperties(v1, t);
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public LaunchpadCommParamsYamlUtilsV2 nextUtil() {
        return (LaunchpadCommParamsYamlUtilsV2)LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
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
