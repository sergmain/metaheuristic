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

import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import aiai.ai.utils.DirUtils;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
@EnableScheduling
public class SequenceProcessor {

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    private File stationDir;

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;
    private final ProcessService processService;

    private Map<Long, Boolean> isDatasetReady = new HashMap<>();
    private Map<String, StationSnippetUtils.SnippetFile> isSnippetsReady = new HashMap<>();

    public SequenceProcessor(StationExperimentSequenceRepository stationExperimentSequenceRepository, ProcessService processService) {
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
        this.processService = processService;
    }

    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.task-assigner-task.timeout'), 3, 20, 10)*1000 }")
    public void scheduleProcessor() {

        File dsDir = StationDatasetUtils.checkEvironment(stationDir);
        if (dsDir==null) {
            return;
        }

        File snippetDir = StationSnippetUtils.checkEvironment(stationDir);
        if (snippetDir==null) {
            return;
        }

        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            final SequenceYaml sequenceYaml = SequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (!isDatasetReady.getOrDefault(sequenceYaml.getDatasetId(), false)) {
                StationDatasetUtils.DatasetFile datasetFile = StationDatasetUtils.getDatasetFile(dsDir, sequenceYaml.getDatasetId());
                if (datasetFile.isError || !datasetFile.isContent) {
                    continue;
                }
                isDatasetReady.put(sequenceYaml.getDatasetId(), true);
            }
            for (SimpleSnippet snippet : sequenceYaml.getSnippets()) {
                StationSnippetUtils.SnippetFile snippetFile = isSnippetsReady.get(snippet.code);
                if (snippetFile==null) {
                    snippetFile = StationSnippetUtils.getSnippetFile(snippetDir, snippet.getCode(), snippet.filename);
                    if (snippetFile.isError || !snippetFile.isContent) {
                        return;
                    }
                    isSnippetsReady.put(snippet.code, snippetFile);
                }

                final File paramFile = prepareParamFile(seq.getExperimentSequenceId(), snippet.getType(), seq.getParams());
                if (paramFile==null) {
                    continue;
                }
                // all resources are prepared, go
                System.out.println("!!!!! all resources are prepared, go !!!! ");

                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(snippetFile.file.getPath());

                    final File execDir = paramFile.getParentFile();
//                    processService.execCommand(snippet.type==SnippetType.fit ? LogData.Type.FIT : LogData.Type.PREDICT, seq.getExperimentSequenceId(), cmd, execDir);

                } catch (Exception err) {
                    err.printStackTrace();
                }
            }
        }
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

    private File prepareParamFile(Long experimentSequenceId, SnippetType type, String params) {
        File seqDir = checkEvironment(stationDir);
        if (seqDir==null) {
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

        File paramFile = new File(snippetTypeDir, "params.yaml" );
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
}