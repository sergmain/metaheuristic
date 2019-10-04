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
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class StationCommParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<StationCommParamsYamlV2, StationCommParamsYaml, Void, StationCommParamsYamlV1, StationCommParamsYamlUtilsV1, StationCommParamsYaml> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV2.class);
    }

    @Override
    public StationCommParamsYaml upgradeTo(StationCommParamsYamlV2 v1, Long ... vars) {
        StationCommParamsYaml t = new StationCommParamsYaml();

        if (v1.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYaml.StationCommContext();
            BeanUtils.copyProperties(v1.stationCommContext, t.stationCommContext);
        }
        if (v1.snippetDownloadStatus!=null) {
            t.snippetDownloadStatus = new StationCommParamsYaml.SnippetDownloadStatus();
            t.snippetDownloadStatus.statuses = v1.snippetDownloadStatus.statuses
                    .stream()
                    .map(o->{
                        StationCommParamsYaml.SnippetDownloadStatus.Status s = new StationCommParamsYaml.SnippetDownloadStatus.Status();
                        s.snippetCode = o.snippetCode;
                        s.snippetState = o.snippetState;
                        return s;
                    })
                    .collect(Collectors.toList());

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
    public StationCommParamsYamlV1 downgradeTo(StationCommParamsYaml v1) {
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
    public Void nextUtil() {
        return null;
    }

    @Override
    public StationCommParamsYamlUtilsV1 prevUtil() {
        return (StationCommParamsYamlUtilsV1)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
    }

    public String toString(StationCommParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    public StationCommParamsYamlV2 to(String s) {
        final StationCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
