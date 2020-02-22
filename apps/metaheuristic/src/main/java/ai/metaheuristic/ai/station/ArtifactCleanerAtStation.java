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
package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class ArtifactCleanerAtStation {

    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final Globals globals;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService;

    public void fixedDelay() {
        for (String mh.dispatcher.Url : mh.dispatcher.LookupExtendedService.lookupExtendedMap.keySet()) {
            if (!globals.isStationEnabled || !currentExecState.isInited(mh.dispatcher.Url)) {
                // don't delete anything until the station has received the list of actual ExecContexts
                continue;
            }

            Metadata.DispatcherInfo mh.dispatcher.Code = metadataService.mh.dispatcher.UrlAsCode(mh.dispatcher.Url);
            final File mh.dispatcher.Dir = new File(globals.stationTaskDir, mh.dispatcher.Code.code);
            if (!mh.dispatcher.Dir.exists()) {
                mh.dispatcher.Dir.mkdir();
            }

            List<StationTask> all = stationTaskService.findAll(mh.dispatcher.Url);
            for (StationTask task : all) {
                if (currentExecState.isState(mh.dispatcher.Url, task.execContextId, EnumsApi.ExecContextState.DOESNT_EXIST)) {
                    log.info("Delete obsolete task, id {}, url {}", task.getTaskId(), mh.dispatcher.Url);
                    stationTaskService.delete(mh.dispatcher.Url, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.completed) {
                    log.info("Delete task with (task.clean && task.delivered && task.completed), id {}, url {}", task.getTaskId(), mh.dispatcher.Url);
                    stationTaskService.delete(mh.dispatcher.Url, task.getTaskId());
                }
            }

/*
            synchronized (StationSyncHolder.stationGlobalSync) {
                try {
                    final AtomicBoolean isEmpty = new AtomicBoolean(true);
                    Files.list(mh.dispatcher.Dir.toPath()).forEach(s -> {
                        isEmpty.set(true);
                        try {
                            Files.list(s).forEach(t -> {
                                isEmpty.set(false);
                                try {
                                    File taskYaml = new File(t.toFile(), Consts.TASK_YAML);
                                    if (!taskYaml.exists()) {
                                        StationTaskService.deleteDir(t.toFile(), "delete in ArtifactCleanerAtStation.fixedDelay()");
                                    }
                                } catch (IOException e) {
                                    log.error("#090.01 Error while deleting path {}, this isn't fatal error.", t);
                                }
                            });
                        } catch (AccessDeniedException e) {
                            // ok, may be later
                        } catch (IOException e) {
                            log.error("#090.07 Error while cleaning up broken tasks", e);
                        }
                    });
                } catch (IOException e) {
                    log.error("#090.07 Error while cleaning up broken tasks", e);
                }
            }
*/
        }
    }
}
