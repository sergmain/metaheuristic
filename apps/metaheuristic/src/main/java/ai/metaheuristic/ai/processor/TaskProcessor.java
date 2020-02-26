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
package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.core.SystemProcessService;
import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.processor_resource.ResourceProvider;
import ai.metaheuristic.ai.processor.processor_resource.ResourceProviderFactory;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.ExtendedTimePeriod;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
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
@Profile("processor")
@RequiredArgsConstructor
public class TaskProcessor {

    private final Globals globals;

    private final SystemProcessService systemProcessService;
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final MetadataService metadataService;
    private final EnvService envService;
    private final ProcessorService processorService;
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
        if (!globals.processorEnabled) {
            return;
        }

        // find all tasks which weren't completed and  weren't finished and resources aren't prepared yet
        List<ProcessorTask> tasks = processorTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(true);
        for (ProcessorTask task : tasks) {

            if (task.launchedOn!=null && task.finishedOn!=null && currentTaskId==null) {
                log.warn("#100.001 unusual situation, there isn't any processed task (currentTaskId==null) but task #{} was already launched and then finished", task.taskId);
            }
            if (StringUtils.isBlank(task.dispatcherUrl)) {
                final String es = "#100.005 task.dispatcherUrl is blank for task #" + task.taskId;
                log.warn(es);
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }

            final Metadata.DispatcherInfo dispatcherInfo = metadataService.dispatcherUrlAsCode(task.dispatcherUrl);
            if (dispatcherInfo ==null) {
                final String es = "#100.010 dispatcherInfo is null for "+task.dispatcherUrl+". task #" + task.taskId;
                log.warn(es);
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }

            DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher = dispatcherLookupExtendedService.lookupExtendedMap.get(task.dispatcherUrl);
            if (dispatcher==null) {
                final String es = "#100.020 Broken task #"+task.taskId+". dispatcher wasn't found for url " + task.dispatcherUrl;
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }

            if (dispatcher.schedule.isCurrentTimeInactive()) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("Can't process task #{} for url {} at this time, time: {}, permitted period of time: {}", task.taskId, task.dispatcherUrl, new Date(), dispatcher.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("#100.030 Params for task #{} is blank", task.getTaskId());
                continue;
            }

            EnumsApi.ExecContextState state = currentExecState.getState(task.dispatcherUrl, task.execContextId);
            if (state== EnumsApi.ExecContextState.UNKNOWN) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("The state for ExecContext #{}, host {} is unknown, delete a task #{}", task.execContextId, task.dispatcherUrl, task.taskId);
                continue;
            }

            if (state!= EnumsApi.ExecContextState.STARTED) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("The state for ExecContext #{}, host: {}, is {}, delete a task #{}", task.execContextId, task.dispatcherUrl, state, task.taskId);
                continue;
            }

            log.info("Start processing task {}", task);
            File taskDir = processorTaskService.prepareTaskDir(task.dispatcherUrl, task.taskId);

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

            ProcessorService.ResultOfChecking resultOfChecking = processorService.checkForPreparingOfAssets(task, dispatcherInfo, taskParamYaml, dispatcher, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            File outputResourceFile = processorService.getOutputResourceFile(task, taskParamYaml, dispatcher, taskDir);
            if (outputResourceFile==null) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.040 Broken task. Can't create outputResourceFile");
                continue;
            }
            if (taskParamYaml.taskYaml.output.isEmpty()) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.050 Broken task. output variable must be specified");
                continue;
            }
            if (taskParamYaml.taskYaml.function ==null) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.080 Broken task. Function isn't defined");
                continue;
            }

            File artifactDir = processorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
            if (artifactDir == null) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.090 Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            File systemDir = processorTaskService.prepareTaskSubDir(taskDir, Consts.SYSTEM_DIR);
            if (systemDir == null) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.100 Error of configuring of environment. 'system' directory wasn't created, task can't be processed.");
                continue;
            }

            String status = processorTaskService.prepareEnvironment(artifactDir);
            if (status!=null) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, status);
            }

            boolean isNotReady = false;
            final FunctionPrepareResult[] results = new FunctionPrepareResult[ totalCountOfFunctions(taskParamYaml.taskYaml) ];
            int idx = 0;
            FunctionPrepareResult result;
            for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.taskYaml.preFunctions) {
                result = prepareFunction(task.dispatcherUrl, dispatcherInfo, preFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(task.dispatcherUrl, task.taskId, result);
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

            result = prepareFunction(task.dispatcherUrl, dispatcherInfo, taskParamYaml.taskYaml.getFunction());
            if (result.isError) {
                markFunctionAsFinishedWithPermanentError(task.dispatcherUrl, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.taskYaml.postFunctions) {
                result = prepareFunction(task.dispatcherUrl, dispatcherInfo, postFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(task.dispatcherUrl, task.taskId, result);
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
            task = processorTaskService.setLaunchOn(task.dispatcherUrl, task.taskId);
            try {
                currentTaskId = task.taskId;
                execAllFunctions(task, dispatcherInfo, dispatcher, taskDir, taskParamYaml, artifactDir, systemDir, results);
            }
            catch(ScheduleInactivePeriodException e) {
                processorTaskService.resetTask(task.dispatcherUrl, task.taskId);
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("An execution of task #{} was terminated because of the beginning of inactivity period. " +
                        "This task will be processed later", task.taskId);
            }
            finally {
                currentTaskId = null;
            }
        }
    }

    private void markFunctionAsFinishedWithPermanentError(String dispatcherUrl, Long taskId, FunctionPrepareResult result) {
        FunctionApiData.SystemExecResult execResult = new FunctionApiData.SystemExecResult(
                result.getFunction().code, false, -990,
                "#100.105 Function "+result.getFunction().code+" has permanent error: " + result.getSystemExecResult().console);
        processorTaskService.markAsFinished(dispatcherUrl, taskId,
                new FunctionApiData.FunctionExec(null, null, null, execResult));

    }

    private void execAllFunctions(
            ProcessorTask task, Metadata.DispatcherInfo dispatcherInfo,
            DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            File taskDir, TaskParamsYaml taskParamYaml, File artifactDir,
            File systemDir, FunctionPrepareResult[] results) {
        List<FunctionApiData.SystemExecResult> preSystemExecResult = new ArrayList<>();
        List<FunctionApiData.SystemExecResult> postSystemExecResult = new ArrayList<>();
        boolean isOk = true;
        int idx = 0;
        DispatcherSchedule schedule = dispatcher.schedule!=null && dispatcher.schedule.policy== ExtendedTimePeriod.SchedulePolicy.strict
                ? dispatcher.schedule : null;
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
                            for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.taskYaml.output) {
                                ResourceProvider resourceProvider = resourceProviderFactory.getResourceProvider(outputVariable.sourcing);
                                generalExec = resourceProvider.processResultingFile(
                                        dispatcher, task, dispatcherInfo, outputVariable.resources.id, mainFunctionConfig);
                            }
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

        processorTaskService.markAsFinished(task.dispatcherUrl, task.getTaskId(),
                new FunctionApiData.FunctionExec(systemExecResult, preSystemExecResult, postSystemExecResult, generalExec));
    }

    private boolean prepareParamsFileForTask(File taskDir, TaskParamsYaml taskParamYaml, FunctionPrepareResult[] results) {

        File artifactDir = processorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return false;
        }

        Set<Integer> versions = Stream.of(results)
                .map(o-> FunctionCoreUtils.getTaskParamsVersion(o.function.metas))
                .collect(Collectors.toSet());

        TaskFileParamsYaml taskFileParamYaml = toTaskFileParamsYaml(taskParamYaml);
        taskFileParamYaml.task.workingPath = taskDir.getAbsolutePath();
        for (Integer version : versions) {

            final String params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(taskFileParamYaml, version);

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

    private TaskFileParamsYaml toTaskFileParamsYaml(TaskParamsYaml v1) {
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        t.task.execContextId = v1.taskYaml.execContextId;
        t.task.clean = v1.taskYaml.clean;
        t.task.timeoutBeforeTerminate = v1.taskYaml.timeoutBeforeTerminate;
        t.task.workingPath = v1.taskYaml.workingPath;

        t.task.inline = v1.taskYaml.inline;
        t.task.input = v1.taskYaml.input!=null ? v1.taskYaml.input.stream().map(TaskProcessor::upInputVariable).collect(Collectors.toList()) : null;
        t.task.output.addAll(v1.taskYaml.output.stream().map(TaskProcessor::upOutputVariable).collect(Collectors.toList()));

        t.checkIntegrity();
        return t;
    }

    private static TaskFileParamsYaml.InputVariable  upInputVariable(TaskParamsYaml.InputVariable v1) {
        TaskFileParamsYaml.InputVariable  v = new TaskFileParamsYaml.InputVariable ();
        v.disk = v1.disk;
        v.git = v1.git;
        v.name = v1.name;
        v.sourcing = v1.sourcing;
        v.resources = v1.resources!=null ? v1.resources.stream().map(r->new TaskFileParamsYaml.Resource(r.id, r.context, r.realName)).collect(Collectors.toList()) : null;
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskParamsYaml.OutputVariable v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.disk = v1.disk;
        v.git = v1.git;
        v.name = v1.name;
        v.sourcing = v1.sourcing;
        v.resources = new TaskFileParamsYaml.Resource(v1.resources.id, v1.resources.context, v1.resources.realName);
        return v;
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
            ProcessorTask task, File taskDir, TaskParamsYaml taskParamYaml, File systemDir, FunctionPrepareResult functionPrepareResult,
            DispatcherSchedule schedule) {

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
                case dispatcher:
                case git:
                    if (functionPrepareResult.functionAssetFile ==null) {
                        throw new IllegalStateException("#100.160 functionAssetFile is null");
                    }
                    cmd.add(functionPrepareResult.functionAssetFile.file.getAbsolutePath());
                    break;
                case processor:
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
    public FunctionPrepareResult prepareFunction(String dispatcherUrl, Metadata.DispatcherInfo dispatcherCode, TaskParamsYaml.FunctionConfig function) {
        FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
        functionPrepareResult.function = function;

        if (functionPrepareResult.function.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            final File baseResourceDir = dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherCode);
            functionPrepareResult.functionAssetFile = ResourceUtils.prepareFunctionFile(baseResourceDir, functionPrepareResult.function.getCode(), functionPrepareResult.function.file);
            // is this function prepared?
            if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
                log.info("Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
                functionPrepareResult.isLoaded = false;

                metadataService.setFunctionDownloadStatus(dispatcherUrl, function.code, EnumsApi.FunctionSourcing.dispatcher, Enums.FunctionState.none);

            }
        }
        else if (functionPrepareResult.function.sourcing== EnumsApi.FunctionSourcing.git) {
            final File resourceDir = dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherCode);
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