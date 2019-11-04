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
public class StationCommParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<StationCommParamsYamlV3, StationCommParamsYaml, Void, StationCommParamsYamlV2, StationCommParamsYamlUtilsV2, StationCommParamsYaml> {

    @Override
    public int getVersion() {
        return 3;
    }

    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV3.class);
    }

    @Override
    public StationCommParamsYaml upgradeTo(StationCommParamsYamlV3 v1, Long ... vars) {
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
    public StationCommParamsYamlV2 downgradeTo(StationCommParamsYaml v1) {
        StationCommParamsYamlV2 t = new StationCommParamsYamlV2();

        if (v1.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV2.StationCommContextV2();
            BeanUtils.copyProperties(v1.stationCommContext, t.stationCommContext);
        }
        if (v1.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV2.RequestStationIdV2(true);
        }
        if (v1.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV2.ReportStationStatusV2();
            BeanUtils.copyProperties(v1.reportStationStatus, t.reportStationStatus);
        }
        if (v1.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV2.ReportStationTaskStatusV2();
            t.reportStationTaskStatus.statuses =
                    v1.reportStationTaskStatus.statuses!=null
                            ? v1.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ReportStationTaskStatusV2.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV2.RequestTaskV2();
            t.requestTask.acceptOnlySigned = v1.requestTask.acceptOnlySigned;
        }
        if (v1.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV2.ReportTaskProcessingResultV2();
            t.reportTaskProcessingResult.results =
                    v1.reportTaskProcessingResult.results!=null
                            ? v1.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ReportTaskProcessingResultV2.SimpleTaskExecResult(o.taskId, o.result, o.metrics))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV2.CheckForMissingOutputResourcesV2(true);
        }
        if (v1.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV2.ResendTaskOutputResourceResultV2();
            t.resendTaskOutputResourceResult.statuses =
                    v1.resendTaskOutputResourceResult.statuses!=null
                            ? v1.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ResendTaskOutputResourceResultV2.SimpleStatus(o.taskId, o.status))
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
    public StationCommParamsYamlUtilsV2 prevUtil() {
        return (StationCommParamsYamlUtilsV2)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    public String toString(StationCommParamsYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    public StationCommParamsYamlV3 to(String s) {
        final StationCommParamsYamlV3 p = getYaml().load(s);
        return p;
    }

}
