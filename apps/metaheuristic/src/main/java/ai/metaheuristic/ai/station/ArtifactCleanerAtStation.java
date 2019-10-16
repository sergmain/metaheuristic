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
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;

    public void fixedDelay() {
        for (String launchpadUrl : launchpadLookupExtendedService.lookupExtendedMap.keySet()) {
            if (!globals.isStationEnabled || !currentExecState.isInited(launchpadUrl)) {
                // don't delete anything until the station has received the list of actual workbooks
                continue;
            }

            Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);
            final File launchpadDir = new File(globals.stationTaskDir, launchpadCode.code);
            if (!launchpadDir.exists()) {
                launchpadDir.mkdir();
            }

            List<StationTask> all = stationTaskService.findAll(launchpadUrl);
            for (StationTask task : all) {
                if (currentExecState.isState(launchpadUrl, task.workbookId, EnumsApi.WorkbookExecState.DOESNT_EXIST)) {
                    log.info("Delete obsolete task, id {}, url {}", task.getTaskId(), launchpadUrl);
                    stationTaskService.delete(launchpadUrl, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.completed) {
                    log.info("Delete task with (task.clean && task.delivered && task.completed), id {}, url {}", task.getTaskId(), launchpadUrl);
                    stationTaskService.delete(launchpadUrl, task.getTaskId());
                }
            }

/*
            synchronized (StationSyncHolder.stationGlobalSync) {
                try {
                    final AtomicBoolean isEmpty = new AtomicBoolean(true);
                    Files.list(launchpadDir.toPath()).forEach(s -> {
                        isEmpty.set(true);
                        try {
                            Files.list(s).forEach(t -> {
                                isEmpty.set(false);
                                try {
                                    File taskYaml = new File(t.toFile(), Consts.TASK_YAML);
                                    if (!taskYaml.exists()) {
                                        FileUtils.deleteDirectory(t.toFile());
                                        // IDK is that bug or side-effect. so delete one more time
                                        // FileUtils.deleteDirectory(t.toFile());
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
