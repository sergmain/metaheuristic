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
import aiai.ai.yaml.snippet.SnippetType;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.station.StationExperimentSequence;
import aiai.ai.yaml.station.StationExperimentSequenceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StationExperimentService {

    private static final String EXPERIMENT_SEQUENCE_FORMAT_STR = "experiment%c%06d%csequence%c%06d";

    private final Globals globals;
    private final SequenceYamlUtils sequenceYamlUtils;
    private final CurrentExecState currentExecState;

    private final Map<Long, Map<Long, StationExperimentSequence>> map = new HashMap<>();

    public StationExperimentService(SequenceYamlUtils sequenceYamlUtils, Globals globals, CurrentExecState currentExecState) {
        this.currentExecState = currentExecState;
        this.sequenceYamlUtils = sequenceYamlUtils;
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (!globals.stationExperimentDir.exists()) {
            return;
        }
        try {
            Files.list(globals.stationExperimentDir.toPath()).forEach(p -> {
                final File file = p.toFile();
                long experimentId = Long.parseLong(file.getName());
                Map<Long, StationExperimentSequence> seqs = map.computeIfAbsent(experimentId, k -> new HashMap<>());
                File seqDir = new File(file, "sequence");
                try {
                    Files.list(seqDir.toPath()).forEach(s -> {
                        long seqId = Long.parseLong(s.toFile().getName());
                        File sequuenceYamlFile = new File(s.toFile(), Consts.SEQUENCE_YAML);
                        if (sequuenceYamlFile.exists()) {
                            try(FileInputStream fis = new FileInputStream(sequuenceYamlFile)) {
                                StationExperimentSequence seq = StationExperimentSequenceUtils.to(fis);
                                seqs.put(seqId, seq);
                            }
                            catch (IOException e) {
                                log.error("Error #3", e);
                                throw new RuntimeException("Error #3", e);
                            }
                        }
                    });
                }
                catch (IOException e) {
                    log.error("Error #2", e);
                    throw new RuntimeException("Error #2", e);
                }
            });
        }
        catch (IOException e) {
            log.error("Error #1", e);
            throw new RuntimeException("Error #1", e);
        }
        //noinspection unused
        int i=0;
    }

    private void putInMap(@NotNull StationExperimentSequence seq) {
        Map<Long, StationExperimentSequence> seqs = map.computeIfAbsent(seq.experimentId, k -> new HashMap<>());
        seqs.put(seq.experimentSequenceId, seq);
    }

    private void deleteFromMap(@NotNull StationExperimentSequence seq) {
        Map<Long, StationExperimentSequence> seqs = map.get(seq.experimentId);
        if (seqs==null) {
            return;
        }
        seqs.remove(seq.experimentSequenceId);
    }

    List<StationExperimentSequence> getForReporting() {
        List<StationExperimentSequence> list = findAllByFinishedOnIsNotNull();
        List<StationExperimentSequence> result = new ArrayList<>();
        for (StationExperimentSequence seq : list) {
            if (!seq.isReported() || (seq.isReported() && !seq.isDelivered() && (seq.getReportedOn()==null || (System.currentTimeMillis() - seq.getReportedOn())>60_000)) ) {
                result.add(seq);
            }
        }
        return result;
    }

    void markAsFinishedIfAllOk(Long seqId, SequenceYaml sequenceYaml) {
        log.info("markAsFinished({})", seqId);
        StationExperimentSequence seqTemp = findById(seqId);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            if (StringUtils.isBlank(seqTemp.getSnippetExecResults())) {
                seqTemp.setSnippetExecResults(SnippetExecUtils.toString(new SnippetExec()));
                save(seqTemp);
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
                save(seqTemp);
            }
        }
    }

    void finishAndWriteToLog(@NotNull StationExperimentSequence seq, String es) {
        log.warn(es);
        seq.setLaunchedOn(System.currentTimeMillis());
        seq.setFinishedOn(System.currentTimeMillis());
        seq.setSnippetExecResults(es);
        save(seq);
    }

    void saveReported(List<StationExperimentSequence> list) {
        saveAll(list);
    }

    boolean isNeedNewExperimentSequence(String stationId) {
        if (stationId==null) {
            return false;
        }
        List<StationExperimentSequence> seqs = findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            if (StringUtils.isBlank(seq.getParams())) {
                log.warn("Params for sequence {} is blank", seq.getExperimentSequenceId());
                continue;
            }
            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (currentExecState.isStarted(sequenceYaml.experimentId)) {
                return false;
            }

        }
        return true;
    }

    void storeExecResult(Long seqId, SimpleSnippet snippet, ProcessService.Result result, long experimentId, File artifactDir) {
        log.info("storeExecResult(experimentId: {}, seqId: {}, snippetOrder: {})", experimentId, seqId, snippet.order);
        StationExperimentSequence seqTemp = findById(seqId);
        if (seqTemp == null) {
            log.error("StationExperimentSequence wasn't found for Id " + seqId);
        } else {
            // store metrics after predict only
            if (snippet.type== SnippetType.predict) {
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
            save(seqTemp);
        }
    }

    List<StationExperimentSequence> findAllByFinishedOnIsNull() {
        List<StationExperimentSequence> list = new ArrayList<>();
        for (Map<Long, StationExperimentSequence> value : map.values()) {
            for (StationExperimentSequence sequence : value.values()) {
                if (sequence.finishedOn==null) {
                    list.add(sequence);
                }
            }
        }
        return list;
    }

    StationExperimentSequence findByExperimentSequenceId(Long id) {
        for (Map<Long, StationExperimentSequence> value : map.values()) {
            for (StationExperimentSequence sequence : value.values()) {
                if (sequence.getExperimentSequenceId()==id) {
                    return sequence;
                }
            }
        }
        return null;
    }

    private List<StationExperimentSequence> findAllByFinishedOnIsNotNull() {
        List<StationExperimentSequence> list = new ArrayList<>();
        for (Map<Long, StationExperimentSequence> value : map.values()) {
            for (StationExperimentSequence sequence : value.values()) {
                if (sequence.finishedOn != null) {
                    list.add(sequence);
                }
            }
        }
        return list;
    }

    Protocol.StationSequenceStatus produceStationSequenceStatus() {
        Protocol.StationSequenceStatus status = new Protocol.StationSequenceStatus(new ArrayList<>());
        List<StationExperimentSequence> list = findAllByFinishedOnIsNull();
        for (StationExperimentSequence sequence : list) {
            status.getStatuses().add( new Protocol.StationSequenceStatus.SimpleStatus(sequence.getExperimentSequenceId()));
        }
        return status;
    }

    void createSequence(Long experimentSequenceId, String params) {

        final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(params);
        final long experimentId = sequenceYaml.experimentId;

        Map<Long, StationExperimentSequence> seqs = map.computeIfAbsent(experimentId, k -> new HashMap<>());
        StationExperimentSequence seq = seqs.computeIfAbsent(experimentSequenceId, k -> new StationExperimentSequence());

        seq.experimentId = experimentId;
        seq.experimentSequenceId = experimentSequenceId;
        seq.createdOn = System.currentTimeMillis();
        seq.params = params;
        seq.finishedOn = null;

        String path = String.format("experiment%c%06d%csequence%c%06d", File.separatorChar, experimentId, File.separatorChar, File.separatorChar, experimentSequenceId);
        File systemDir = new File(globals.stationDir, path);
        try {
            if (systemDir.exists()) {
                FileUtils.deleteDirectory(systemDir);
            }
            //noinspection ResultOfMethodCallIgnored
            systemDir.mkdirs();
            File sequenceYamlFile = new File(systemDir, Consts.SEQUENCE_YAML);
            FileUtils.write(sequenceYamlFile, StationExperimentSequenceUtils.toString(seq), Charsets.UTF_8, false);
            putInMap(seq);
        }
        catch( Throwable th) {
            log.error("Error ", th);
            throw new RuntimeException("Error", th);
        }
    }

    public StationExperimentSequence save(StationExperimentSequence seq) {
        String path = String.format("experiment%c%06d%csequence%c%06d", File.separatorChar, seq.experimentId, File.separatorChar, File.separatorChar, seq.experimentSequenceId);
        File sequenceDir = new File(globals.stationDir, path);
        if (!sequenceDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            sequenceDir.mkdirs();
        }
        File sequenceYaml = new File(sequenceDir, Consts.SEQUENCE_YAML);


        if (sequenceYaml.exists()) {
            log.debug("{} file exists. Make backup", sequenceYaml.getPath());
            File yamlFileBak = new File(sequenceDir, Consts.SEQUENCE_YAML + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (sequenceYaml.exists()) {
                //noinspection ResultOfMethodCallIgnored
                sequenceYaml.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(sequenceYaml, StationExperimentSequenceUtils.toString(seq), Charsets.UTF_8, false);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + sequenceYaml.getPath(), e);
        }
        return seq;
    }

    void saveAll(List<StationExperimentSequence> list) {
        for (StationExperimentSequence stationExperimentSequence : list) {
            save(stationExperimentSequence);
        }
    }

    public StationExperimentSequence findById(Long experimentSequenceId) {
        for (Map<Long, StationExperimentSequence> value : map.values()) {
            for (StationExperimentSequence sequence : value.values()) {
                if (sequence.experimentSequenceId == experimentSequenceId) {
                    return sequence;
                }
            }
        }
        return null;
    }

    public List<StationExperimentSequence> findAll() {
        List<StationExperimentSequence> list = new ArrayList<>();
        for (Map<Long, StationExperimentSequence> entry : map.values()) {
            list.addAll(entry.values());
        }
        return list;
    }

    void deleteById(long experimentSequenceId) {
        delete( findById(experimentSequenceId) );
    }

    public void delete(StationExperimentSequence seq) {
        if (seq==null) {
            return;
        }
        String path = String.format(EXPERIMENT_SEQUENCE_FORMAT_STR, File.separatorChar, seq.experimentId, File.separatorChar, File.separatorChar, seq.experimentSequenceId);

        File systemDir = new File(globals.stationDir, path);
        try {
            if (systemDir.exists()) {
                FileUtils.deleteDirectory(systemDir);
                deleteFromMap(seq);
            }
        }
        catch( Throwable th) {
            log.error("Error deleting sequence "+ seq.experimentSequenceId, th);
        }
    }
}
