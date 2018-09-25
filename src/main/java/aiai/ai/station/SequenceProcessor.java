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
import aiai.ai.launchpad.beans.LogData;
import aiai.ai.station.beans.StationExperimentSequence;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.repositories.LogDataRepository;
import aiai.ai.station.repositories.StationExperimentSequenceRepository;
import aiai.ai.utils.DirUtils;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleFeature;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SequenceProcessor {

    private final Globals globals;

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;
    private final ProcessService processService;
    private final StationService stationService;
    private final LogDataRepository logDataRepository;
    private final SequenceYamlUtils sequenceYamlUtils;

    private Map<Long, AssetFile> isDatasetReady = new HashMap<>();
    private Map<Long, AssetFile> isFeatureReady = new HashMap<>();
    private Map<String, StationSnippetUtils.SnippetFile> isSnippetsReady = new HashMap<>();

    static class CurrentExecState {
        private final Map<Long, Enums.ExperimentExecState> experimentState = new HashMap<>();
        boolean isInit = false;

        private void register(List<Protocol.ExperimentStatus.SimpleStatus> statuses) {
            synchronized(experimentState) {
                for (Protocol.ExperimentStatus.SimpleStatus status : statuses) {
                    experimentState.put(status.experimentId, status.state);
                }
                isInit = true;
            }
        }

        Enums.ExperimentExecState getState(long experimentId) {
            synchronized(experimentState) {
                if (!isInit) {
                    return null;
                }
                return experimentState.getOrDefault(experimentId, Enums.ExperimentExecState.DOESNT_EXIST);
            }
        }

        boolean isState(long experimentId, Enums.ExperimentExecState state) {
            Enums.ExperimentExecState currState = getState(experimentId);
            return currState!=null && currState==state;
        }

        boolean isStarted(long experimentId) {
            return isState(experimentId, Enums.ExperimentExecState.STARTED);
        }
    }

    final CurrentExecState STATE = new CurrentExecState();

    public SequenceProcessor(Globals globals, StationExperimentSequenceRepository stationExperimentSequenceRepository, ProcessService processService, StationService stationService, LogDataRepository logDataRepository, SequenceYamlUtils sequenceYamlUtils) {
        this.globals = globals;
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
        this.processService = processService;
        this.stationService = stationService;
        this.logDataRepository = logDataRepository;
        this.sequenceYamlUtils = sequenceYamlUtils;
    }

    @PostConstruct
    public void init() {
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }
        EnvYaml envYaml = EnvYamlUtils.toEnvYaml(stationService.getEnv());
        if (envYaml == null) {
            log.warn("env.yaml wasn't found or empty. path: {}{}env.yaml", globals.stationDir, File.separatorChar );
            return;
        }

        File stationDatasetDir = StationDatasetUtils.checkAndCreateDatasetDir(globals.stationDir);
        if (stationDatasetDir == null) {
            return;
        }

        File snippetDir = StationSnippetUtils.checkEvironment(globals.stationDir);
        if (snippetDir == null) {
            return;
        }

        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            if (StringUtils.isBlank(seq.getParams())) {
                // very strange behaviour. this field is required in DB and can't be null
                // is this bug in mysql or it's a spring's data bug with MEDIUMTEXT fields?
                log.warn("Params for sequence {} is blank", seq.getId());
                continue;
            }
            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (!STATE.isStarted(sequenceYaml.experimentId)) {
                continue;
            }
            AssetFile datasetFile = isDatasetReady.get(sequenceYaml.dataset.id);
            if (datasetFile == null) {
                datasetFile = StationDatasetUtils.prepareDatasetFile(stationDatasetDir, sequenceYaml.dataset.id);
                // is this dataset prepared?
                if (datasetFile.isError || !datasetFile.isContent) {
                    log.info("Dataset #{} hasn't been prepared yet", sequenceYaml.dataset.id);
                    continue;
                }
                isDatasetReady.put(sequenceYaml.dataset.id, datasetFile);
            }

            if (sequenceYaml.snippets.isEmpty()) {
                finishAndWriteToLog(seq, "Broken sequence. List of snippets is empty");
                continue;
            }

            File artifactDir = prepareSequenceDir(sequenceYaml.experimentId, seq.getExperimentSequenceId(), "artifacts");
            if (artifactDir == null) {
                finishAndWriteToLog(seq, "Error of configuring of environment. 'artifacts' directory wasn't created, sequence can't be processed.");
                continue;
            }

            boolean isFeatureOk = true;
            for (SimpleFeature feature : sequenceYaml.features) {
                AssetFile assetFile= isFeatureReady.get(feature.id);
                if (assetFile == null) {
                    assetFile = StationFeatureUtils.prepareFeatureFile(stationDatasetDir, sequenceYaml.dataset.id, feature.id);
                    // is this feature prepared?
                    if (assetFile.isError || !assetFile.isContent) {
                        log.info("Feature hasn't been prepared yet, {}", assetFile);
                        isFeatureOk = false;
                        continue;
                    }
                    isFeatureReady.put(sequenceYaml.dataset.id, assetFile);
                }
            }
            if (!isFeatureOk) {
                continue;
            }

            // at this point dataset and all features have to be downloaded from server

            sequenceYaml.artifactPath = artifactDir.getAbsolutePath();
            initAllPaths(stationDatasetDir, sequenceYaml);
            final String params = sequenceYamlUtils.toString(sequenceYaml);

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
                    // stop processing this sequence because last snippet was finished with an error
                    break;
                }
                if (isThisSnippetCompleted(snippet, snippetExec)) {
                    continue;
                }

                final File paramFile = prepareParamFile(sequenceYaml.experimentId, seq.getExperimentSequenceId(), snippet.getType(), params);
                if (paramFile == null) {
                    break;
                }
                String intepreter = envYaml.getEnvs().get(snippet.env);
                if (intepreter == null) {
                    log.warn("Can't process sequence, interpreter wasn't found for env: {}", snippet.env);
                    break;
                }

                log.info("all system are checked, lift off");

                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(intepreter);
                    cmd.add(snippetFile.file.getAbsolutePath());

                    final File execDir = paramFile.getParentFile();
                    ProcessService.Result result = processService.execCommand(snippet.type == SnippetType.fit ? LogData.Type.FIT : LogData.Type.PREDICT, seq.getExperimentSequenceId(), cmd, execDir);
                    storeExecResult(seq.getId(), snippet, result, sequenceYaml.experimentId, artifactDir);
                    if (!result.isOk()) {
                        break;
                    }

                } catch (Exception err) {
                    log.error("Error exec process " + intepreter, err);
                }
            }
            markAsFinishedIfAllOk(seq.getId(), sequenceYaml);
        }
    }

    private void initAllPaths(File stationDatasetDir, SequenceYaml sequenceYaml) {
        final AssetFile datasetAssetFile = StationDatasetUtils.prepareDatasetFile(stationDatasetDir, sequenceYaml.dataset.id);
        if (datasetAssetFile.isError || !datasetAssetFile.isContent ) {
            log.warn("Dataset file wasn't found. {}", datasetAssetFile);
            return;
        }
        sequenceYaml.dataset.path = datasetAssetFile.file.getAbsolutePath();

        for (SimpleFeature feature : sequenceYaml.features) {
            AssetFile featureAssetFile = StationFeatureUtils.prepareFeatureFile(stationDatasetDir, sequenceYaml.dataset.id, feature.id);
            if (featureAssetFile.isError || !featureAssetFile.isContent ) {
                log.warn("Feature file wasn't found. {}", featureAssetFile);
                return;
            }
            feature.path = featureAssetFile.file.getAbsolutePath();
        }
    }

    private void storeExecResult(Long seqId, SimpleSnippet snippet, ProcessService.Result result, long experimentId, File artifactDir) {
        log.info("storeExecResult(experimentId: {}, seqId: {}, snippetOrder: {})", experimentId, seqId, snippet.order);
        StationExperimentSequence seqTemp = stationExperimentSequenceRepository.findById(seqId).orElse(null);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            // store metrics after predict only
            if (snippet.type==SnippetType.predict) {
                File metricsFile = new File(artifactDir, Consts.METRICS_FILE_NAME);
                Metrics metrics = new Metrics();
                if (metricsFile.exists()) {
                    try {
                        String execMetrics = FileUtils.readFileToString(metricsFile, StandardCharsets.UTF_8);
                        metrics.setStatus(Metrics.Status.Ok);
                        metrics.setMetrics(execMetrics);
                    }
                    catch (IOException e) {
                        log.error("Erorr reading metrics file {}", metricsFile.getAbsolutePath());
                        seqTemp.setMetrics("system-error : " + e.toString());
                        metrics.setStatus(Metrics.Status.Error);
                        metrics.setError(e.toString());
                    }
                } else {
                    metrics.setStatus(Metrics.Status.NotFound);
                }
                seqTemp.setMetrics(MetricsUtils.toString(metrics));
            }
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(seqTemp.getSnippetExecResults());
            if (snippetExec==null) {
                snippetExec = new SnippetExec();
            }
            snippetExec.getExecs().put(snippet.order, result);
            String yaml = SnippetExecUtils.toString(snippetExec);
            seqTemp.setSnippetExecResults(yaml);
            stationExperimentSequenceRepository.save(seqTemp);
        }
    }

    private void markAsFinishedIfAllOk(Long seqId, SequenceYaml sequenceYaml) {
        log.info("markAsFinished({})", seqId);
        StationExperimentSequence seqTemp = stationExperimentSequenceRepository.findById(seqId).orElse(null);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            if (StringUtils.isBlank(seqTemp.getSnippetExecResults())) {
                seqTemp.setSnippetExecResults(SnippetExecUtils.toString(new SnippetExec()));
                stationExperimentSequenceRepository.save(seqTemp);
            }
            else {
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
    }

    @Contract("_, null -> false")
    private boolean isThisSnippetCompleted(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        return snippetExec.execs.get(snippet.order)!=null;
    }

    @Contract("_, null -> false")
    private boolean isThisSnippetCompletedWithError(SimpleSnippet snippet, SnippetExec snippetExec) {
        if (snippetExec ==null) {
            return false;
        }
        final ProcessService.Result result = snippetExec.execs.get(snippet.order);
        return result!=null && !result.isOk();
    }

    private void finishAndWriteToLog(@NotNull StationExperimentSequence seq, String es) {
        log.warn(es);
        seq.setLaunchedOn(System.currentTimeMillis());
        seq.setFinishedOn(System.currentTimeMillis());
        seq.setSnippetExecResults(es);
        stationExperimentSequenceRepository.save(seq);
        LogData logData = new LogData();
        logData.setRefId(seq.getId());
        logData.setType(LogData.Type.SEQUENCE);
        logData.setLogData(es);
        logDataRepository.save(logData);
    }

    private File prepareParamFile(long experimentId, long experimentSequenceId, @NotNull SnippetType type, @NotNull String params) {
        File snippetTypeDir = prepareSequenceDir(experimentId, experimentSequenceId, type.toString());
        if (snippetTypeDir == null) {
            return null;
        }

        File paramFile = new File(snippetTypeDir, "params.yaml");
        if (paramFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            paramFile.delete();
        }
        try {
            FileUtils.writeStringToFile(paramFile, params, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error with writing to params.yaml file", e);
            return null;
        }
        return paramFile;
    }

    private File prepareSequenceDir(long experimentId, Long experimentSequenceId, String snippetType) {
        String path = String.format("experiment%c%06d%csequence%c%06d%c%s", File.separatorChar, experimentId, File.separatorChar, File.separatorChar, experimentSequenceId, File.separatorChar, snippetType);
        //noinspection UnnecessaryLocalVariable
        File snippetTypeDir = DirUtils.createDir(globals.stationDir, path);
        return snippetTypeDir;
    }

    public void processExperimentStatus(List<Protocol.ExperimentStatus.SimpleStatus> statuses) {
        STATE.register(statuses);
    }
}