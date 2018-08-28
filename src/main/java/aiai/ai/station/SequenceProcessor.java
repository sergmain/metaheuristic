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
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleFeature;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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

    private Map<Long, AssetFile> isDatasetReady = new HashMap<>();
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

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
    public void scheduleProcessor() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }
        EnvYaml envYaml = EnvYamlUtils.toEnvYaml(stationService.getEnv());
        if (envYaml == null) {
            log.warn("env.yaml wasn't found or empty. path: {}/env.yaml", globals.stationDir );
            return;
        }

        File stationDir = StationDatasetUtils.checkEvironment(globals.stationDir);
        if (stationDir == null) {
            return;
        }

        File snippetDir = StationSnippetUtils.checkEvironment(globals.stationDir);
        if (snippetDir == null) {
            return;
        }

        AssetFile datasetFile;
        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            final SequenceYaml sequenceYaml = SequenceYamlUtils.toSequenceYaml(seq.getParams());
            datasetFile = isDatasetReady.get(sequenceYaml.dataset.id);
            if (datasetFile == null) {
                datasetFile = StationDatasetUtils.prepareDatasetFile(stationDir, sequenceYaml.dataset.id);
                if (datasetFile.isError || !datasetFile.isContent) {
                    continue;
                }
                isDatasetReady.put(sequenceYaml.dataset.id, datasetFile);
            }

            if (sequenceYaml.snippets.isEmpty()) {
                finishAndWriteToLog(seq, "Broken sequence. List of snippets is empty.");
                continue;
            }

            File artifactDir = prepareSequenceDir(seq.getExperimentSequenceId(), "artifacts");
            if (artifactDir == null) {
                continue;
            }
            sequenceYaml.artifactPath = artifactDir.getAbsolutePath();
            initAllPaths(stationDir, sequenceYaml);
            final String params = SequenceYamlUtils.toString(sequenceYaml);

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
                SnippetExec snippetExec =  SnippetExecUtils.toSnippetExec(seq.getSnippetExecResults());
                if (isThisSnippetCompletedWithError(snippet, snippetExec)) {
                    // stop processing this sequence
                    break;
                }
                if (isThisSnippetCompleted(snippet, snippetExec)) {
                    continue;
                }

                final File paramFile = prepareParamFile(seq.getExperimentSequenceId(), snippet.getType(), params);
                if (paramFile == null) {
                    continue;
                }
                String intepreter = envYaml.getEnvs().get(snippet.env);
                if (intepreter == null) {
                    log.warn("Can't process sequence, interpreter wasn't found for env: {}", snippet.env);
                    continue;
                }

                log.info("!!! all system are checked, lift off !!! ");

                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(intepreter);
                    cmd.add(snippetFile.file.getAbsolutePath());

                    final File execDir = paramFile.getParentFile();
                    ProcessService.Result result = processService.execCommand(snippet.type == SnippetType.fit ? LogData.Type.FIT : LogData.Type.PREDICT, seq.getExperimentSequenceId(), cmd, execDir);
                    storeExecResult(seq.getId(), snippet.order, result);
                    if (!result.isOk()) {
                        break;
                    }

                } catch (Exception err) {
                    log.error("Error exec process " + intepreter, err);
                }
            }
            markAsFinished(seq.getId(), sequenceYaml);
        }
    }

    private void initAllPaths(File stationDir, SequenceYaml sequenceYaml) {
        final AssetFile datasetAssetFile = StationDatasetUtils.prepareDatasetFile(stationDir, sequenceYaml.dataset.id);
        if (datasetAssetFile.isError || !datasetAssetFile.isContent ) {
            log.warn("Dataset file wasn't found. {}", datasetAssetFile);
            return;
        }
        sequenceYaml.dataset.path = datasetAssetFile.file.getAbsolutePath();

        for (SimpleFeature feature : sequenceYaml.features) {
            AssetFile featureAssetFile = StationFeatureUtils.prepareFeatureFile(stationDir, sequenceYaml.dataset.id, feature.id);
            if (featureAssetFile.isError || !featureAssetFile.isContent ) {
                log.warn("Feature file wasn't found. {}", featureAssetFile);
                return;
            }
            feature.path = featureAssetFile.file.getAbsolutePath();
        }
    }

    private void storeExecResult(Long seqId, int snippetOrder, ProcessService.Result result) {
        log.info("storeExecResult({}, {})", seqId, snippetOrder);
        StationExperimentSequence seqTemp = stationExperimentSequenceRepository.findById(seqId).orElse(null);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(seqTemp.getSnippetExecResults());
            if (snippetExec==null) {
                snippetExec = new SnippetExec();
            }
            snippetExec.getExecs().put(snippetOrder, result);
            String yaml = SnippetExecUtils.toString(snippetExec);
            seqTemp.setSnippetExecResults(yaml);
            stationExperimentSequenceRepository.save(seqTemp);
        }
    }

    private void markAsFinished(Long seqId, SequenceYaml sequenceYaml) {
        log.info("markAsFinished({})", seqId);
        StationExperimentSequence seqTemp = stationExperimentSequenceRepository.findById(seqId).orElse(null);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            if (StringUtils.isBlank(seqTemp.getSnippetExecResults())) {
                seqTemp.setSnippetExecResults(SnippetExecUtils.toString(new SnippetExec()));
                stationExperimentSequenceRepository.save(seqTemp);
            }
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(seqTemp.getSnippetExecResults());
            final int execSize = snippetExec.getExecs().size();
            if (sequenceYaml.getSnippets().size() != execSize) {
                // if last exec Ok?
                if (snippetExec.getExecs().get(execSize).isOk()) {
                    log.warn("Don't mark this experimentSequence as finished because not all snippets were processed");
                    return;
                }
            }
            seqTemp.setFinishedOn(System.currentTimeMillis());
            stationExperimentSequenceRepository.save(seqTemp);
        }
    }

    private boolean isThisSnippetCompleted(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        return snippetExec.execs.get(snippet.order)!=null;
    }

    private boolean isThisSnippetCompletedWithError(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        final ProcessService.Result result = snippetExec.execs.get(snippet.order);
        return result!=null && !result.isOk();
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
        File snippetTypeDir = prepareSequenceDir(experimentSequenceId, type.toString());
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
            log.error("Error with writing to params.yaml file", e);
            return null;
        }
        return paramFile;
    }

    private File prepareSequenceDir(Long experimentSequenceId, String snippetType) {
        String path = String.format("sequence%c%06d%c%s", File.separatorChar, experimentSequenceId, File.separatorChar, snippetType);
        File snippetTypeDir = DirUtils.createDir(globals.stationDir, path);
        return snippetTypeDir;
    }

}