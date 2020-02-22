/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.core.SystemProcessService;
import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.env.EnvService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.station.station_resource.ResourceProvider;
import ai.metaheuristic.ai.station.station_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.yaml.mh.dispatcher._lookup.ExtendedTimePeriod;
import ai.metaheuristic.ai.yaml.mh.dispatcher._lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
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

    private final SystemProcessService systemProcessService;
    private final StationTaskService stationTaskService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService;
    private final MetadataService metadataService;
    private final EnvService envService;
    private final StationService stationService;
    private final ResourceProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    private Long currentTaskId;

    @Data
    public static class FunctionPrepareResult {
        public TaskParamsYaml.FunctionConfig function;
        public AssetFile functionAssetFile;
        public FunctionApiData.SystemExecResult systemExecResult;
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

            if (task.launchedOn!=null && task.finishedOn!=null && currentTaskId==null) {
                log.warn("#100.001 unusual situation, there isn't any processed task (currentTaskId==null) but task #{} was already launched and then finished", task.taskId);
            }
            if (StringUtils.isBlank(task.mh.dispatcher.Url)) {
                final String es = "#100.005 task.mh.dispatcher.Url is blank for task #" + task.taskId;
                log.warn(es);
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, es);
                continue;
            }

            final Metadata.DispatcherInfo mh.dispatcher.Info = metadataService.mh.dispatcher.UrlAsCode(task.mh.dispatcher.Url);
            if (mh.dispatcher.Info ==null) {
                final String es = "#100.010 mh.dispatcher.Info is null for "+task.mh.dispatcher.Url+". task #" + task.taskId;
                log.warn(es);
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, es);
                continue;
            }

            DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher. = mh.dispatcher.LookupExtendedService.lookupExtendedMap.get(task.mh.dispatcher.Url);
            if (mh.dispatcher.==null) {
                final String es = "#100.020 Broken task #"+task.taskId+". mh.dispatcher. wasn't found for url " + task.mh.dispatcher.Url;
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, es);
                continue;
            }

            if (mh.dispatcher..schedule.isCurrentTimeInactive()) {
                stationTaskService.delete(task.mh.dispatcher.Url, task.taskId);
                log.info("Can't process task #{} for url {} at this time, time: {}, permitted period of time: {}", task.taskId, task.mh.dispatcher.Url, new Date(), mh.dispatcher..schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("#100.030 Params for task #{} is blank", task.getTaskId());
                continue;
            }

            EnumsApi.ExecContextState state = currentExecState.getState(task.mh.dispatcher.Url, task.execContextId);
            if (state== EnumsApi.ExecContextState.UNKNOWN) {
                stationTaskService.delete(task.mh.dispatcher.Url, task.taskId);
                log.info("The state for ExecContext #{}, host {} is unknown, delete a task #{}", task.execContextId, task.mh.dispatcher.Url, task.taskId);
                continue;
            }

            if (state!= EnumsApi.ExecContextState.STARTED) {
                stationTaskService.delete(task.mh.dispatcher.Url, task.taskId);
                log.info("The state for ExecContext #{}, host: {}, is {}, delete a task #{}", task.execContextId, task.mh.dispatcher.Url, state, task.taskId);
                continue;
            }

            log.info("Start processing task {}", task);
            File taskDir = stationTaskService.prepareTaskDir(task.mh.dispatcher.Url, task.taskId);

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

            StationService.ResultOfChecking resultOfChecking = stationService.checkForPreparingOfAssets(task, mh.dispatcher.Info, taskParamYaml, mh.dispatcher., taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            File outputResourceFile = stationService.getOutputResourceFile(task, taskParamYaml, mh.dispatcher., taskDir);
            if (outputResourceFile==null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, "#100.040 Broken task. Can't create outputResourceFile");
                continue;
            }
            SourceCodeParamsYaml.Variable dsp = taskParamYaml.taskYaml.getResourceStorageUrls()
                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
            if (dsp==null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, "#100.050 Broken task. Can't find params for outputResourceCode");
                continue;
            }
            if (taskParamYaml.taskYaml.function ==null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, "#100.080 Broken task. Function isn't defined");
                continue;
            }

            File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
            if (artifactDir == null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, "#100.090 Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            File systemDir = stationTaskService.prepareTaskSubDir(taskDir, Consts.SYSTEM_DIR);
            if (systemDir == null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, "#100.100 Error of configuring of environment. 'system' directory wasn't created, task can't be processed.");
                continue;
            }

            String status = stationTaskService.prepareEnvironment(artifactDir);
            if (status!=null) {
                stationTaskService.markAsFinishedWithError(task.mh.dispatcher.Url, task.taskId, status);
            }

            boolean isNotReady = false;
            final FunctionPrepareResult[] results = new FunctionPrepareResult[ totalCountOfFunctions(taskParamYaml.taskYaml) ];
            int idx = 0;
            FunctionPrepareResult result;
            for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.taskYaml.preFunctions) {
                result = prepareFunction(task.mh.dispatcher.Url, mh.dispatcher.Info, preFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(task.mh.dispatcher.Url, task.taskId, result);
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

            result = prepareFunction(task.mh.dispatcher.Url, mh.dispatcher.Info, taskParamYaml.taskYaml.getFunction());
            if (result.isError) {
                markFunctionAsFinishedWithPermanentError(task.mh.dispatcher.Url, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.taskYaml.postFunctions) {
                result = prepareFunction(task.mh.dispatcher.Url, mh.dispatcher.Info, postFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(task.mh.dispatcher.Url, task.taskId, result);
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
            task = stationTaskService.setLaunchOn(task.mh.dispatcher.Url, task.taskId);
            try {
                currentTaskId = task.taskId;
                execAllFunctions(task, mh.dispatcher.Info, mh.dispatcher., taskDir, taskParamYaml, artifactDir, systemDir, results);
            }
            catch(ScheduleInactivePeriodException e) {
                stationTaskService.resetTask(task.mh.dispatcher.Url, task.taskId);
                stationTaskService.delete(task.mh.dispatcher.Url, task.taskId);
                log.info("An execution of task #{} was terminated because of the beginning of inactivity period. " +
                        "This task will be processed later", task.taskId);
            }
            finally {
                currentTaskId = null;
            }
        }
    }

    private void markFunctionAsFinishedWithPermanentError(String mh.dispatcher.Url, Long taskId, FunctionPrepareResult result) {
        FunctionApiData.SystemExecResult execResult = new FunctionApiData.SystemExecResult(
                result.getFunction().code, false, -990,
                "#100.105 Function "+result.getFunction().code+" has permanent error: " + result.getSystemExecResult().console);
        stationTaskService.markAsFinished(mh.dispatcher.Url, taskId,
                new FunctionApiData.FunctionExec(null, null, null, execResult));

    }

    private void execAllFunctions(
            StationTask task, Metadata.DispatcherInfo mh.dispatcher.Info,
            DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher.,
            File taskDir, TaskParamsYaml taskParamYaml, File artifactDir,
            File systemDir, FunctionPrepareResult[] results) {
        List<FunctionApiData.SystemExecResult> preSystemExecResult = new ArrayList<>();
        List<FunctionApiData.SystemExecResult> postSystemExecResult = new ArrayList<>();
        boolean isOk = true;
        int idx = 0;
        LaunchpadSchedule schedule = mh.dispatcher..schedule!=null && mh.dispatcher..schedule.policy== ExtendedTimePeriod.SchedulePolicy.strict
                ? mh.dispatcher..schedule : null;
        for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.taskYaml.preFunctions) {
            FunctionPrepareResult result = results[idx++];
            FunctionApiData.SystemExecResult execResult;
            if (result==null) {
                execResult = new FunctionApiData.SystemExecResult(
                        preFunctionConfig.code, false, -999,
                        "#100.110 Illegal State, result of preparing of function "+ preFunctionConfig.code+" is null");
            }
            else {
                execResult = execFunction(task, taskDir, taskParamYaml, systemDir, result, schedule);
            }
            preSystemExecResult.add(execResult);
            if (!execResult.isOk) {
                isOk = false;
                break;
            }
        }
        FunctionApiData.SystemExecResult systemExecResult = null;
        FunctionApiData.SystemExecResult generalExec = null;
        if (isOk) {
            FunctionPrepareResult result = results[idx++];
            if (result==null) {
                systemExecResult = new FunctionApiData.SystemExecResult(
                        taskParamYaml.taskYaml.getFunction().code, false, -999,
                        "#100.120 Illegal State, result of preparing of function "+taskParamYaml.taskYaml.getFunction()+" is null");
                isOk = false;
            }
            if (isOk) {
                TaskParamsYaml.FunctionConfig mainFunctionConfig = result.function;
                systemExecResult = execFunction(task, taskDir, taskParamYaml, systemDir, result, schedule);
                if (!systemExecResult.isOk) {
                    isOk = false;
                }
                if (isOk) {
                    for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.taskYaml.postFunctions) {
                        result = results[idx++];
                        FunctionApiData.SystemExecResult execResult;
                        if (result==null) {
                            execResult = new FunctionApiData.SystemExecResult(
                                    postFunctionConfig.code, false, -999,
                                    "#100.130 Illegal State, result of preparing of function "+ postFunctionConfig.code+" is null");
                        }
                        else {
                            execResult = execFunction(task, taskDir, taskParamYaml, systemDir, result, schedule);
                        }
                        postSystemExecResult.add(execResult);
                        if (!execResult.isOk) {
                            isOk = false;
                            break;
                        }
                    }
                    if (isOk && systemExecResult.isOk()) {
                        try {
                            stationTaskService.storeMetrics(task.mh.dispatcher.Url, task, mainFunctionConfig, artifactDir);
                            stationTaskService.storePredictedData(task.mh.dispatcher.Url, task, mainFunctionConfig, artifactDir);
                            stationTaskService.storeFittingCheck(task.mh.dispatcher.Url, task, mainFunctionConfig, artifactDir);

                            final SourceCodeParamsYaml.Variable params = taskParamYaml.taskYaml.resourceStorageUrls
                                    .get(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next());
                            ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(params.sourcing);
                            generalExec = resourceProvider.processResultingFile(
                                    mh.dispatcher., task, mh.dispatcher.Info,
                                    taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(),
                                    mainFunctionConfig
                            );
                        }
                        catch (Throwable th) {
                            generalExec = new FunctionApiData.SystemExecResult(
                                    mainFunctionConfig.code, false, -997,
                                    "#100.132 Error storing function's result, error: " + th.getMessage());

                        }
                    }
                }
            }
        }

        stationTaskService.markAsFinished(task.mh.dispatcher.Url, task.getTaskId(),
                new FunctionApiData.FunctionExec(systemExecResult, preSystemExecResult, postSystemExecResult, generalExec));
    }

    private boolean prepareParamsFileForTask(File taskDir, TaskParamsYaml taskParamYaml, FunctionPrepareResult[] results) {

        File artifactDir = stationTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return false;
        }

        Set<Integer> versions = Stream.of(results)
                .map(o-> FunctionCoreUtils.getTaskParamsVersion(o.function.metas))
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

    private int totalCountOfFunctions(TaskParamsYaml.TaskYaml taskYaml) {
        int count = 0;
        count += (taskYaml.preFunctions !=null) ? taskYaml.preFunctions.size() : 0;
        count += (taskYaml.postFunctions !=null) ? taskYaml.postFunctions.size() : 0;
        count += (taskYaml.function !=null) ? 1 : 0;
        return count;
    }

    @SuppressWarnings({"WeakerAccess", "deprecation"})
    // TODO 2019.05.02 implement unit-test for this method
    public FunctionApiData.SystemExecResult execFunction(
            StationTask task, File taskDir, TaskParamsYaml taskParamYaml, File systemDir, FunctionPrepareResult functionPrepareResult,
            LaunchpadSchedule schedule) {

        File paramFile = new File(
                taskDir,
                ConstsApi.ARTIFACTS_DIR + File.separatorChar +
                        String.format(Consts.PARAMS_YAML_MASK, FunctionCoreUtils.getTaskParamsVersion(functionPrepareResult.function.metas)));

        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(functionPrepareResult.function.env)) {
            interpreter = new Interpreter(envService.getEnvYaml().getEnvs().get(functionPrepareResult.function.env));
            if (interpreter.list == null) {
                log.warn("#100.150 Can't process the task, the interpreter wasn't found for env: {}", functionPrepareResult.function.env);
                return null;
            }
            cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());
        }
        else {
            // function.file is executable file
            cmd = new ArrayList<>();
        }

        log.info("All systems are checked for the task #{}, lift off", task.taskId );

        FunctionApiData.SystemExecResult systemExecResult;
        try {
            switch (functionPrepareResult.function.sourcing) {
                case mh.dispatcher.:
                case git:
                    if (functionPrepareResult.functionAssetFile ==null) {
                        throw new IllegalStateException("#100.160 functionAssetFile is null");
                    }
                    cmd.add(functionPrepareResult.functionAssetFile.file.getAbsolutePath());
                    break;
                case station:
                    if (functionPrepareResult.function.file!=null) {
                        //noinspection UseBulkOperation
                        Arrays.stream(StringUtils.split(functionPrepareResult.function.file)).forEachOrdered(cmd::add);
                    }
                    break;
                default:
                    throw new IllegalStateException("#100.170 Unknown sourcing: "+ functionPrepareResult.function.sourcing );
            }

            if (!functionPrepareResult.function.skipParams) {
                if (StringUtils.isNoneBlank(functionPrepareResult.function.params)) {
                    final Meta meta = MetaUtils.getMeta(functionPrepareResult.function.metas,
                            ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
                    if (MetaUtils.isTrue(meta)) {
                        final Meta metaExt = MetaUtils.getMeta(functionPrepareResult.function.metas,
                                ConstsApi.META_MH_FUNCTION_PARAMS_FILE_EXT_META);
                        String ext = (metaExt!=null && metaExt.value!=null && !metaExt.value.isBlank())
                                ? metaExt.value : ".txt";

                        File execFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + toFilename(functionPrepareResult.function.code) + ext);
                        FileUtils.writeStringToFile(execFile, functionPrepareResult.function.params, StandardCharsets.UTF_8 );
                        cmd.add( execFile.getAbsolutePath() );
                    }
                    else {
                        cmd.addAll(Arrays.asList(StringUtils.split(functionPrepareResult.function.params)));
                    }
                }
                cmd.add(paramFile.getAbsolutePath());
            }

            File consoleLogFile = new File(systemDir, Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            // Exec function
            systemExecResult = systemProcessService.execCommand(
                    cmd, taskDir, consoleLogFile, taskParamYaml.taskYaml.timeoutBeforeTerminate, functionPrepareResult.function.code, schedule);

        }
        catch (ScheduleInactivePeriodException e) {
            throw e;
        }
        catch (Throwable th) {
            log.error("#100.180 Error exec process:\n" +
                    "\tenv: " + functionPrepareResult.function.env +"\n" +
                    "\tinterpreter: " + interpreter+"\n" +
                    "\tfile: " + (functionPrepareResult.functionAssetFile !=null && functionPrepareResult.functionAssetFile.file!=null
                    ? functionPrepareResult.functionAssetFile.file.getAbsolutePath()
                    : functionPrepareResult.function.file) +"\n" +
                    "\tparams", th);
            systemExecResult = new FunctionApiData.SystemExecResult(
                    functionPrepareResult.function.code, false, -1, ExceptionUtils.getStackTrace(th));
        }
        return systemExecResult;
    }

    private String toFilename(String functionCode) {
        return functionCode.replace(':', '_').replace(' ', '_');
    }

    @SuppressWarnings("WeakerAccess")
    // TODO 2019.05.02 implement unit-test for this method
    public FunctionPrepareResult prepareFunction(String mh.dispatcher.Url, Metadata.DispatcherInfo mh.dispatcher.Code, TaskParamsYaml.FunctionConfig function) {
        FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
        functionPrepareResult.function = function;

        if (functionPrepareResult.function.sourcing== EnumsApi.FunctionSourcing.mh.dispatcher.) {
            final File baseResourceDir = mh.dispatcher.LookupExtendedService.prepareBaseResourceDir(mh.dispatcher.Code);
            functionPrepareResult.functionAssetFile = ResourceUtils.prepareFunctionFile(baseResourceDir, functionPrepareResult.function.getCode(), functionPrepareResult.function.file);
            // is this function prepared?
            if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
                log.info("Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
                functionPrepareResult.isLoaded = false;

                metadataService.setFunctionDownloadStatus(mh.dispatcher.Url, function.code, EnumsApi.FunctionSourcing.mh.dispatcher., Enums.FunctionState.none);

            }
        }
        else if (functionPrepareResult.function.sourcing== EnumsApi.FunctionSourcing.git) {
            final File resourceDir = mh.dispatcher.LookupExtendedService.prepareBaseResourceDir(mh.dispatcher.Code);
            log.info("Root dir for function: " + resourceDir);
            GitSourcingService.GitExecResult result = gitSourcingService.prepareFunction(resourceDir, functionPrepareResult.function);
            if (!result.ok) {
                log.warn("Function {} has a permanent error, {}", functionPrepareResult.function.code, result.error);
                functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1, result.error);
                functionPrepareResult.isLoaded = false;
                functionPrepareResult.isError = true;
                return functionPrepareResult;
            }
            functionPrepareResult.functionAssetFile = new AssetFile();
            functionPrepareResult.functionAssetFile.file = new File(result.functionDir, functionPrepareResult.function.file);
            log.info("Function asset file: {}, exist: {}", functionPrepareResult.functionAssetFile.file.getAbsolutePath(), functionPrepareResult.functionAssetFile.file.exists() );
        }
        return functionPrepareResult;
    }
}