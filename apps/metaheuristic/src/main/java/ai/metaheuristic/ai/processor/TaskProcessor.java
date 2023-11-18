/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.functions.FunctionRepositoryData;
import ai.metaheuristic.ai.functions.FunctionRepositoryProcessorService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.variable_providers.VariableProvider;
import ai.metaheuristic.ai.processor.variable_providers.VariableProviderFactory;
import ai.metaheuristic.ai.utils.ArtifactUtils;
import ai.metaheuristic.ai.utils.EnvServiceUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.commons.dispatcher_schedule.ExtendedTimePeriod;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.lang.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 1/4/2021
 * Time: 9:07 AM
 */
@SuppressWarnings("SimplifyStreamApiCallChains")
@Slf4j
public class TaskProcessor {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final ProcessorEnvironment processorEnvironment;
    private final ProcessorService processorService;
    private final VariableProviderFactory resourceProviderFactory;
    private final FunctionRepositoryProcessorService functionRepositoryProcessorService;

    private static final AtomicInteger activeTaskProcessing = new AtomicInteger();

    private boolean processing = false;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public TaskProcessor(Globals globals, ProcessorTaskService processorTaskService, CurrentExecState currentExecState,
                         ProcessorEnvironment processorEnvironment, ProcessorService processorService,
                         VariableProviderFactory resourceProviderFactory,
                         FunctionRepositoryProcessorService functionRepositoryProcessorService) {
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.currentExecState = currentExecState;
        this.processorEnvironment = processorEnvironment;
        this.processorService = processorService;
        this.resourceProviderFactory = resourceProviderFactory;
        this.functionRepositoryProcessorService = functionRepositoryProcessorService;
    }

    public void process(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {
        if (!globals.processor.enabled) {
            return;
        }
        if (processing) {
            return;
        }
        writeLock.lock();
        try {
            if (processing) {
                return;
            }
            processing = true;
            try {
                processInternal(core);
            }
            finally {
                processing = false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void processInternal(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core) {

        log.debug("100.000 Start processInternal at processor {} for url #{}", core.coreCode, core.dispatcherUrl);

        // find all tasks which weren't completed and  weren't finished and resources aren't prepared yet
        List<ProcessorCoreTask> tasks = processorTaskService.findAllByCompetedIsFalseAndFinishedOnIsNullAndAssetsPreparedIs(core, true);
        for (ProcessorCoreTask task : tasks) {
            log.info("100.001 Start processing task #{}", task.taskId);

            if (StringUtils.isBlank(task.dispatcherUrl)) {
                final String es = "100.005 task.dispatcherUrl is blank for task #" + task.taskId;
                log.error(es);
                // because task is valid only for correct dispatcherUrl, we don't need to mark it as FinishedWithError
                continue;
            }

            ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl(task.dispatcherUrl);

            processorTaskService.setLaunchOn(core, task.taskId);

            final MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.metadataParams.processorStateByDispatcherUrl(core);
            if (processorState.processorId==null || S.b(processorState.sessionId)) {
                log.warn("100.010 processor {} with dispatcher {} isn't ready", core.coreCode, dispatcherUrl.url);
                continue;
            }

            DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
            if (dispatcher==null) {
                final String es = "100.020 Broken task #"+task.taskId+". dispatcher wasn't found for url " + dispatcherUrl;
                processorTaskService.markAsFinishedWithError(core, task.taskId, es);
                continue;
            }

            if (dispatcher.schedule.isCurrentTimeInactive()) {
                processorTaskService.delete(core, task.taskId);
                log.warn("100.025 Can't process task #{} for url {} at this time, time: {}, permitted period of time: {}", task.taskId, dispatcherUrl, new Date(), dispatcher.schedule.asString);
                return;
            }

            if (StringUtils.isBlank(task.getParams())) {
                log.warn("100.030 Params for task #{} is blank", task.getTaskId());
                continue;
            }

            EnumsApi.ExecContextState state = currentExecState.getState(dispatcherUrl, task.execContextId);
            if (state== EnumsApi.ExecContextState.UNKNOWN) {
                log.info("100.032 The state for ExecContext #{}, host {} is unknown, the task #{} will be skipped", task.execContextId, dispatcherUrl, task.taskId);
                continue;
            }

            if (state!= EnumsApi.ExecContextState.STARTED) {
                log.info("100.034 The state for ExecContext #{}, host: {}, is {}, delete a task #{}", task.execContextId, dispatcherUrl, state, task.taskId);
                processorTaskService.delete(core, task.taskId);
                continue;
            }

            log.info("Start processing task {}", task);
            Path taskDir = processorTaskService.prepareTaskDir(core, task.taskId);

            final TaskParamsYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamsYamlUtils.UTILS.to(task.getParams());
            } catch (CheckIntegrityFailedException e) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, "100.037 Broken task. Check of integrity was failed.");
                continue;
            }

            ProcessorService.ResultOfChecking resultOfChecking = processorService.checkForPreparingVariables(core, task, processorState, taskParamYaml, dispatcher, taskDir);
            if (resultOfChecking.isError) {
                continue;
            }
            boolean isAllLoaded = resultOfChecking.isAllLoaded;
            if (!processorService.checkOutputResourceFile(core, task, taskParamYaml, dispatcher, taskDir)) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, "100.040 Broken task. Can't create outputResourceFile");
                continue;
            }
            if (taskParamYaml.task.outputs.isEmpty()) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, "100.050 Broken task. output variable must be specified");
                continue;
            }

            Path artifactDir = ProcessorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
            if (artifactDir == null) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, "100.090 Error of configuring of environment. 'artifacts' directory wasn't created, task can't be processed.");
                continue;
            }

            Path systemDir = ProcessorTaskService.prepareTaskSubDir(taskDir, Consts.SYSTEM_DIR);
            if (systemDir == null) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, "100.100 Error of configuring of environment. 'system' directory wasn't created, task can't be processed.");
                continue;
            }

            String status = EnvServiceUtils.prepareEnvironment(artifactDir, new EnvServiceUtils.EnvYamlShort(processorEnvironment.envParams.getEnvParamsYaml()));
            if (status!=null) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, status);
            }

            boolean isNotReady = false;
            final FunctionRepositoryData.FunctionPrepareResult[] results = new FunctionRepositoryData.FunctionPrepareResult[ totalCountOfFunctions(taskParamYaml.task) ];
            int idx = 0;
            FunctionRepositoryData.FunctionPrepareResult result;

            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

            for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
                result = functionRepositoryProcessorService.prepareFunction(dispatcher, assetManagerUrl, preFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(core, task.taskId, result);
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

            result = functionRepositoryProcessorService.prepareFunction(dispatcher, assetManagerUrl, taskParamYaml.task.getFunction());
            if (result.isError) {
                markFunctionAsFinishedWithPermanentError(core, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.task.postFunctions) {
                result = functionRepositoryProcessorService.prepareFunction(dispatcher, assetManagerUrl, postFunctionConfig);
                if (result.isError) {
                    markFunctionAsFinishedWithPermanentError(core, task.taskId, result);
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
                String es = "100.110 Error while preparing params.yaml file for task #"+task.taskId+", error: " + th.getMessage();
                log.warn(es);
                processorTaskService.markAsFinishedWithError(core, task.taskId, es);
                continue;
            }

            // at this point all required resources have to be prepared
            ProcessorCoreTask taskResult = processorTaskService.setLaunchOn(core, task.taskId);
            if (taskResult==null) {
                String es = "100.120 Task #"+task.taskId+" wasn't found";
                log.warn(es);
                // there isn't this task any more. So we can't mark it as Finished
//                processorTaskService.markAsFinishedWithError(task.dispatcherUrl, task.taskId, es);
                continue;
            }
            try {
                execAllFunctions(core, task, processorState, dispatcher, taskDir, taskParamYaml, systemDir, results);
            }
            catch(ScheduleInactivePeriodException e) {
                processorTaskService.resetTask(core, task.taskId);
                processorTaskService.delete(core, task.taskId);
                log.info("100.130 An execution of task #{} was terminated because of the beginning of inactivity period. " +
                        "This task will be processed later", task.taskId);
            }
        }
    }

    private void markFunctionAsFinishedWithPermanentError(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, FunctionRepositoryData.FunctionPrepareResult result) {
        FunctionApiData.SystemExecResult execResult = new FunctionApiData.SystemExecResult(
                result.getFunction().code, false, -990,
                "100.150 Function "+result.getFunction().code+" has a permanent error: " + result.getSystemExecResult().console);
        processorTaskService.markAsFinished(core, taskId,
                new FunctionApiData.FunctionExec(null, null, null, execResult));
    }

    private void execAllFunctions(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
                                  ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
                                  DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
                                  Path taskDir, TaskParamsYaml taskParamYaml,
                                  Path systemDir, FunctionRepositoryData.FunctionPrepareResult[] results) {
        List<FunctionApiData.SystemExecResult> preSystemExecResult = new ArrayList<>();
        List<FunctionApiData.SystemExecResult> postSystemExecResult = new ArrayList<>();
        boolean isOk = true;
        int idx = 0;
        DispatcherSchedule schedule = dispatcher.schedule.policy == ExtendedTimePeriod.SchedulePolicy.strict ? dispatcher.schedule : null;
        for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
            FunctionRepositoryData.FunctionPrepareResult result = results[idx++];
            FunctionApiData.SystemExecResult execResult;
            if (result==null) {
                execResult = new FunctionApiData.SystemExecResult(
                        preFunctionConfig.code, false, -999,
                        "100.170 Illegal State, result of preparing of function "+ preFunctionConfig.code+" is null");
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
            FunctionRepositoryData.FunctionPrepareResult result = results[idx++];
            if (result==null) {
                systemExecResult = new FunctionApiData.SystemExecResult(
                        taskParamYaml.task.getFunction().code, false, -999,
                        "100.190 Illegal State, result of preparing of function "+taskParamYaml.task.getFunction()+" is null");
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
                                    "100.210 Illegal State, result of preparing of function "+ postFunctionConfig.code+" is null");
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
                                        core, taskDir, dispatcher, task, processorState, outputVariable, mainFunctionConfig);
                            }
                        }
                        catch (Throwable th) {
                            generalExec = new FunctionApiData.SystemExecResult(
                                    mainFunctionConfig.code, false, -997,
                                    "100.230 Error storing function's result, error: " + th.getMessage());

                        }
                    }
                }
            }
        }

        processorTaskService.markAsFinished(core, task.getTaskId(),
                new FunctionApiData.FunctionExec(systemExecResult, preSystemExecResult, postSystemExecResult, generalExec));
    }

    private static boolean prepareParamsFileForTask(Path taskDir, TaskParamsYaml taskParamYaml, FunctionRepositoryData.FunctionPrepareResult[] results) {

        Path artifactDir = ProcessorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return false;
        }

        Set<Integer> versions = Stream.of(results)
                .map(o-> FunctionCoreUtils.getTaskParamsVersion(o.function.metas))
                .collect(Collectors.toSet());

        return ArtifactUtils.prepareParamsFileForTask(artifactDir, taskDir.toAbsolutePath().toString(), taskParamYaml, versions);
    }

    private static int totalCountOfFunctions(TaskParamsYaml.TaskYaml taskYaml) {
        int count = 0;
        count += taskYaml.preFunctions.size();
        count += taskYaml.postFunctions.size();
        count++;
        return count;
    }

    @SuppressWarnings({"WeakerAccess", "UseBulkOperation"})
    // TODO 2019.05.02 implement unit-test for this method
    public FunctionApiData.SystemExecResult execFunction(
            ProcessorCoreTask task, Path taskDir, TaskParamsYaml taskParamYaml, Path systemDir, FunctionRepositoryData.FunctionPrepareResult functionPrepareResult,
            @Nullable DispatcherSchedule schedule) {

        Path paramFile = taskDir
                .resolve(ConstsApi.ARTIFACTS_DIR)
                .resolve(String.format(Consts.PARAMS_YAML_MASK, FunctionCoreUtils.getTaskParamsVersion(functionPrepareResult.function.metas)));

        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(functionPrepareResult.function.env)) {
            final String exec = processorEnvironment.envParams.getEnvParamsYaml().getEnvs().stream().filter(o -> o.code.equals(functionPrepareResult.function.env)).findFirst().map(o -> o.exec).orElse(null);
            interpreter = new Interpreter(exec);
            if (interpreter.list == null) {
                String es = "100.290 Can't process the task, the interpreter wasn't found for env: " + functionPrepareResult.function.env;
                log.warn(es);
                return new FunctionApiData.SystemExecResult(functionPrepareResult.function.code, false, -991, es);
            }
            cmd = Arrays.stream(interpreter.list).collect(Collectors.toList());
        }
        else {
            // function.file is executable file which will be existed in any environment,
            // so we don't need to add environment specific command-line param
            // i.e. 'curl'
            cmd = new ArrayList<>();
        }

        FunctionApiData.SystemExecResult systemExecResult;
        try {
            switch (functionPrepareResult.function.sourcing) {
                case dispatcher:
                case git:
                    if (functionPrepareResult.functionAssetFile ==null) {
                        throw new IllegalStateException("100.310 functionAssetFile is null");
                    }
                    cmd.add(functionPrepareResult.functionAssetFile.file.toAbsolutePath().toString());
                    break;
                default:
                    throw new IllegalStateException("100.330 Unknown sourcing: "+ functionPrepareResult.function.sourcing );
            }

            if (!S.b(functionPrepareResult.function.params)) {
                List<String> list = Arrays.stream(StringUtils.split(functionPrepareResult.function.params)).filter(o->!S.b(o)).collect(Collectors.toList());
                cmd.addAll(list);
            }
            cmd.add(paramFile.toAbsolutePath().toString());

            Path consoleLogFile = systemDir.resolve(Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            final Supplier<Boolean> execContextDeletionCheck =
                    () -> currentExecState.isState(new ProcessorAndCoreData.DispatcherUrl(task.dispatcherUrl), task.execContextId,
                            EnumsApi.ExecContextState.DOESNT_EXIST, EnumsApi.ExecContextState.STOPPED, EnumsApi.ExecContextState.ERROR,
                            EnumsApi.ExecContextState.FINISHED);

            try {
                activeTaskProcessing.incrementAndGet();
                log.info("All systems are checked for the task #{}, lift off, active task processing: {}", task.taskId, activeTaskProcessing.get());
                // Exec function
                systemExecResult = SystemProcessLauncher.execCommand(
                        cmd, taskDir, consoleLogFile, taskParamYaml.task.timeoutBeforeTerminate, functionPrepareResult.function.code, schedule,
                        globals.processor.taskConsoleOutputMaxLines, List.of(execContextDeletionCheck));
            }
            finally {
                activeTaskProcessing.decrementAndGet();
            }
        }
        catch (ScheduleInactivePeriodException e) {
            throw e;
        }
        catch (Throwable th) {
            log.error("100.350 Error exec process:\n" +
                    "\tenv: " + functionPrepareResult.function.env +"\n" +
                    "\tinterpreter: " + interpreter+"\n" +
                    "\tfile: " + (functionPrepareResult.functionAssetFile !=null && functionPrepareResult.functionAssetFile.file!=null
                    ? functionPrepareResult.functionAssetFile.file.toAbsolutePath()
                    : functionPrepareResult.function.file) +"\n" +
                    "\tparams", th);
            systemExecResult = new FunctionApiData.SystemExecResult(
                    functionPrepareResult.function.code, false, -1, ExceptionUtils.getStackTrace(th));
        }
        return systemExecResult;
    }
}
