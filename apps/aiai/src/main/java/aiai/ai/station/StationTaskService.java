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
import aiai.ai.utils.DigitUtils;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.metrics.Metrics;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.station.StationTask;
import aiai.ai.yaml.station.StationTaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Service
@Slf4j
public class StationTaskService {

    private final Globals globals;
    private final CurrentExecState currentExecState;

    private final Map<Long, StationTask> map = new HashMap<>();

    public StationTaskService(Globals globals, CurrentExecState currentExecState) {
        this.currentExecState = currentExecState;
        this.globals = globals;
    }

    @PostConstruct
    public void postConstruct() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.stationTaskDir.exists()) {
            return;
        }
        try {
            Files.list(globals.stationTaskDir.toPath()).forEach(p -> {
                final File topDir = p.toFile();
                if (!topDir.isDirectory()) {
                    return;
                }
                try {
                    Files.list(topDir.toPath()).forEach(s -> {
                        long taskId = Long.parseLong(s.toFile().getName());
                        File taskYamlFile = new File(s.toFile(), Consts.TASK_YAML);
                        if (taskYamlFile.exists()) {
                            try(FileInputStream fis = new FileInputStream(taskYamlFile)) {
                                StationTask task = StationTaskUtils.to(fis);
                                map.put(taskId, task);
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

    private void putInMap(StationTask task) {
        map.put(task.taskId, task);
    }

    private void deleteFromMap(StationTask task) {
        map.remove(task.taskId);
    }

    List<StationTask> getForReporting() {
        List<StationTask> list = findAllByFinishedOnIsNotNull();
        List<StationTask> result = new ArrayList<>();
        for (StationTask stationTask : list) {
            if (!stationTask.isReported() ||
                    (stationTask.isReported() && !stationTask.isDelivered() &&
                            (stationTask.getReportedOn()==null || (System.currentTimeMillis() - stationTask.getReportedOn())>60_000)) ) {
                result.add(stationTask);
            }
        }
        return result;
    }

    void markAsFinishedIfAllOk(Long taskId, TaskParamYaml taskParamYaml) {
        log.info("markAsFinished({})", taskId);
        StationTask taskTemp = findById(taskId);
        if (taskTemp == null) {
            log.error("StationTask wasn't found for Id " + taskId);
        } else {
            if (StringUtils.isBlank(taskTemp.getSnippetExecResults())) {
                taskTemp.setSnippetExecResults(SnippetExecUtils.toString(new SnippetExec()));
                save(taskTemp);
            }
            else {
                SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(taskTemp.getSnippetExecResults());
                final int execSize = snippetExec.getExecs().size();
                if (taskParamYaml.getSnippets().size() != execSize) {
                    // if last exec Ok?
                    if (snippetExec.getExecs().get(execSize).isOk()) {
                        log.warn("Don't mark this experimentSequence as finished because not all snippets were processed");
                        return;
                    }
                }
                taskTemp.setFinishedOn(System.currentTimeMillis());
                save(taskTemp);
            }
        }
    }

    void finishAndWriteToLog(StationTask seq, String es) {
        log.warn(es);
        seq.setLaunchedOn(System.currentTimeMillis());
        seq.setFinishedOn(System.currentTimeMillis());
        seq.setSnippetExecResults(es);
        save(seq);
    }

    void saveReported(List<StationTask> list) {
        saveAll(list);
    }

    boolean isNeedNewExperimentSequence(String stationId) {
        if (stationId==null) {
            return false;
        }
        List<StationTask> tasks = findAllByFinishedOnIsNull();
        for (StationTask task : tasks) {
            if (currentExecState.isStarted(task.taskId)) {
                return false;
            }
        }
        return true;
    }

    void storeExecResult(Long taskId, SimpleSnippet snippet, ProcessService.Result result, File artifactDir) {
        log.info("storeExecResult(taskId: {}, snippetOrder: {})", taskId, snippet.order);
        StationTask seqTemp = findById(taskId);
        if (seqTemp == null) {
            log.error("StationTask wasn't found for Id " + taskId);
        } else {
            // store metrics after predict only
            if (snippet.isMetrics()) {
                File metricsFile = new File(artifactDir, Consts.METRICS_FILE_NAME);
                Metrics metrics = new Metrics();
                if (metricsFile.exists()) {
                    try {
                        String execMetrics = FileUtils.readFileToString(metricsFile, StandardCharsets.UTF_8);
                        metrics.setStatus(Metrics.Status.Ok);
                        metrics.setMetrics(execMetrics);
                    }
                    catch (IOException e) {
                        log.error("Error reading metrics file {}", metricsFile.getAbsolutePath());
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

    List<StationTask> findAllByFinishedOnIsNull() {
        List<StationTask> list = new ArrayList<>();
        for (StationTask sequence : map.values()) {
            if (sequence.finishedOn==null) {
                list.add(sequence);
            }
        }
        return list;
    }

    StationTask findByTaskId(Long id) {
        for (StationTask sequence : map.values()) {
            if (sequence.getTaskId()==id) {
                return sequence;
            }
        }
        return null;
    }

    private List<StationTask> findAllByFinishedOnIsNotNull() {
        List<StationTask> list = new ArrayList<>();
        for (StationTask sequence : map.values()) {
            if (sequence.finishedOn != null) {
                list.add(sequence);
            }
        }
        return list;
    }

    Protocol.StationTaskStatus produceStationSequenceStatus() {
        Protocol.StationTaskStatus status = new Protocol.StationTaskStatus(new ArrayList<>());
        List<StationTask> list = findAllByFinishedOnIsNull();
        for (StationTask sequence : list) {
            status.getStatuses().add( new Protocol.StationTaskStatus.SimpleStatus(sequence.getTaskId()));
        }
        return status;
    }

    void createTask(long taskId, String params) {

        StationTask seq = map.computeIfAbsent(taskId, k -> new StationTask());

        seq.taskId = taskId;
        seq.createdOn = System.currentTimeMillis();
        seq.params = params;
        seq.finishedOn = null;

        String path = getTaskPath(taskId);
        File systemDir = new File(globals.stationTaskDir, path);
        try {
            if (systemDir.exists()) {
                FileUtils.deleteDirectory(systemDir);
            }
            //noinspection ResultOfMethodCallIgnored
            systemDir.mkdirs();
            File sequenceYamlFile = new File(systemDir, Consts.TASK_YAML);
            FileUtils.write(sequenceYamlFile, StationTaskUtils.toString(seq), Charsets.UTF_8, false);
            putInMap(seq);
        }
        catch( Throwable th) {
            log.error("Error ", th);
            throw new RuntimeException("Error", th);
        }
    }

    private String getTaskPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return ""+power.power7+File.separatorChar+power.power4+File.separatorChar;
    }

    public StationTask save(StationTask task) {
        String path = String.format("%06d", task.taskId);
        File sequenceDir = new File(globals.stationTaskDir, path);
        if (!sequenceDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            sequenceDir.mkdirs();
        }
        File sequenceYaml = new File(sequenceDir, Consts.TASK_YAML);


        if (sequenceYaml.exists()) {
            log.debug("{} file exists. Make backup", sequenceYaml.getPath());
            File yamlFileBak = new File(sequenceDir, Consts.TASK_YAML + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (sequenceYaml.exists()) {
                //noinspection ResultOfMethodCallIgnored
                sequenceYaml.renameTo(yamlFileBak);
            }
        }

        try {
            FileUtils.write(sequenceYaml, StationTaskUtils.toString(task), Charsets.UTF_8, false);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while writing to file: " + sequenceYaml.getPath(), e);
        }
        return task;
    }

    void saveAll(List<StationTask> list) {
        for (StationTask stationTask : list) {
            save(stationTask);
        }
    }

    public StationTask findById(Long taskId) {
        for (StationTask task : map.values()) {
            if (task.taskId == taskId) {
                return task;
            }
        }
        return null;
    }

    public Collection<StationTask> findAll() {
        return map.values();
    }

    void deleteById(long taskId) {
        delete( findById(taskId) );
    }

    public void delete(final StationTask task) {
        if (task==null) {
            return;
        }
        final String path = getTaskPath(task.taskId);

        final File systemDir = new File(globals.stationDir, path);
        try {
            if (systemDir.exists()) {
                FileUtils.deleteDirectory(systemDir);
                deleteFromMap(task);
            }
        }
        catch( Throwable th) {
            log.error("Error deleting task "+ task.taskId, th);
        }
    }
}
