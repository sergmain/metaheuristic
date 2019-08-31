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

package ai.metaheuristic.ai.yaml.communication.station;

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
public class StationCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<StationCommParamsYamlV1, StationCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV1.class);
    }

    @Override
    public StationCommParamsYaml upgradeTo(StationCommParamsYamlV1 v1, Long ... vars) {
        StationCommParamsYaml t = new StationCommParamsYaml();

        if (v1.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYaml.StationCommContext();
            BeanUtils.copyProperties(v1.stationCommContext, t.stationCommContext);
        }
        if (v1.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYaml.RequestStationId(true);
        }
        if (v1.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYaml.ReportStationStatus();
            BeanUtils.copyProperties(v1.reportStationStatus, t.reportStationStatus);
        }
        if (v1.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYaml.ReportStationTaskStatus();
            t.reportStationTaskStatus.statuses =
                    v1.reportStationTaskStatus.statuses!=null
                            ? v1.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestTask!=null) {
            t.requestTask = new StationCommParamsYaml.RequestTask();
            t.requestTask.acceptOnlySigned = v1.requestTask.acceptOnlySigned;
        }
        if (v1.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYaml.ReportTaskProcessingResult();
            t.reportTaskProcessingResult.results =
                    v1.reportTaskProcessingResult.results!=null
                            ? v1.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(o.taskId, o.result, o.metrics))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYaml.CheckForMissingOutputResources(true);
        }
        if (v1.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYaml.ResendTaskOutputResourceResult();
            t.resendTaskOutputResourceResult.statuses =
                    v1.resendTaskOutputResourceResult.statuses!=null
                            ? v1.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v1, t);
        return t;
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

    public String toString(StationCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    public StationCommParamsYamlV1 to(String s) {
        final StationCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
