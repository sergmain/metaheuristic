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
import aiai.ai.comm.Protocol;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.beans.LogData;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.yaml.station.StationExperimentSequence;
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

    private final ProcessService processService;
    private final StationService stationService;
    private final SequenceYamlUtils sequenceYamlUtils;
    private final StationExperimentService stationExperimentService;
    private final CurrentExecState currentExecState;

    private Map<Long, AssetFile> isDatasetReady = new HashMap<>();
    private Map<Long, AssetFile> isFeatureReady = new HashMap<>();
    private Map<String, StationSnippetUtils.SnippetFile> isSnippetsReady = new HashMap<>();

    public SequenceProcessor(Globals globals, ProcessService processService, StationService stationService, SequenceYamlUtils sequenceYamlUtils, StationExperimentService stationExperimentService, CurrentExecState currentExecState) {
        this.globals = globals;
        this.processService = processService;
        this.stationService = stationService;
        this.sequenceYamlUtils = sequenceYamlUtils;
        this.stationExperimentService = stationExperimentService;
        this.currentExecState = currentExecState;
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

        List<StationExperimentSequence> seqs = stationExperimentService.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            if (StringUtils.isBlank(seq.getParams())) {
                log.warn("Params for sequence {} is blank", seq.getExperimentSequenceId());
                continue;
            }
            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (!currentExecState.isStarted(sequenceYaml.experimentId)) {
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
                stationExperimentService.finishAndWriteToLog(seq, "Broken sequence. List of snippets is empty");
                continue;
            }

            File artifactDir = prepareSequenceDir(sequenceYaml.experimentId, seq.getExperimentSequenceId(), "artifacts");
            if (artifactDir == null) {
                stationExperimentService.finishAndWriteToLog(seq, "Error of configuring of environment. 'artifacts' directory wasn't created, sequence can't be processed.");
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
            seq = stationExperimentService.save(seq);
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
                    stationExperimentService.storeExecResult(seq.getExperimentSequenceId(), snippet, result, sequenceYaml.experimentId, artifactDir);
                    if (!result.isOk()) {
                        break;
                    }

                } catch (Exception err) {
                    log.error("Error exec process " + intepreter, err);
                }
            }
            stationExperimentService.markAsFinishedIfAllOk(seq.getExperimentSequenceId(), sequenceYaml);
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

    private File prepareParamFile(long experimentId, long experimentSequenceId, @NotNull SnippetType type, @NotNull String params) {
        File snippetTypeDir = prepareSequenceDir(experimentId, experimentSequenceId, type.toString());
        if (snippetTypeDir == null) {
            return null;
        }

        File paramFile = new File(snippetTypeDir, Consts.PARAMS_YAML);
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
        currentExecState.register(statuses);
    }
}