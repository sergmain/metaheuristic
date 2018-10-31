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
import aiai.ai.Globals;
import aiai.ai.comm.Command;
import aiai.ai.comm.Protocol;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.metadata.MetadataUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StationService {

    private final Globals globals;
    private final StationTaskService stationTaskService;
    private final StationDatasetService stationDatasetService;

    private String env;
    private EnvYaml envYaml;
    private Metadata metadata = new Metadata();

    public StationService(Globals globals, StationTaskService stationTaskService, StationDatasetService stationDatasetService) {
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.stationDatasetService = stationDatasetService;
    }

    @PostConstruct
    public void init() {
        if (!globals.isStationEnabled) {
            return;
        }

        final File file = new File(globals.stationDir, Consts.ENV_YAML_FILE_NAME);
        if (!file.exists()) {
            log.warn("Station's environment config file doesn't exist: {}", file.getPath());
            return;
        }
        try {
            env = FileUtils.readFileToString(file, Charsets.UTF_8);
            envYaml = EnvYamlUtils.toEnvYaml(env);
            if (envYaml==null) {
                log.error("env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
                throw new IllegalStateException("Station isn't configured, env.yaml is empty or doesn't exist");
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + file.getPath(), e);
        }

        final File metadataFile = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (!metadataFile.exists()) {
            log.warn("Station's metadata file doesn't exist: {}", file.getPath());
            return;
        }
        try(FileInputStream fis = new FileInputStream(metadataFile)) {
            metadata = MetadataUtils.to(fis);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + metadataFile.getPath(), e);
        }
        //noinspection unused
        int i=0;
    }

    public String getEnv() {
        return env;
    }

    EnvYaml getEnvYaml() {
        return envYaml;
    }

    Command produceReportStationStatus() {
        //noinspection UnnecessaryLocalVariable
        Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(getEnv(), globals.stationActiveTime);
        return reportStationStatus;
    }

    private static final Object syncObj = new Object();

    public String getStationId() {
        synchronized (syncObj) {
            return metadata.metadata.get(StationConsts.STATION_ID);
        }
    }

    public void setStationId(String stationId) {
        if (stationId==null) {
            throw new IllegalStateException("StationId is null");
        }
        synchronized (syncObj) {
            metadata.metadata.put(StationConsts.STATION_ID, stationId);
            updateMetadataFile();
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.info("Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.stationDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (metadataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                metadataFile.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(metadataFile, MetadataUtils.toString(metadata), Charsets.UTF_8, false);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + metadataFile.getPath(), e);
        }

    }

    public void markAsDelivered(List<Long> ids) {
        List<StationTask> list = new ArrayList<>();
        for (Long id : ids) {
            StationTask seq = stationTaskService.findByExperimentSequenceId(id);
            if(seq==null) {
                continue;
            }
            seq.setDelivered(true);
            list.add(seq);
        }
        stationTaskService.saveAll(list);
    }

    public void assignTasks(List<Protocol.AssignedTask.RawAssembling> rawAssemblings, List<Protocol.AssignedTask.DatasetProducing> datasetProducings, List<Protocol.AssignedTask.Sequence> sequences) {
        createRawAssembling(rawAssemblings);
        createDatasetProcessing(datasetProducings);
        createSequence(sequences);
    }

    private void createSequence(List<Protocol.AssignedTask.Sequence> sequences) {
        if (sequences==null) {
            return;
        }
        for (Protocol.AssignedTask.Sequence sequence : sequences) {
            stationTaskService.createSequence(sequence.experimentSequenceId, sequence.params);
        }
    }

    private void createDatasetProcessing(List<Protocol.AssignedTask.DatasetProducing> datasetProducings) {
        if (datasetProducings==null) {
            return;
        }
        for (Protocol.AssignedTask.DatasetProducing datasetProducing : datasetProducings) {
            stationDatasetService.createDatasetProducing(datasetProducing.datasetId, datasetProducing.params);
        }
    }

    private void createRawAssembling(List<Protocol.AssignedTask.RawAssembling> rawAssemblings) {
        if (rawAssemblings==null) {
            return;
        }
        for (Protocol.AssignedTask.RawAssembling rawAssembling : rawAssemblings) {
            stationDatasetService.createRawAssembling(rawAssembling.datasetId, rawAssembling.params);
        }
    }
}
