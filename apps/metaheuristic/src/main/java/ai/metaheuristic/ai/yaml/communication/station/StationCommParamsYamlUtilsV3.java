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

import ai.metaheuristic.commons.S;
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
        extends AbstractParamsYamlUtils<StationCommParamsYamlV3, StationCommParamsYamlV4, StationCommParamsYamlUtilsV4, StationCommParamsYamlV2, StationCommParamsYamlUtilsV2, StationCommParamsYamlV3> {

    @Override
    public int getVersion() {
        return 3;
    }

    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV3.class);
    }

    @Override
    public StationCommParamsYamlV4 upgradeTo(StationCommParamsYamlV3 v3, Long ... vars) {
        StationCommParamsYamlV4 t = new StationCommParamsYamlV4();

        if (v3.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV4.StationCommContextV4();
            BeanUtils.copyProperties(v3.stationCommContext, t.stationCommContext);
        }
        if (v3.snippetDownloadStatus!=null) {
            t.snippetDownloadStatus = new StationCommParamsYamlV4.SnippetDownloadStatusV4();
            t.snippetDownloadStatus.statuses = v3.snippetDownloadStatus.statuses
                    .stream()
                    .map(o->{
                        StationCommParamsYamlV4.SnippetDownloadStatusV4.Status s = new StationCommParamsYamlV4.SnippetDownloadStatusV4.Status();
                        s.snippetCode = o.snippetCode;
                        s.snippetState = o.snippetState;
                        return s;
                    })
                    .collect(Collectors.toList());

        }
        if (v3.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV4.RequestStationIdV4(true);
        }
        if (v3.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV4.ReportStationStatusV4();
            BeanUtils.copyProperties(v3.reportStationStatus, t.reportStationStatus);
        }
        if (v3.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV4.ReportStationTaskStatusV4();
            t.reportStationTaskStatus.statuses =
                    v3.reportStationTaskStatus.statuses!=null
                            ? v3.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV4.ReportStationTaskStatusV4.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v3.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV4.RequestTaskV4();
            t.requestTask.acceptOnlySigned = v3.requestTask.acceptOnlySigned;
        }
        if (v3.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV4.ReportTaskProcessingResultV4();
            t.reportTaskProcessingResult.results =
                    v3.reportTaskProcessingResult.results!=null
                            ? v3.reportTaskProcessingResult.results
                            .stream()
                            .map(o->{
                                StationCommParamsYamlV4.ReportTaskProcessingResultV4.MachineLearningTaskResult ml = null;
                                if (!S.b(o.metrics)) {
                                    ml = new StationCommParamsYamlV4.ReportTaskProcessingResultV4.MachineLearningTaskResult();
                                    ml.metrics = o.metrics;
                                }
                                return new StationCommParamsYamlV4.ReportTaskProcessingResultV4.SimpleTaskExecResult(o.taskId, o.result, ml);
                            })
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v3.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV4.CheckForMissingOutputResourcesV4(true);
        }
        if (v3.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV4.ResendTaskOutputResourceResultV4();
            t.resendTaskOutputResourceResult.statuses =
                    v3.resendTaskOutputResourceResult.statuses!=null
                            ? v3.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV4.ResendTaskOutputResourceResultV4.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v3, t);
        return t;
    }

    @Override
    public StationCommParamsYamlV2 downgradeTo(StationCommParamsYamlV3 v3) {
        StationCommParamsYamlV2 t = new StationCommParamsYamlV2();

        if (v3.stationCommContext!=null) {
            t.stationCommContext = new StationCommParamsYamlV2.StationCommContextV2();
            BeanUtils.copyProperties(v3.stationCommContext, t.stationCommContext);
        }
        if (v3.requestStationId!=null) {
            t.requestStationId = new StationCommParamsYamlV2.RequestStationIdV2(true);
        }
        if (v3.reportStationStatus!=null) {
            t.reportStationStatus = new StationCommParamsYamlV2.ReportStationStatusV2();
            BeanUtils.copyProperties(v3.reportStationStatus, t.reportStationStatus);
        }
        if (v3.reportStationTaskStatus!=null) {
            t.reportStationTaskStatus = new StationCommParamsYamlV2.ReportStationTaskStatusV2();
            t.reportStationTaskStatus.statuses =
                    v3.reportStationTaskStatus.statuses!=null
                            ? v3.reportStationTaskStatus.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ReportStationTaskStatusV2.SimpleStatus(o.taskId))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v3.requestTask!=null) {
            t.requestTask = new StationCommParamsYamlV2.RequestTaskV2();
            t.requestTask.acceptOnlySigned = v3.requestTask.acceptOnlySigned;
        }
        if (v3.reportTaskProcessingResult!=null) {
            t.reportTaskProcessingResult = new StationCommParamsYamlV2.ReportTaskProcessingResultV2();
            t.reportTaskProcessingResult.results =
                    v3.reportTaskProcessingResult.results!=null
                            ? v3.reportTaskProcessingResult.results
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ReportTaskProcessingResultV2.SimpleTaskExecResult(o.taskId, o.result, o.metrics))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }
        if (v3.checkForMissingOutputResources!=null) {
            t.checkForMissingOutputResources = new StationCommParamsYamlV2.CheckForMissingOutputResourcesV2(true);
        }
        if (v3.resendTaskOutputResourceResult!=null) {
            t.resendTaskOutputResourceResult = new StationCommParamsYamlV2.ResendTaskOutputResourceResultV2();
            t.resendTaskOutputResourceResult.statuses =
                    v3.resendTaskOutputResourceResult.statuses!=null
                            ? v3.resendTaskOutputResourceResult.statuses
                            .stream()
                            .map(o->new StationCommParamsYamlV2.ResendTaskOutputResourceResultV2.SimpleStatus(o.taskId, o.status))
                            .collect(Collectors.toList())
                            : new ArrayList<>();
        }

        BeanUtils.copyProperties(v3, t);
        return t;
    }

    @Override
    public StationCommParamsYamlUtilsV4 nextUtil() {
        return (StationCommParamsYamlUtilsV4)StationCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
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
