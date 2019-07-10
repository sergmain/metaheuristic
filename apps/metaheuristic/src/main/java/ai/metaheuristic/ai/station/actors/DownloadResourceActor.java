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
package ai.metaheuristic.ai.station.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.station.StationTaskService;
import ai.metaheuristic.ai.station.net.HttpClientExecutor;
import ai.metaheuristic.ai.station.tasks.DownloadResourceTask;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("station")
public class DownloadResourceActor extends AbstractTaskQueue<DownloadResourceTask> {

    private final Globals globals;
    private final StationTaskService stationTaskService;

    public DownloadResourceActor(Globals globals, StationTaskService stationTaskService) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
    }

    @PostConstruct
    public void postConstruct() {
    }

    @SuppressWarnings("Duplicates")
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        DownloadResourceTask task;
        while ((task = poll()) != null) {
            StationTask stationTask = stationTaskService.findById(task.launchpad.url, task.taskId);
            if (stationTask!=null && stationTask.finishedOn!=null) {
                log.info("Task #{} was already finished, skip it", task.taskId);
                continue;
            }
            AssetFile assetFile = ResourceUtils.prepareDataFile(task.targetDir, task.id, null);
            if (assetFile.isError ) {
                log.warn("#810.010 Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.debug("Resource was already downloaded. Asset file: {}", assetFile);
                continue;
            }

            log.info("Start processing the download task {}", task);
            try {
                final String payloadRestUrl = task.launchpad.url + Consts.REST_V1_URL + Consts.PAYLOAD_REST_URL + "/resource/" + EnumsApi.BinaryDataType.DATA;
                final String uri = payloadRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + task.stationId+ '-' + task.taskId + '-' + URLEncoder.encode(task.getId(), StandardCharsets.UTF_8.toString());

                File parentDir = assetFile.file.getParentFile();
                if (parentDir==null) {
                    String es = "#810.020 Can't get parent dir for asset file " + assetFile.file.getAbsolutePath();
                    log.error(es);
                    stationTaskService.markAsFinishedWithError(task.launchpad.url, task.taskId, es);
                    continue;
                }
                File tempFile;
                try {
                    tempFile = File.createTempFile("resource-", ".temp", parentDir);
                } catch (IOException e) {
                    String es = "#810.030 Error creating temp file in parent dir: " + parentDir.getAbsolutePath();
                    log.error(es, e);
                    stationTaskService.markAsFinishedWithError(task.launchpad.url, task.taskId, es);
                    continue;
                }

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.FlowState flowState = Enums.FlowState.none;
                int idx = 0;
                do {
                    try {
                        final URIBuilder builder = new URIBuilder(uri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("stationId", task.stationId)
                                .addParameter("taskId", Long.toString(task.getTaskId()))
                                .addParameter("code", task.getId())
                                .addParameter("chunkSize", task.chunkSize!=null ? task.chunkSize.toString() : "")
                                .addParameter("chunkNum", Integer.toString(idx));

                        final URI build = builder.build();
                        final Request request = Request.Get(build)
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response;
                        if (task.launchpad.securityEnabled) {
                            response = HttpClientExecutor.getExecutor(task.launchpad.url, task.launchpad.restUsername, task.launchpad.restToken, task.launchpad.restPassword).execute(request);
                        }
                        else {
                            response = request.execute();
                        }
                        File partFile = new File(dir, String.format(mask, idx));
                        response.saveContent(partFile);
                        if (partFile.length()==0) {
                            flowState = Enums.FlowState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            final String es = String.format("#810.035 Resource %s wasn't found on launchpad. Task #%s is finished.", task.getId(), task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            flowState = Enums.FlowState.resource_doesnt_exist;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            final String es = String.format("#810.036 Unknown error with a resource %s. Task #%s is finished.", task.getId(), task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            flowState = Enums.FlowState.unknow_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            final String es = String.format("#810.037 Unknown error with a resource %s. Task #%s is finished.", task.getId(), task.getTaskId());
                            log.warn(es);
                            stationTaskService.markAsFinishedWithError(task.launchpad.url, task.getTaskId(), es);
                            flowState = Enums.FlowState.unknow_error;
                            break;
                        }
                    }
                    catch(SocketTimeoutException e) {
                        log.error("#810.040 SocketTimeoutException, uri: " + uri, e);
                        return;
                    }
                    idx++;
                } while (idx<1000);
                if (flowState==Enums.FlowState.none) {
                    log.error("#810.050  something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (flowState==Enums.FlowState.unknow_error || flowState==Enums.FlowState.resource_doesnt_exist) {
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    for (int i = 0; i < idx; i++) {
                        FileUtils.copyFile(new File(assetFile.file.getAbsolutePath() + "." + i + ".tmp"), fos);
                    }
                }

                if (!tempFile.renameTo(assetFile.file)) {
                    log.warn("#810.060 Can't rename file {} to file {}", tempFile.getPath(), assetFile.file.getPath());
                    continue;
                }
                log.info("Resource #{} was loaded", task.getId());
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpServletResponse.SC_CONFLICT) {
                    log.warn("#810.080 Resource with id {} is broken and need to be recreated", task.getId());
                } else {
                    log.error("#810.090 HttpResponseException.getStatusCode(): {}", e.getStatusCode());
                    log.error("#810.091 HttpResponseException", e);
                }
            } catch (SocketTimeoutException e) {
                log.error("#810.100 SocketTimeoutException", e);
            } catch (IOException e) {
                log.error("#810.110 IOException", e);
            } catch (URISyntaxException e) {
                log.error("#810.120 URISyntaxException", e);
            }
        }
    }
}