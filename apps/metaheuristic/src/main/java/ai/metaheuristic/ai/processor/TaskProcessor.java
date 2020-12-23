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
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.processor.variable_providers.VariableProvider;
import ai.metaheuristic.ai.processor.variable_providers.VariableProviderFactory;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.ExtendedTimePeriod;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
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
import org.springframework.lang.Nullable;
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
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final MetadataService metadataService;
    private final EnvService envService;
    private final ProcessorService processorService;
    private final VariableProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

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
            processorTaskService.setLaunchOn(task.dispatcherUrl, task.taskId);
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
                log.info("#100.025 Can't process task #{} for url {} at this time, time: {}, permitted period of time: {}", task.taskId, task.dispatcherUrl, new Date(), dispatcher.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("#100.030 Params for task #{} is blank", task.getTaskId());
                continue;
            }

            EnumsApi.ExecContextState state = currentExecState.getState(task.dispatcherUrl, task.execContextId);
            if (state== EnumsApi.ExecContextState.UNKNOWN) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("#100.032 The state for ExecContext #{}, host {} is unknown, delete a task #{}", task.execContextId, task.dispatcherUrl, task.taskId);
                continue;
            }

            if (state!= EnumsApi.ExecContextState.STARTED) {
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("#100.034 The state for ExecContext #{}, host: {}, is {}, delete a task #{}", task.execContextId, task.dispatcherUrl, state, task.taskId);
                continue;
            }

            log.info("Start processing task {}", task);
            File taskDir = processorTaskService.prepareTaskDir(task.dispatcherUrl, task.taskId);

            final TaskParamsYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            } catch (CheckIntegrityFailedException e) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.037 Broken task. Check of integrity was failed.");
                continue;
            }

            ProcessorService.ResultOfChecking resultOfChecking = processorService.checkForPreparingOVariables(task, dispatcherInfo, taskParamYaml, dispatcher, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            if (!processorService.checkOutputResourceFile(task, taskParamYaml, dispatcher, taskDir)) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.040 Broken task. Can't create outputResourceFile");
                continue;
            }
            if (taskParamYaml.task.outputs.isEmpty()) {
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, "#100.050 Broken task. output variable must be specified");
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
            final FunctionPrepareResult[] results = new FunctionPrepareResult[ totalCountOfFunctions(taskParamYaml.task) ];
            int idx = 0;
            FunctionPrepareResult result;
            for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
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

            result = prepareFunction(task.dispatcherUrl, dispatcherInfo, taskParamYaml.task.getFunction());
            if (result.isError) {
                markFunctionAsFinishedWithPermanentError(task.dispatcherUrl, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.task.postFunctions) {
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

            try {
                if (!prepareParamsFileForTask(taskDir, taskParamYaml, results)) {
                    continue;
                }
            } catch (Throwable th) {
                String es = "#100.110 Error while preparing params.yaml file for task #"+task.taskId+", error: " + th.getMessage();
                log.warn(es);
                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }

            // at this point all required resources have to be prepared
            ProcessorTask taskResult = processorTaskService.setLaunchOn(task.dispatcherUrl, task.taskId);
            if (taskResult==null) {
                String es = "#100.120 Task #"+task.taskId+" wasn't found";
                log.warn(es);
                // there isn't this task any more. So we can't mark it as Finished
//                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }
            try {
                execAllFunctions(task, dispatcherInfo, dispatcher, taskDir, taskParamYaml, artifactDir, systemDir, results);
            }
            catch(ScheduleInactivePeriodException e) {
                processorTaskService.resetTask(task.dispatcherUrl, task.taskId);
                processorTaskService.delete(task.dispatcherUrl, task.taskId);
                log.info("#100.130 An execution of task #{} was terminated because of the beginning of inactivity period. " +
                        "This task will be processed later", task.taskId);
            }
        }
    }

    private void markFunctionAsFinishedWithPermanentError(String dispatcherUrl, Long taskId, FunctionPrepareResult result) {
        FunctionApiData.SystemExecResult execResult = new FunctionApiData.SystemExecResult(
                result.getFunction().code, false, -990,
                "#100.150 Function "+result.getFunction().code+" has a permanent error: " + result.getSystemExecResult().console);
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
        for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
            FunctionPrepareResult result = results[idx++];
            FunctionApiData.SystemExecResult execResult;
            if (result==null) {
                execResult = new FunctionApiData.SystemExecResult(
                        preFunctionConfig.code, false, -999,
                        "#100.170 Illegal State, result of preparing of function "+ preFunctionConfig.code+" is null");
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
                        taskParamYaml.task.getFunction().code, false, -999,
                        "#100.190 Illegal State, result of preparing of function "+taskParamYaml.task.getFunction()+" is null");
                isOk = false;
            }
            if (isOk) {
                TaskParamsYaml.FunctionConfig mainFunctionConfig = result.function;
                systemExecResult = execFunction(task, taskDir, taskParamYaml, systemDir, result, schedule);
                if (!systemExecResult.isOk) {
                    isOk = false;
                }
                if (isOk) {
                    for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.task.postFunctions) {
                        result = results[idx++];
                        FunctionApiData.SystemExecResult execResult;
                        if (result==null) {
                            execResult = new FunctionApiData.SystemExecResult(
                                    postFunctionConfig.code, false, -999,
                                    "#100.210 Illegal State, result of preparing of function "+ postFunctionConfig.code+" is null");
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
                            for (TaskParamsYaml.OutputVariable outputVariable : taskParamYaml.task.outputs) {
                                VariableProvider resourceProvider = resourceProviderFactory.getVariableProvider(outputVariable.sourcing);
                                generalExec = resourceProvider.processOutputVariable(
                                        taskDir, dispatcher, task, dispatcherInfo, outputVariable, mainFunctionConfig);
                            }
                        }
                        catch (Throwable th) {
                            generalExec = new FunctionApiData.SystemExecResult(
                                    mainFunctionConfig.code, false, -997,
                                    "#100.230 Error storing function's result, error: " + th.getMessage());

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
                log.error("#100.250 Error with writing to " + paramFile.getAbsolutePath() + " file", e);
                return false;
            }
        }
        return true;
    }

    private TaskFileParamsYaml toTaskFileParamsYaml(TaskParamsYaml v1) {
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        t.task.execContextId = v1.task.execContextId;
        t.task.clean = v1.task.clean;
        t.task.workingPath = v1.task.workingPath;

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskProcessor::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskProcessor::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));

        t.checkIntegrity();
        return t;
    }

    private static EnumsApi.DataType toType(EnumsApi.VariableContext context) {
        switch(context) {
            case global:
                return EnumsApi.DataType.global_variable;
            case local:
            case array:
                return EnumsApi.DataType.variable;
            default:
                throw new IllegalStateException("#100.270 wrong context: " + context);
        }
    }

    private static TaskFileParamsYaml.InputVariable upInputVariable(TaskParamsYaml.InputVariable v1) {
        TaskFileParamsYaml.InputVariable  v = new TaskFileParamsYaml.InputVariable();
        v.id = v1.id.toString();
        v.dataType = toType(v1.context);
        v.name = v1.name;
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskParamsYaml.OutputVariable v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.id = v1.id.toString();
        v.name = v1.name;
        v.dataType = toType(v1.context);
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    private static int totalCountOfFunctions(TaskParamsYaml.TaskYaml taskYaml) {
        int count = 0;
        count += taskYaml.preFunctions.size();
        count += taskYaml.postFunctions.size();
        count++;
        return count;
    }

    @SuppressWarnings({"WeakerAccess", "deprecation"})
    // TODO 2019.05.02 implement unit-test for this method
    public FunctionApiData.SystemExecResult execFunction(
            ProcessorTask task, File taskDir, TaskParamsYaml taskParamYaml, File systemDir, FunctionPrepareResult functionPrepareResult,
            @Nullable DispatcherSchedule schedule) {

        File paramFile = new File(
                taskDir,
                ConstsApi.ARTIFACTS_DIR + File.separatorChar +
                        String.format(Consts.PARAMS_YAML_MASK, FunctionCoreUtils.getTaskParamsVersion(functionPrepareResult.function.metas)));

        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(functionPrepareResult.function.env)) {
            interpreter = new Interpreter(envService.getEnvYaml().getEnvs().get(functionPrepareResult.function.env));
            if (interpreter.list == null) {
                String es = "#100.290 Can't process the task, the interpreter wasn't found for env: " + functionPrepareResult.function.env;
                log.warn(es);
                return new FunctionApiData.SystemExecResult(functionPrepareResult.function.code, false, -991, es);
            }
            cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());
        }
        else {
            // function.file is executable file which will be present in any environment,
            // so we dont need to add environment specific command-line param
            // i.e. 'curl'
            cmd = new ArrayList<>();
        }

        log.info("All systems are checked for the task #{}, lift off", task.taskId );

        FunctionApiData.SystemExecResult systemExecResult;
        try {
            switch (functionPrepareResult.function.sourcing) {
                case dispatcher:
                case git:
                    if (functionPrepareResult.functionAssetFile ==null) {
                        throw new IllegalStateException("#100.310 functionAssetFile is null");
                    }
                    cmd.add(functionPrepareResult.functionAssetFile.file.getAbsolutePath());
                    break;
                case processor:
                    if (!S.b(functionPrepareResult.function.file)) {
                        //noinspection UseBulkOperation
                        Arrays.stream(StringUtils.split(functionPrepareResult.function.file)).forEachOrdered(cmd::add);
                    }
                    else if (!S.b(functionPrepareResult.function.content)) {
                        final String metaExt = MetaUtils.getValue(functionPrepareResult.function.metas,
                                ConstsApi.META_MH_FUNCTION_PARAMS_FILE_EXT_META);
                        String ext = S.b(metaExt) ? ".txt" : metaExt;
                        if (ext.indexOf('.')==-1) {
                            ext = "." + ext;
                        }

                        File execFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + toFilename(functionPrepareResult.function.code) + ext);
                        FileUtils.writeStringToFile(execFile, functionPrepareResult.function.content, StandardCharsets.UTF_8 );

                        cmd.add(execFile.getAbsolutePath());
                    }
                    else {
                        log.warn("#100.325 How?");
                    }
                    break;
                default:
                    throw new IllegalStateException("#100.330 Unknown sourcing: "+ functionPrepareResult.function.sourcing );
            }

            if (!functionPrepareResult.function.skipParams) {
                if (!S.b(functionPrepareResult.function.params)) {
                    List<String> list = Arrays.stream(StringUtils.split(functionPrepareResult.function.params)).filter(o->!S.b(o)).collect(Collectors.toList());
                    cmd.addAll(list);
                }
                cmd.add(paramFile.getAbsolutePath());
            }

            File consoleLogFile = new File(systemDir, Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            // Exec function
            systemExecResult = SystemProcessLauncher.execCommand(
                    cmd, taskDir, consoleLogFile, taskParamYaml.task.timeoutBeforeTerminate, functionPrepareResult.function.code, schedule,
                    globals.taskConsoleOutputMaxLines);

        }
        catch (ScheduleInactivePeriodException e) {
            throw e;
        }
        catch (Throwable th) {
            log.error("#100.350 Error exec process:\n" +
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
            functionPrepareResult.functionAssetFile = AssetUtils.prepareFunctionFile(baseResourceDir, functionPrepareResult.function.getCode(), functionPrepareResult.function.file);
            // is this function prepared?
            if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
                log.info("#100.370 Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
                functionPrepareResult.isLoaded = false;

                metadataService.setFunctionDownloadStatus(dispatcherUrl, function.code, EnumsApi.FunctionSourcing.dispatcher, Enums.FunctionState.none);

            }
        }
        else if (functionPrepareResult.function.sourcing== EnumsApi.FunctionSourcing.git) {
            if (S.b(functionPrepareResult.function.file)) {
                String s = S.f("#100.390 Function %s has a blank file", functionPrepareResult.function.code);
                log.warn(s);
                functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1, s);
                functionPrepareResult.isLoaded = false;
                functionPrepareResult.isError = true;
                return functionPrepareResult;
            }
            final File resourceDir = dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherCode);
            log.info("Root dir for function: " + resourceDir);
            GitSourcingService.GitExecResult result = gitSourcingService.prepareFunction(resourceDir, functionPrepareResult.function);
            if (!result.ok) {
                log.warn("#100.410 Function {} has a permanent error, {}", functionPrepareResult.function.code, result.error);
                functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1, result.error);
                functionPrepareResult.isLoaded = false;
                functionPrepareResult.isError = true;
                return functionPrepareResult;
            }
            functionPrepareResult.functionAssetFile = new AssetFile();
            functionPrepareResult.functionAssetFile.file = new File(result.functionDir, Objects.requireNonNull(functionPrepareResult.function.file));
            log.info("Function asset file: {}, exist: {}", functionPrepareResult.functionAssetFile.file.getAbsolutePath(), functionPrepareResult.functionAssetFile.file.exists() );
        }
        return functionPrepareResult;
    }
}