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
public class StationCommParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<StationCommParamsYamlV4, StationCommParamsYaml, Void, StationCommParamsYamlV3, StationCommParamsYamlUtilsV3, StationCommParamsYaml> {

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV4.class);
    }

    @Override
    public StationCommParamsYaml upgradeTo(StationCommParamsYamlV4 v4, Long ... vars) {
        StationCommParamsYaml t = new StationCommParamsYaml();

        if (v4.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYaml.StationCommContext();
            BeanUtils.copyProperties(v4.stationCommContext, t.stationCommContext);
        }
        if (v4.snippetDownloadStatus!=null) {
            t.snippetDownloadStatus = new StationCommParamsYaml.SnippetDownloadStatus();
            t.snippetDownloadStatus.statuses = v4.snippetDownloadStatus.statuses
                    .stream()
                    .map(o->{
                        StationCommParamsYaml.SnippetDownloadStatus.Status s = new StationCommParamsYaml.SnippetDownloadStatus.Status();
                        s.snippetCode = o.snippetCode;
                        s.snippetState = o.snippetState;
                        return s;
                    })
                    .collect(Collectors.toList());

        }
        if (v4.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYaml.RequestStationId(true);
        }
        if (v4.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYaml.ReportStationStatus();
            BeanUtils.copyProperties(v4.reportStationStatus, t.reportStationStatus);
        }
        if (v4.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYaml.ReportStationTaskStatus();
            t.reportStationTaskStatus.statuses =
                    v4.reportStationTaskStatus.statuses!=null
                            ? v4.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYaml.ReportStationTaskStatus.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v4.requestTask!=null) {
            t.requestTask = new StationCommParamsYaml.RequestTask();
            t.requestTask.acceptOnlySigned = v4.requestTask.acceptOnlySigned;
        }
        if (v4.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYaml.ReportTaskProcessingResult();
            t.reportTaskProcessingResult.results =
                    v4.reportTaskProcessingResult.results!=null
                            ? v4.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(o.taskId, o.result,
                                    o.ml==null ? null : new StationCommParamsYaml.ReportTaskProcessingResult.MachineLearningTaskResult(o.ml.metrics, o.ml.predicted, o.ml.fitting)))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v4.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYaml.CheckForMissingOutputResources(true);
        }
        if (v4.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYaml.ResendTaskOutputResourceResult();
            t.resendTaskOutputResourceResult.statuses =
                    v4.resendTaskOutputResourceResult.statuses!=null
                            ? v4.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v4, t);
        return t;
    }

    @Override
    public StationCommParamsYamlV3 downgradeTo(StationCommParamsYaml v1) {
        StationCommParamsYamlV3 t = new StationCommParamsYamlV3();

        if (v1.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV3.StationCommContextV3();
            BeanUtils.copyProperties(v1.stationCommContext, t.stationCommContext);
        }
        if (v1.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV3.RequestStationIdV3(true);
        }
        if (v1.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV3.ReportStationStatusV3();
            BeanUtils.copyProperties(v1.reportStationStatus, t.reportStationStatus);
        }
        if (v1.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV3.ReportStationTaskStatusV3();
            t.reportStationTaskStatus.statuses =
                    v1.reportStationTaskStatus.statuses!=null
                            ? v1.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ReportStationTaskStatusV3.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV3.RequestTaskV3();
            t.requestTask.acceptOnlySigned = v1.requestTask.acceptOnlySigned;
        }
        if (v1.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV3.ReportTaskProcessingResultV3();
            t.reportTaskProcessingResult.results =
                    v1.reportTaskProcessingResult.results!=null
                            ? v1.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ReportTaskProcessingResultV3.SimpleTaskExecResult(o.taskId, o.result,
                                    o.ml!=null ? o.ml.metrics : null, null))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v1.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV3.CheckForMissingOutputResourcesV3(true);
        }
        if (v1.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV3.ResendTaskOutputResourceResultV3();
            t.resendTaskOutputResourceResult.statuses =
                    v1.resendTaskOutputResourceResult.statuses!=null
                            ? v1.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV3.ResendTaskOutputResourceResultV3.SimpleStatus(o.taskId, o.status))
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
    public StationCommParamsYamlUtilsV3 prevUtil() {
        return (StationCommParamsYamlUtilsV3)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public String toString(StationCommParamsYamlV4 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public StationCommParamsYamlV4 to(String s) {
        final StationCommParamsYamlV4 p = getYaml().load(s);
        return p;
    }

}
