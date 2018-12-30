/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.omg.CORBA.BooleanHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.List;

@Service
@Slf4j
@Profile("station")
public class ArtifactCleanerAtStation {

    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final Globals globals;
    private final MetadataService metadataService;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;

    public ArtifactCleanerAtStation(StationTaskService stationTaskService, CurrentExecState currentExecState, Globals globals, MetadataService metadataService, LaunchpadLookupExtendedService launchpadLookupExtendedService) {
        this.stationTaskService = stationTaskService;
        this.currentExecState = currentExecState;
        this.globals = globals;
        this.metadataService = metadataService;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
    }

    public void fixedDelay() {
        for (String launchpadUrl : launchpadLookupExtendedService.lookupExtendedMap.keySet()) {
            if (!globals.isStationEnabled || !currentExecState.isInit(launchpadUrl)) {
                // don't delete anything until station will receive the list of actual flow instances
                continue;
            }

            Metadata.LaunchpadInfo launchpadCode = metadataService.launchpadUrlAsCode(launchpadUrl);

            List<StationTask> all = stationTaskService.findAll(launchpadUrl);
//            log.debug("Number tasks for deleting obsolete: {} ", all.size());
            for (StationTask task : all) {
                if (currentExecState.isState(launchpadUrl, task.flowInstanceId, Enums.FlowInstanceExecState.DOESNT_EXIST)) {
                    log.info("Delete obsolete task, id {}, url {}", task.getTaskId(), launchpadUrl);
                    stationTaskService.delete(launchpadUrl, task.getTaskId());
                    continue;
                }
                if (task.clean && task.delivered && task.resourceUploaded) {
                    log.info("Delete task with clean==true, id {}, url {}", task.getTaskId(), launchpadUrl);
                    stationTaskService.delete(launchpadUrl, task.getTaskId());
                }
            }

            final File launchpadDir = new File(globals.stationTaskDir, launchpadCode.code);
            if (!launchpadDir.exists()) {
                launchpadDir.mkdir();
            }
            synchronized (StationSyncHolder.stationGlobalSync) {
                try {
                    final BooleanHolder isEmpty = new BooleanHolder(true);
                    Files.list(launchpadDir.toPath()).forEach(s -> {
                        isEmpty.value = true;
                        try {
                            Files.list(s).forEach(t -> {
                                isEmpty.value = false;
                                try {
                                    File taskYaml = new File(t.toFile(), Consts.TASK_YAML);
                                    if (!taskYaml.exists()) {
                                        FileUtils.deleteDirectory(t.toFile());
                                        // IDK is that bug or side-effect. so delete one more time
                                        FileUtils.deleteDirectory(t.toFile());
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
        }
    }
}
