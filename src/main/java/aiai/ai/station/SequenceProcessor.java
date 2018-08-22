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

import aiai.ai.Globals;
import aiai.ai.beans.LogData;
import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.repositories.LogDataRepository;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import aiai.ai.utils.DirUtils;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
@EnableScheduling
@Slf4j
public class SequenceProcessor {

    private final Globals globals;

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;
    private final ProcessService processService;
    private final StationService stationService;
    private final LogDataRepository logDataRepository;

    private Map<Long, StationDatasetUtils.DatasetFile> isDatasetReady = new HashMap<>();
    private Map<String, StationSnippetUtils.SnippetFile> isSnippetsReady = new HashMap<>();

    public SequenceProcessor(Globals globals, StationExperimentSequenceRepository stationExperimentSequenceRepository, ProcessService processService, StationService stationService, LogDataRepository logDataRepository) {
        this.globals = globals;
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
        this.processService = processService;
        this.stationService = stationService;
        this.logDataRepository = logDataRepository;
    }

    @PostConstruct
    public void init() {
    }

    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
    public void scheduleProcessor() {
        if (!globals.isStationEnabled) {
            return;
        }
        EnvYaml envYaml = EnvYamlUtils.toEnvYaml(stationService.getEnv());
        if (envYaml == null) {
            log.warn("env.yaml wasn't found or empty. path: " + globals.stationDir + "/env.yaml");
            return;
        }

        File dsDir = StationDatasetUtils.checkEvironment(globals.stationDir);
        if (dsDir == null) {
            return;
        }

        File snippetDir = StationSnippetUtils.checkEvironment(globals.stationDir);
        if (snippetDir == null) {
            return;
        }

        StationDatasetUtils.DatasetFile datasetFile;
        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            final SequenceYaml sequenceYaml = SequenceYamlUtils.toSequenceYaml(seq.getParams());
            datasetFile = isDatasetReady.get(sequenceYaml.getDatasetId());
            if (datasetFile == null) {
                datasetFile = StationDatasetUtils.getDatasetFile(dsDir, sequenceYaml.getDatasetId());
                if (datasetFile.isError || !datasetFile.isContent) {
                    continue;
                }
                isDatasetReady.put(sequenceYaml.getDatasetId(), datasetFile);
            }

            if (sequenceYaml.snippets.isEmpty()) {
                finishAndWriteToLog(seq, "Broken sequence. List of snippets is empty.");
                continue;
            }
            // right now we handle only 1 or 2 snippets. Support of bigger number of snippets in the sequence will be added later
            else if (sequenceYaml.snippets.size() == 2) {
                if ( sequenceYaml.snippets.get(0).type == SnippetType.fit && sequenceYaml.snippets.get(0).type == SnippetType.predict) {
                    finishAndWriteToLog(seq, "Check of order of snippets was failed");
                    continue;
                }
            }
            else if (sequenceYaml.snippets.size()>2) {
                finishAndWriteToLog(seq, "Number of snippets in the sequence if too long, size: " + sequenceYaml.snippets.size());
                continue;
            }
            seq.setLaunchedOn(System.currentTimeMillis());
            seq = stationExperimentSequenceRepository.save(seq);
            for (SimpleSnippet snippet : sequenceYaml.getSnippets()) {
                StationSnippetUtils.SnippetFile snippetFile = isSnippetsReady.get(snippet.code);
                if (snippetFile == null) {
                    snippetFile = StationSnippetUtils.getSnippetFile(snippetDir, snippet.getCode(), snippet.filename);
                    if (snippetFile.isError || !snippetFile.isContent) {
                        return;
                    }
                    isSnippetsReady.put(snippet.code, snippetFile);
                }

                final File paramFile = prepareParamFile(seq.getExperimentSequenceId(), snippet.getType(), seq.getParams());
                if (paramFile == null) {
                    continue;
                }
                String intepreter = envYaml.getEnvs().get(snippet.env);
                if (intepreter == null) {
                    log.warn("Can't precess sequence, interpreter wan't found for env: " + snippet.env);
                    continue;
                }

                System.out.println("!!! all system are checked, lift off !!! ");

                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(intepreter);
                    cmd.add(snippetFile.file.getAbsolutePath());
                    cmd.add(datasetFile.file.getAbsolutePath());

                    final File execDir = paramFile.getParentFile();
                    processService.execCommand(snippet.type == SnippetType.fit ? LogData.Type.FIT : LogData.Type.PREDICT, seq.getExperimentSequenceId(), cmd, execDir);
                } catch (Exception err) {
                    log.error("Error exec process " + intepreter, err);
                }
            }
            log.info("update finishedOn");
            StationExperimentSequence seqTemp = stationExperimentSequenceRepository.findById(seq.getId()).orElse(null);
            if (seqTemp == null) {
                log.error("StationExperimentSequence wasn't found for Id " + seq.getId());
            } else {
                seqTemp.setFinishedOn(System.currentTimeMillis());
                stationExperimentSequenceRepository.save(seqTemp);
            }
        }
    }

    private void finishAndWriteToLog(StationExperimentSequence seq, String es) {
        log.warn(es);
        seq.setLaunchedOn(System.currentTimeMillis());
        seq.setFinishedOn(System.currentTimeMillis());
        stationExperimentSequenceRepository.save(seq);
        LogData logData = new LogData();
        logData.setRefId(seq.getId());
        logData.setType(LogData.Type.SEQUENCE);
        logData.setLogData(es);
        logDataRepository.save(logData);
    }

    private File prepareParamFile(Long experimentSequenceId, SnippetType type, String params) {
        File seqDir = checkEvironment(globals.stationDir);
        if (seqDir == null) {
            return null;
        }

        File currDir = new File(seqDir, String.format("%05d", experimentSequenceId));
        if (!currDir.exists()) {
            boolean isOk = currDir.mkdirs();
            if (!isOk) {
                System.out.println("Can't make all directories for path: " + currDir.getAbsolutePath());
                return null;
            }
        }

        File snippetTypeDir = DirUtils.createDir(currDir, type.toString());
        if (snippetTypeDir == null) {
            return null;
        }

        File paramFile = new File(snippetTypeDir, "params.yaml");
        if (paramFile.exists()) {
            paramFile.delete();
        }
        try {
            FileUtils.writeStringToFile(paramFile, params);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return paramFile;
    }

    public static File checkEvironment(File stationDir) {
        File seqDir = new File(stationDir, "sequence");
        if (!seqDir.exists()) {
            boolean isOk = seqDir.mkdirs();
            if (!isOk) {
                System.out.println("Can't make all directories for path: " + seqDir.getAbsolutePath());
                return null;
            }
        }
        return seqDir;
    }
}