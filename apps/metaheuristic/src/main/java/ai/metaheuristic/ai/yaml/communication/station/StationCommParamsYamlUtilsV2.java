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

package ai.metaheuristic.ai.yaml.communication.station;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class StationCommParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<StationCommParamsYamlV2, StationCommParamsYamlV3, StationCommParamsYamlUtilsV3, StationCommParamsYamlV1, StationCommParamsYamlUtilsV1, StationCommParamsYamlV2> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV2.class);
    }

    @Override
    public StationCommParamsYamlV3 upgradeTo(StationCommParamsYamlV2 v2, Long ... vars) {
        StationCommParamsYamlV3 t = new StationCommParamsYamlV3();

        if (v2.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV3.StationCommContextV3();
            BeanUtils.copyProperties(v2.stationCommContext, t.stationCommContext);
        }
        if (v2.snippetDownloadStatus!=null) {
            t.snippetDownloadStatus = new StationCommParamsYamlV3.SnippetDownloadStatusV3();
            t.snippetDownloadStatus.statuses = v2.snippetDownloadStatus.statuses
                    .stream()
                    .map(o->{
                        StationCommParamsYamlV3.SnippetDownloadStatusV3.Status s = new StationCommParamsYamlV3.SnippetDownloadStatusV3.Status();
                        s.snippetCode = o.snippetCode;
                        s.snippetState = o.snippetState;
                        return s;
                    })
                    .collect(Collectors.toList());

        }
        if (v2.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV3.RequestStationIdV3(true);
        }
        if (v2.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV3.ReportStationStatusV3();
            BeanUtils.copyProperties(v2.reportStationStatus, t.reportStationStatus);
        }
        if (v2.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV3.ReportStationTaskStatusV3();
            t.reportStationTaskStatus.statuses =
                    v2.reportStationTaskStatus.statuses!=null
                            ? v2.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ReportStationTaskStatusV3.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v2.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV3.RequestTaskV3();
            t.requestTask.acceptOnlySigned = v2.requestTask.acceptOnlySigned;
        }
        if (v2.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV3.ReportTaskProcessingResultV3();
            t.reportTaskProcessingResult.results =
                    v2.reportTaskProcessingResult.results!=null
                            ? v2.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ReportTaskProcessingResultV3.SimpleTaskExecResult(o.taskId, o.result, o.metrics, List.of()))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v2.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV3.CheckForMissingOutputResourcesV3(true);
        }
        if (v2.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV3.ResendTaskOutputResourceResultV3();
            t.resendTaskOutputResourceResult.statuses =
                    v2.resendTaskOutputResourceResult.statuses!=null
                            ? v2.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ResendTaskOutputResourceResultV3.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v2, t);
        return t;
    }

    @Override
    public StationCommParamsYamlV1 downgradeTo(StationCommParamsYamlV2 v1) {
        StationCommParamsYamlV1 t = new StationCommParamsYamlV1();

        if (v1.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV1.StationCommContextV1();
            BeanUtils.copyProperties(v1.stationCommContext, t.stationCommContext);
        }
        if (v1.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV1.RequestStationIdV1(true);
        }
        if (v1.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV1.ReportStationStatusV1();
            BeanUtils.copyProperties(v1.reportStationStatus, t.reportStationStatus);
        }
        if (v1.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV1.ReportStationTaskStatusV1();
            t.reportStationTaskStatus.statuses =
                    v1.reportStationTaskStatus.statuses!=null
                            ? v1.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV1.ReportStationTaskStatusV1.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV1.RequestTaskV1();
            t.requestTask.acceptOnlySigned = v1.requestTask.acceptOnlySigned;
        }
        if (v1.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV1.ReportTaskProcessingResultV1();
            t.reportTaskProcessingResult.results =
                    v1.reportTaskProcessingResult.results!=null
                            ? v1.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYamlV1.ReportTaskProcessingResultV1.SimpleTaskExecResult(o.taskId, o.result, o.metrics))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV1.CheckForMissingOutputResourcesV1(true);
        }
        if (v1.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV1.ResendTaskOutputResourceResultV1();
            t.resendTaskOutputResourceResult.statuses =
                    v1.resendTaskOutputResourceResult.statuses!=null
                            ? v1.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV1.ResendTaskOutputResourceResultV1.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v1, t);
        return t;
    }

    @Override
    public StationCommParamsYamlUtilsV3 nextUtil() {
        return (StationCommParamsYamlUtilsV3)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public StationCommParamsYamlUtilsV1 prevUtil() {
        return (StationCommParamsYamlUtilsV1)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
    }

    @Override
    public String toString(StationCommParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public StationCommParamsYamlV2 to(String s) {
        final StationCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
