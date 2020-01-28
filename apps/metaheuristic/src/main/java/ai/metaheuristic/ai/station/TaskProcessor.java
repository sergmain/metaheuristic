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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.core.ExecProcessService;
import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.station.station_resource.ResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.yaml.launchpad_lookup.ExtendedTimePeriod;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class TaskProcessor {

    private final Globals globals;

    private final ExecProcessService execProcessService;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService ;
    private final MetadataService metadataService;
    private final EnvService envService;
    private final StationService stationService;
    private final ResourceProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;
    private final TaskProcessorStateService taskProcessorStateService;

    @Data
    public static class SnippetPrepareResult {
        public TaskParamsYaml.SnippetConfig snippet;
        public AssetFile snippetAssetFile;
        public SnippetApiData.SnippetExecResult snippetExecResult;
        boolean isLoaded = true;
        boolean isError = false;
    }

    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        // find all tasks which weren't completed and  weren't finished and resources aren't prepared yet
        List<StationTask> tasks = stationTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(true);
        for (StationTask task : tasks) {

            if (task.launchedOn!=null && task.finishedOn!=null && taskProcessorStateService.currentTaskId==null) {
                log.warn("#100.001 unusual situation, there isn't any processed task (currentTaskId==null) but task #{} was already launched and then finished", task.taskId);
            }
            if (StringUtils.isBlank(task.launchpadUrl)) {
                final String es = "#100.005 task.launchpadUrl is blank for task #" + task.taskId;
                log.warn(es);
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, es);
                continue;
            }

            final Metadata.LaunchpadInfo launchpadInfo = metadataService.launchpadUrlAsCode(task.launchpadUrl);
            if (launchpadInfo==null) {
                final String es = "#100.010 launchpadInfo is null for "+task.launchpadUrl+". task #" + task.taskId;
                log.warn(es);
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, es);
                continue;
            }

            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad = launchpadLookupExtendedService.lookupExtendedMap.get(task.launchpadUrl);
            if (launchpad==null) {
                final String es = "#100.020 Broken task #"+task.taskId+". Launchpad wasn't found for url " + task.launchpadUrl;
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, es);
                continue;
            }

            if (launchpad.schedule.isCurrentTimeInactive()) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("Can't process task #{} for url {} at this time, time: {}, permitted period of time: {}", task.taskId, task.launchpadUrl, new Date(), launchpad.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("#100.030 Params for task #{} is blank", task.getTaskId());
                continue;
            }

            EnumsApi.WorkbookExecState state = currentExecState.getState(task.launchpadUrl, task.workbookId);
            if (state== EnumsApi.WorkbookExecState.UNKNOWN) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("The state for Workbook #{}, host {} is unknown, delete a task #{}", task.workbookId, task.launchpadUrl, task.taskId);
                continue;
            }

            if (state!= EnumsApi.WorkbookExecState.STARTED) {
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("The state for Workbook #{}, host: {}, is {}, delete a task #{}", task.workbookId, task.launchpadUrl, state, task.taskId);
                continue;
            }

            log.info("Start processing task {}", task);
            File taskDir = stationTaskService.prepareTaskDir(task.launchpadUrl, task.taskId);

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, launchpadInfo, taskParamYaml, launchpad, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            File outputResourceFile = stationService.getOutputResourceFile(task, taskParamYaml, launchpad, taskDir);
            if (outputResourceFile==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "#100.040 Broken task. Can't create outputResourceFile");
                continue;
            }
            DataStorageParams dsp = taskParamYaml.taskYaml.getResourceStorageUrls()
                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
            if (dsp==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "#100.050 Broken task. Can't find params for outputResourceCode");
                continue;
            }
            if (taskParamYaml.taskYaml.snippet==null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "#100.080 Broken task. Snippet isn't defined");
                continue;
            }

            File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
            if (artifactDir == null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "#100.090 Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            File systemDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.SYSTEM_DIR);
            if (systemDir == null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, "#100.100 Error of configuring of environment. 'system' directory wasn't created, task can't be processed.");
                continue;
            }

            String status = stationTaskService.prepareEnvironment(artifactDir);
            if (status!=null) {
                stationTaskService.markAsFinishedWithError(task.launchpadUrl, task.taskId, status);
            }

            boolean isNotReady = false;
            final SnippetPrepareResult[] results = new SnippetPrepareResult[ totalCountOfSnippets(taskParamYaml.taskYaml) ];
            int idx = 0;
            SnippetPrepareResult result;
            for (TaskParamsYaml.SnippetConfig preSnippetConfig : taskParamYaml.taskYaml.preSnippets) {
                result = prepareSnippet(task.launchpadUrl, launchpadInfo, preSnippetConfig);
                if (result.isError) {
                    markSnippetAsFinishedWithPermanentError(task.launchpadUrl, task.taskId, result);
                    isNotReady = true;
                    break;
                }
                if (!result.isLoaded || !isAllLoaded) {
                    isNotReady = true;
                    break;
                }
                results[idx++] = result;
            }
            if (isNotReady) {
                continue;
            }

            result = prepareSnippet(task.launchpadUrl, launchpadInfo, taskParamYaml.taskYaml.getSnippet());
            if (result.isError) {
                markSnippetAsFinishedWithPermanentError(task.launchpadUrl, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.SnippetConfig postSnippetConfig : taskParamYaml.taskYaml.postSnippets) {
                result = prepareSnippet(task.launchpadUrl, launchpadInfo, postSnippetConfig);
                if (result.isError) {
                    markSnippetAsFinishedWithPermanentError(task.launchpadUrl, task.taskId, result);
                    isNotReady = true;
                    break;
                }
                if (!result.isLoaded) {
                    isNotReady = true;
                    break;
                }
                results[idx++] = result;
            }
            if (isNotReady) {
                continue;
            }

            if (!prepareParamsFileForTask(taskDir, taskParamYaml, results)) {
                continue;
            }

            // at this point all required resources have to be prepared
            task = stationTaskService.setLaunchOn(task.launchpadUrl, task.taskId);
            try {
                taskProcessorStateService.currentTaskId = task.taskId;
                execAllSnippets(task, launchpadInfo, launchpad, taskDir, taskParamYaml, artifactDir, systemDir, results);
            }
            catch(ScheduleInactivePeriodException e) {
                stationTaskService.resetTask(task.launchpadUrl, task.taskId);
                stationTaskService.delete(task.launchpadUrl, task.taskId);
                log.info("An execution of task #{} was terminated because of the beginning of inactivity period. " +
                        "This task will be processed later", task.taskId);
            }
            finally {
                taskProcessorStateService.currentTaskId = null;
            }
        }
    }

    private void markSnippetAsFinishedWithPermanentError(String launchpadUrl, Long taskId, SnippetPrepareResult result) {
        SnippetApiData.SnippetExecResult execResult = new SnippetApiData.SnippetExecResult(
                result.getSnippet().code, false, -990,
                "#100.105 Snippet "+result.getSnippet().code+" has permanent error: " + result.getSnippetExecResult().console);
        stationTaskService.markAsFinished(launchpadUrl, taskId,
                new SnippetApiData.SnippetExec(null, null, null, execResult));

    }

    private void execAllSnippets(
            StationTask task, Metadata.LaunchpadInfo launchpadInfo,
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            File taskDir, TaskParamsYaml taskParamYaml, File artifactDir,
            File systemDir, SnippetPrepareResult[] results) {
        List<SnippetApiData.SnippetExecResult> preSnippetExecResult = new ArrayList<>();
        List<SnippetApiData.SnippetExecResult> postSnippetExecResult = new ArrayList<>();
        boolean isOk = true;
        int idx = 0;
        LaunchpadSchedule schedule = launchpad.schedule!=null && launchpad.schedule.policy== ExtendedTimePeriod.SchedulePolicy.strict
                ? launchpad.schedule : null;
        for (TaskParamsYaml.SnippetConfig preSnippetConfig : taskParamYaml.taskYaml.preSnippets) {
            SnippetPrepareResult result = results[idx++];
            SnippetApiData.SnippetExecResult execResult;
            if (result==null) {
                execResult = new SnippetApiData.SnippetExecResult(
                        preSnippetConfig.code, false, -999,
                        "#100.110 Illegal State, result of preparing of snippet "+preSnippetConfig.code+" is null");
            }
            else {
                execResult = execSnippet(task, taskDir, taskParamYaml, systemDir, result, schedule);
            }
            preSnippetExecResult.add(execResult);
            if (!execResult.isOk) {
                isOk = false;
                break;
            }
        }
        SnippetApiData.SnippetExecResult snippetExecResult = null;
        SnippetApiData.SnippetExecResult generalExec = null;
        if (isOk) {
            SnippetPrepareResult result = results[idx++];
            if (result==null) {
                snippetExecResult = new SnippetApiData.SnippetExecResult(
                        taskParamYaml.taskYaml.getSnippet().code, false, -999,
                        "#100.120 Illegal State, result of preparing of snippet "+taskParamYaml.taskYaml.getSnippet()+" is null");
                isOk = false;
            }
            if (isOk) {
                TaskParamsYaml.SnippetConfig mainSnippetConfig = result.snippet;
                snippetExecResult = execSnippet(task, taskDir, taskParamYaml, systemDir, result, schedule);
                if (!snippetExecResult.isOk) {
                    isOk = false;
                }
                if (isOk) {
                    for (TaskParamsYaml.SnippetConfig postSnippetConfig : taskParamYaml.taskYaml.postSnippets) {
                        result = results[idx++];
                        SnippetApiData.SnippetExecResult execResult;
                        if (result==null) {
                            execResult = new SnippetApiData.SnippetExecResult(
                                    postSnippetConfig.code, false, -999,
                                    "#100.130 Illegal State, result of preparing of snippet "+postSnippetConfig.code+" is null");
                        }
                        else {
                            execResult = execSnippet(task, taskDir, taskParamYaml, systemDir, result, schedule);
                        }
                        postSnippetExecResult.add(execResult);
                        if (!execResult.isOk) {
                            isOk = false;
                            break;
                        }
                    }
                    if (isOk && snippetExecResult.isOk()) {
                        try {
                            stationTaskService.storeMetrics(task.launchpadUrl, task, mainSnippetConfig, artifactDir);
                            stationTaskService.storePredictedData(task.launchpadUrl, task, mainSnippetConfig, artifactDir);
                            stationTaskService.storeFittingCheck(task.launchpadUrl, task, mainSnippetConfig, artifactDir);

                            final DataStorageParams params = taskParamYaml.taskYaml.resourceStorageUrls
                                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
                            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                            generalExec = resourceProvider.processResultingFile(
                                    launchpad, task, launchpadInfo,
                                    taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(),
                                    mainSnippetConfig
                            );
                        }
                        catch (Throwable th) {
                            generalExec = new SnippetApiData.SnippetExecResult(
                                    mainSnippetConfig.code, false, -997,
                                    "#100.132 Error storing snippet's result, error: " + th.getMessage());

                        }
                    }
                }
            }
        }

        stationTaskService.markAsFinished(task.launchpadUrl, task.getTaskId(),
                new SnippetApiData.SnippetExec(snippetExecResult, preSnippetExecResult, postSnippetExecResult, generalExec));
    }

    private boolean prepareParamsFileForTask(File taskDir, TaskParamsYaml taskParamYaml, SnippetPrepareResult[] results) {

        File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return false;
        }

        Set<Integer> versions = Stream.of(results)
                .map(o-> SnippetCoreUtils.getTaskParamsVersion(o.snippet.metas))
                .collect(Collectors.toSet());

        taskParamYaml.taskYaml.workingPath = taskDir.getAbsolutePath();
        for (Integer version : versions) {

            final String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(taskParamYaml, version);

            // persist params.yaml file
            File paramFile = new File(artifactDir, String.format(Consts.PARAMS_YAML_MASK, version));
            if (paramFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                paramFile.delete();
            }

            try {
                FileUtils.writeStringToFile(paramFile, params, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("#100.140 Error with writing to " + paramFile.getAbsolutePath() + " file", e);
                return false;
            }
        }
        return true;
    }

    private int totalCountOfSnippets(TaskParamsYaml.TaskYaml taskYaml) {
        int count = 0;
        count += (taskYaml.preSnippets!=null) ? taskYaml.preSnippets.size() : 0;
        count += (taskYaml.postSnippets!=null) ? taskYaml.postSnippets.size() : 0;
        count += (taskYaml.snippet!=null) ? 1 : 0;
        return count;
    }

    @SuppressWarnings({"WeakerAccess", "deprecation"})
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetApiData.SnippetExecResult execSnippet(
            StationTask task, File taskDir, TaskParamsYaml taskParamYaml, File systemDir, SnippetPrepareResult snippetPrepareResult,
            LaunchpadSchedule schedule) {

        File paramFile = new File(
                taskDir,
                ConstsApi.ARTIFACTS_DIR + File.separatorChar +
                        String.format(Consts.PARAMS_YAML_MASK, SnippetCoreUtils.getTaskParamsVersion(snippetPrepareResult.snippet.metas)));

        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(snippetPrepareResult.snippet.env)) {
            interpreter = new Interpreter(envService.getEnvYaml().getEnvs().get(snippetPrepareResult.snippet.env));
            if (interpreter.list == null) {
                log.warn("#100.150 Can't process the task, the interpreter wasn't found for env: {}", snippetPrepareResult.snippet.env);
                return null;
            }
            cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());
        }
        else {
            // snippet.file is executable file
            cmd = new ArrayList<>();
        }

        log.info("All systems are checked for the task #{}, lift off", task.taskId );

        SnippetApiData.SnippetExecResult snippetExecResult;
        try {
            switch (snippetPrepareResult.snippet.sourcing) {
                case launchpad:
                case git:
                    if (snippetPrepareResult.snippetAssetFile==null) {
                        throw new IllegalStateException("#100.160 snippetAssetFile is null");
                    }
                    cmd.add(snippetPrepareResult.snippetAssetFile.file.getAbsolutePath());
                    break;
                case station:
                    if (snippetPrepareResult.snippet.file!=null) {
                        //noinspection UseBulkOperation
                        Arrays.stream(StringUtils.split(snippetPrepareResult.snippet.file)).forEachOrdered(cmd::add);
                    }
                    break;
                default:
                    throw new IllegalStateException("#100.170 Unknown sourcing: "+snippetPrepareResult.snippet.sourcing );
            }

            if (!snippetPrepareResult.snippet.skipParams) {
                if (StringUtils.isNoneBlank(snippetPrepareResult.snippet.params)) {
                    final Meta meta = MetaUtils.getMeta(snippetPrepareResult.snippet.metas,
                            ConstsApi.META_MH_SNIPPET_PARAMS_AS_FILE_META,
                            Consts.META_SNIPPET_PARAMS_AS_FILE_META);
                    if (MetaUtils.isTrue(meta)) {
                        final Meta metaExt = MetaUtils.getMeta(snippetPrepareResult.snippet.metas,
                                ConstsApi.META_MH_SNIPPET_PARAMS_FILE_EXT_META,
                                Consts.META_SNIPPET_PARAMS_FILE_EXT_META);
                        String ext = (metaExt!=null && metaExt.value!=null && !metaExt.value.isBlank())
                                ? metaExt.value : ".txt";

                        File execFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + toFilename(snippetPrepareResult.snippet.code) + ext);
                        FileUtils.writeStringToFile(execFile, snippetPrepareResult.snippet.params, StandardCharsets.UTF_8 );
                        cmd.add( execFile.getAbsolutePath() );
                    }
                    else {
                        cmd.addAll(Arrays.asList(StringUtils.split(snippetPrepareResult.snippet.params)));
                    }
                }
                cmd.add(paramFile.getAbsolutePath());
            }

            File consoleLogFile = new File(systemDir, Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            // Exec snippet
            snippetExecResult = execProcessService.execCommand(
                    cmd, taskDir, consoleLogFile, taskParamYaml.taskYaml.timeoutBeforeTerminate, snippetPrepareResult.snippet.code, schedule);

        }
        catch (ScheduleInactivePeriodException e) {
            throw e;
        }
        catch (Throwable th) {
            log.error("#100.180 Error exec process:\n" +
                    "\tenv: " + snippetPrepareResult.snippet.env +"\n" +
                    "\tinterpreter: " + interpreter+"\n" +
                    "\tfile: " + (snippetPrepareResult.snippetAssetFile!=null && snippetPrepareResult.snippetAssetFile.file!=null
                    ? snippetPrepareResult.snippetAssetFile.file.getAbsolutePath()
                    : snippetPrepareResult.snippet.file) +"\n" +
                    "\tparams", th);
            snippetExecResult = new SnippetApiData.SnippetExecResult(
                    snippetPrepareResult.snippet.code, false, -1, ExceptionUtils.getStackTrace(th));
        }
        return snippetExecResult;
    }

    private String toFilename(String snippetCode) {
        return snippetCode.replace(':', '_').replace(' ', '_');
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public SnippetPrepareResult prepareSnippet(String launchpadUrl, Metadata.LaunchpadInfo launchpadCode, TaskParamsYaml.SnippetConfig snippet) {
        SnippetPrepareResult snippetPrepareResult = new SnippetPrepareResult();
        snippetPrepareResult.snippet = snippet;

        if (snippetPrepareResult.snippet.sourcing== EnumsApi.SnippetSourcing.launchpad) {
            final File snippetDir = launchpadLookupExtendedService.prepareBaseResourceDir(launchpadCode);
            snippetPrepareResult.snippetAssetFile = ResourceUtils.prepareSnippetFile(snippetDir, snippetPrepareResult.snippet.getCode(), snippetPrepareResult.snippet.file);
            // is this snippet prepared?
            if (snippetPrepareResult.snippetAssetFile.isError || !snippetPrepareResult.snippetAssetFile.isContent) {
                log.info("Snippet {} hasn't been prepared yet, {}", snippetPrepareResult.snippet.code, snippetPrepareResult.snippetAssetFile);
                snippetPrepareResult.isLoaded = false;

                metadataService.setSnippetDownloadStatus(launchpadUrl, snippet.code, EnumsApi.SnippetSourcing.launchpad, Enums.SnippetState.none);

            }
        }
        else if (snippetPrepareResult.snippet.sourcing==EnumsApi.SnippetSourcing.git) {
            final File snippetRootDir = launchpadLookupExtendedService.prepareBaseResourceDir(launchpadCode);
            log.info("Root dir for snippet: " + snippetRootDir);
            GitSourcingService.GitExecResult result = gitSourcingService.prepareSnippet(snippetRootDir, snippetPrepareResult.snippet);
            if (!result.ok) {
                log.warn("Snippet {} has a permanent error, {}", snippetPrepareResult.snippet.code, result.error);
                snippetPrepareResult.snippetExecResult = new SnippetApiData.SnippetExecResult(snippet.code, false, -1, result.error);
                snippetPrepareResult.isLoaded = false;
                snippetPrepareResult.isError = true;
                return snippetPrepareResult;
            }
            snippetPrepareResult.snippetAssetFile = new AssetFile();
            snippetPrepareResult.snippetAssetFile.file = new File(result.snippetDir, snippetPrepareResult.snippet.file);
            log.info("Snippet asset file: {}, exist: {}", snippetPrepareResult.snippetAssetFile.file.getAbsolutePath(), snippetPrepareResult.snippetAssetFile.file.exists() );
        }
        return snippetPrepareResult;
    }
}