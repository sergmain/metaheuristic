/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.actors.DownloadSealedSecretService;
import ai.metaheuristic.ai.processor.secret.FunctionSecretGate;
import ai.metaheuristic.ai.processor.secret.FunctionSecretChannel;
import ai.metaheuristic.ai.processor.secret.SealedSecretCache;
import ai.metaheuristic.ai.processor.secret.TaskSecretPlan;
import ai.metaheuristic.ai.processor.security.ProcessorKeyPair;
import ai.metaheuristic.ai.processor.tasks.DownloadSealedSecretTask;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.commons.security.AsymmetricEncryptor;
import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.commons.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.functions.FunctionRepositoryData;
import ai.metaheuristic.ai.functions.FunctionRepositoryProcessorService;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
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
import ai.metaheuristic.commons.utils.ArtifactCommonUtils;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
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
    private final ProcessorKeyPair processorKeyPair;
    private final SealedSecretCache sealedSecretCache;
    private final DownloadSealedSecretService downloadSealedSecretService;

    private static final AtomicInteger activeTaskProcessing = new AtomicInteger();

    private boolean processing = false;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public TaskProcessor(Globals globals, ProcessorTaskService processorTaskService, CurrentExecState currentExecState,
                         ProcessorEnvironment processorEnvironment, ProcessorService processorService,
                         VariableProviderFactory resourceProviderFactory,
                         FunctionRepositoryProcessorService functionRepositoryProcessorService,
                         ProcessorKeyPair processorKeyPair,
                         SealedSecretCache sealedSecretCache,
                         DownloadSealedSecretService downloadSealedSecretService) {
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.currentExecState = currentExecState;
        this.processorEnvironment = processorEnvironment;
        this.processorService = processorService;
        this.resourceProviderFactory = resourceProviderFactory;
        this.functionRepositoryProcessorService = functionRepositoryProcessorService;
        this.processorKeyPair = processorKeyPair;
        this.sealedSecretCache = sealedSecretCache;
        this.downloadSealedSecretService = downloadSealedSecretService;
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

            final MetadataParamsYaml.ProcessorSession processorState = processorEnvironment.getProcessorEnv().metadataParams().processorStateByDispatcherUrl(core);
            if (processorState.processorId==null || S.b(processorState.sessionId)) {
                log.warn("100.010 processor {} with dispatcher {} isn't ready", core.coreCode, dispatcherUrl.url);
                continue;
            }

            DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = processorEnvironment.getProcessorEnv().dispatcherLookupExtendedService().lookupExtendedMap.get(dispatcherUrl);
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

            ProcessorData.ResultOfChecking resultOfChecking = processorService.checkForPreparingVariables(core, task, processorState, taskParamYaml, dispatcher, taskDir);
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

            if (!S.b(taskParamYaml.task.function.assetDir)) {
                Path assetDir = ProcessorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ASSET_DIR);
                if (assetDir == null) {
                    processorTaskService.markAsFinishedWithError(core, task.taskId, "100.105 Error of configuring of environment. 'asset' directory wasn't created, task can't be processed.");
                    continue;
                }
                // copy content of Function's asset dir to Task's asset dir
                final Path baseResourceDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath,
                        new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl));
                final Path functionDir = ArtifactCommonUtils.prepareFunctionPath(baseResourceDir)
                        .resolve(ArtifactCommonUtils.normalizeCode(taskParamYaml.task.function.code));
                final Path functionAssetDir = functionDir.resolve(taskParamYaml.task.function.assetDir);
                if (Files.isDirectory(functionAssetDir)) {
                    try {
                        PathUtils.copyDirectory(functionAssetDir, assetDir);
                    }
                    catch (IOException e) {
                        processorTaskService.markAsFinishedWithError(core, task.taskId,
                                "100.107 Error copying function's asset dir content to task's asset dir: " + e.getMessage());
                        continue;
                    }
                }
                else {
                    log.warn("100.106 Function {} has assetDir='{}' configured but directory {} doesn't exist",
                            taskParamYaml.task.function.code, taskParamYaml.task.function.assetDir, functionAssetDir);
                }
            }

            String status = EnvServiceUtils.prepareEnvironment(artifactDir, new EnvServiceUtils.EnvYamlShort(processorEnvironment.getProcessorEnv().envParams().getEnvParamsYaml()));
            if (status!=null) {
                processorTaskService.markAsFinishedWithError(core, task.taskId, status);
            }

            boolean isNotReady = false;
            final FunctionRepositoryData.FunctionPrepareResult[] results = new FunctionRepositoryData.FunctionPrepareResult[ totalCountOfFunctions(taskParamYaml.task) ];
            int idx = 0;
            FunctionRepositoryData.FunctionPrepareResult result;

            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

            for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
                result = functionRepositoryProcessorService.prepareFunction(assetManagerUrl, preFunctionConfig);
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

            result = functionRepositoryProcessorService.prepareFunction(assetManagerUrl, taskParamYaml.task.getFunction());
            if (result.isError) {
                markFunctionAsFinishedWithPermanentError(core, task.taskId, result);
                continue;
            }
            results[idx++] = result;
            if (!result.isLoaded || !isAllLoaded) {
                continue;
            }

            for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.task.postFunctions) {
                result = functionRepositoryProcessorService.prepareFunction(assetManagerUrl, postFunctionConfig);
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

            // Stage 6 (vault secret handoff): task-level secret plan.
            // Walks pre + main + post Functions, finds which (if any) needs an
            // API key. AWAITING → skip this task cycle; VIOLATION → mark task
            // failed; READY → decrypt + open channel + generate checkCode now;
            // NO_SECRET_NEEDED → continue normally with null channel/handoff.
            final TaskSecretPlan.Plan secretPlan = TaskSecretPlan.plan(
                    taskParamYaml.task, taskParamYaml.companyId,
                    k -> sealedSecretCache.get(taskParamYaml.companyId, k));

            if (secretPlan.kind() == TaskSecretPlan.Kind.AWAITING) {
                downloadSealedSecretService.add(new DownloadSealedSecretTask(
                        core, dispatcher.dispatcherLookup, task.taskId, core.processorId,
                        taskParamYaml.companyId, secretPlan.keyCode()));
                log.info("100.140 Sealed secret not yet cached for task {}, key {}; enqueued fetch, skip this launch cycle",
                        task.taskId, secretPlan.keyCode());
                continue;
            }
            if (secretPlan.kind() == TaskSecretPlan.Kind.MULTI_SECRET_VIOLATION) {
                log.warn(secretPlan.violationMessage());
                processorTaskService.markAsFinishedWithError(core, task.taskId, secretPlan.violationMessage());
                continue;
            }

            byte[] keyBytes = null;
            String checkCode = null;
            FunctionSecretChannel secretChannel = null;
            SystemProcessLauncher.SecretHandoff secretHandoff = null;
            String secretPhase = null;
            try {
                if (secretPlan.kind() == TaskSecretPlan.Kind.READY) {
                    try {
                        keyBytes = AsymmetricEncryptor.decrypt(secretPlan.sealed(), processorKeyPair.getPrivateKey());
                    } catch (Throwable th) {
                        String es = "100.142 Failed to decrypt sealed secret for task #" + task.taskId + ", key " + secretPlan.keyCode() + ": " + th.getMessage();
                        log.warn(es, th);
                        processorTaskService.markAsFinishedWithError(core, task.taskId, es);
                        continue;
                    }
                    checkCode = generateCheckCode();
                    try {
                        secretChannel = new FunctionSecretChannel();
                    } catch (IOException ioe) {
                        String es = "100.144 Failed to open FunctionSecretChannel for task #" + task.taskId + ": " + ioe.getMessage();
                        log.warn(es, ioe);
                        processorTaskService.markAsFinishedWithError(core, task.taskId, es);
                        continue;
                    }
                    final FunctionSecretChannel ch = secretChannel;
                    final String cc = checkCode;
                    final byte[] kb = keyBytes;
                    secretHandoff = new SystemProcessLauncher.SecretHandoff(
                            ch.getPort(),
                            () -> {
                                try {
                                    ch.handoff(cc, kb);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                    secretPhase = secretPlan.phase();
                }

                try {
                    if (!prepareParamsFileForTask(taskDir, taskParamYaml, results, checkCode,
                            secretChannel != null ? secretChannel.getPort() : null)) {
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
                    execAllFunctions(core, task, processorState, dispatcher, taskDir, taskParamYaml, systemDir, results,
                            secretPhase, secretHandoff);
                }
                catch(ScheduleInactivePeriodException e) {
                    processorTaskService.resetTask(core, task.taskId);
                    processorTaskService.delete(core, task.taskId);
                    log.info("100.130 An execution of task #{} was terminated because of the beginning of inactivity period. " +
                            "This task will be processed later", task.taskId);
                }
            }
            finally {
                // Stage 6: zero plaintext key bytes and close the loopback
                // channel — single ownership at the task level. Whether the
                // handoff succeeded, failed, threw, or was never invoked, we
                // always reach this block.
                if (keyBytes != null) {
                    java.util.Arrays.fill(keyBytes, (byte) 0);
                }
                if (secretChannel != null) {
                    try {
                        secretChannel.close();
                    } catch (IOException ignored) {
                        // best effort
                    }
                }
            }
        }
    }

    private void markFunctionAsFinishedWithPermanentError(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, Long taskId, FunctionRepositoryData.FunctionPrepareResult result) {
        FunctionApiData.SystemExecResult execResult = new FunctionApiData.SystemExecResult(
                result.getFunction().code, false, -990,
                "100.150 Function "+result.getFunction().code+" has a permanent error: " + (result.getSystemExecResult()!=null ? result.getSystemExecResult().console : "<system exec console is null>"));
        processorTaskService.markAsFinished(core, taskId,
                new FunctionApiData.FunctionExec(null, null, null, execResult));
    }

    private void execAllFunctions(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
                                  ProcessorCoreTask task, MetadataParamsYaml.ProcessorSession processorState,
                                  DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher,
                                  Path taskDir, TaskParamsYaml taskParamYaml,
                                  Path systemDir, FunctionRepositoryData.@Nullable FunctionPrepareResult[] results,
                                  // Stage 6: secretPhase is the per-task secret-owning phase ("pre[N]" / "main" /
                                  // "post[N]"), or null when no Function in this task needs a secret. secretHandoff
                                  // is the SystemProcessLauncher hook bound to that phase. Both are null together.
                                  @Nullable String secretPhase,
                                  SystemProcessLauncher.@Nullable SecretHandoff secretHandoff) {
        List<FunctionApiData.SystemExecResult> preSystemExecResult = new ArrayList<>();
        List<FunctionApiData.SystemExecResult> postSystemExecResult = new ArrayList<>();
        boolean isOk = true;
        int idx = 0;
        DispatcherSchedule schedule = dispatcher.schedule.policy == ExtendedTimePeriod.SchedulePolicy.strict ? dispatcher.schedule : null;
        int preIdx = 0;
        for (TaskParamsYaml.FunctionConfig preFunctionConfig : taskParamYaml.task.preFunctions) {
            FunctionRepositoryData.FunctionPrepareResult result = results[idx++];
            FunctionApiData.SystemExecResult execResult;
            if (result==null) {
                execResult = new FunctionApiData.SystemExecResult(
                        preFunctionConfig.code, false, -999,
                        "100.170 Illegal State, result of preparing of function "+ preFunctionConfig.code+" is null");
            }
            else {
                SystemProcessLauncher.SecretHandoff preHandoff =
                        ("pre[" + preIdx + "]").equals(secretPhase) ? secretHandoff : null;
                execResult = execFunction(core, dispatcher.dispatcherLookup, task, taskDir, taskParamYaml, systemDir, result, schedule, preHandoff);
            }
            preIdx++;
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
                SystemProcessLauncher.SecretHandoff mainHandoff =
                        "main".equals(secretPhase) ? secretHandoff : null;
                systemExecResult = execFunction(core, dispatcher.dispatcherLookup, task, taskDir, taskParamYaml, systemDir, result, schedule, mainHandoff);
                if (!systemExecResult.isOk) {
                    isOk = false;
                }
                if (isOk) {
                    int postIdx = 0;
                    for (TaskParamsYaml.FunctionConfig postFunctionConfig : taskParamYaml.task.postFunctions) {
                        result = results[idx++];
                        FunctionApiData.SystemExecResult execResult;
                        if (result==null) {
                            execResult = new FunctionApiData.SystemExecResult(
                                    postFunctionConfig.code, false, -999,
                                    "100.210 Illegal State, result of preparing of function "+ postFunctionConfig.code+" is null");
                        }
                        else {
                            SystemProcessLauncher.SecretHandoff postHandoff =
                                    ("post[" + postIdx + "]").equals(secretPhase) ? secretHandoff : null;
                            execResult = execFunction(core, dispatcher.dispatcherLookup, task, taskDir, taskParamYaml, systemDir, result, schedule, postHandoff);
                        }
                        postIdx++;
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
        return prepareParamsFileForTask(taskDir, taskParamYaml, results, null, null);
    }

    /**
     * Stage 6 overload: when {@code checkCode} and {@code secretPort} are
     * both non-null, they're written into the TaskFileParamsYaml so the
     * Function reads them on startup and participates in the secret-channel
     * handoff. Both null = no handoff for this task.
     */
    private static boolean prepareParamsFileForTask(Path taskDir, TaskParamsYaml taskParamYaml, FunctionRepositoryData.FunctionPrepareResult[] results,
                                                    @Nullable String checkCode, @Nullable Integer secretPort) {

        Path artifactDir = ProcessorTaskService.prepareTaskSubDir(taskDir, ConstsApi.ARTIFACTS_DIR);
        if (artifactDir == null) {
            return false;
        }

        Set<Integer> versions = Stream.of(results)
                .map(o-> FunctionCoreUtils.getTaskParamsVersion(o.function.metas))
                .collect(Collectors.toSet());

        return ArtifactUtils.prepareParamsFileForTask(artifactDir, taskDir.toAbsolutePath().toString(), taskParamYaml, versions, checkCode, secretPort);
    }

    /**
     * Stage 6: generates a fresh per-launch check-code. 32 bytes from
     * {@link java.security.SecureRandom} encoded as hex (64-char string).
     * The token is written ONLY into TaskFileParamsYaml; it never appears
     * in the cmdline, env, or anywhere else on disk.
     */
    private static String generateCheckCode() {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
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
            ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core,
            DispatcherLookupParamsYaml.DispatcherLookup dispatcher,
            ProcessorCoreTask task, Path taskDir, TaskParamsYaml taskParamYaml, Path systemDir, FunctionRepositoryData.FunctionPrepareResult functionPrepareResult,
            @Nullable DispatcherSchedule schedule,
            SystemProcessLauncher.@Nullable SecretHandoff secretHandoff) {

        Path paramFile = taskDir
                .resolve(ConstsApi.ARTIFACTS_DIR)
                .resolve(String.format(Consts.PARAMS_YAML_MASK, FunctionCoreUtils.getTaskParamsVersion(functionPrepareResult.function.metas)));

        List<String> cmd;
        Interpreter interpreter=null;
        if (StringUtils.isNotBlank(functionPrepareResult.function.env)) {
            final String exec = processorEnvironment.getProcessorEnv().envParams().getEnvParamsYaml().getEnvs().stream().filter(o -> o.code.equals(functionPrepareResult.function.env)).findFirst().map(o -> o.exec).orElse(null);
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
                    if (functionPrepareResult.functionAssetFile ==null || functionPrepareResult.functionAssetFile.file==null) {
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
            // HARD CONTRACT — DO NOT BREAK.
            //
            // The LAST positional argument passed to every Function is the
            // ABSOLUTE PATH (not just the filename — full path, .toAbsolutePath())
            // to the TaskFileParamsYaml file. Functions parse argv[-1] as the
            // params-file path and read everything else (Function-specific args
            // declared in FunctionConfig.params, plus any wrapper-interpreter
            // args) from positions before it.
            //
            // Nothing — no flag, no named arg, no Stage-6 secret port,
            // no debug switch — may be appended after this line. If you need
            // to pass new per-launch data to the Function, add a field to
            // TaskFileParamsYaml.Task (the Function already reads that file at
            // startup; one more @Nullable field is free) and the Function-side
            // SDK exposes it. See Stage 6 (vault secret handoff) where
            // checkCode + secretPort live in TaskFileParamsYaml precisely
            // because the cmdline tail is unavailable.
            //
            // Adding flags here would break every third-party Function (Java,
            // Python, Go, shell) that counts argv positionally — and the
            // breakage would be silent because most Functions don't validate
            // the trailing arg is a real file path before opening it.
            cmd.add(paramFile.toAbsolutePath().toString());

            Path consoleLogFile = systemDir.resolve(Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME);

            final Supplier<Boolean> execContextDeletionCheck =
                    () -> currentExecState.isState(new ProcessorAndCoreData.DispatcherUrl(task.dispatcherUrl), task.execContextId,
                            EnumsApi.ExecContextState.DOESNT_EXIST, EnumsApi.ExecContextState.STOPPED, EnumsApi.ExecContextState.ERROR,
                            EnumsApi.ExecContextState.FINISHED);

            // Stage 6 (vault secret handoff): if this is the phase that owns the
            // task's single secret, pass the SecretHandoff through to the
            // launcher; otherwise launch normally. Decrypt + checkCode + channel
            // lifecycle live at task level (execAllFunctions caller) — single
            // ownership of keyBytes; this method does NOT zero or close anything.
            try {
                activeTaskProcessing.incrementAndGet();
                log.info("All systems are checked for the task #{}, lift off, active task processing: {}", task.taskId, activeTaskProcessing.get());
                if (secretHandoff != null) {
                    systemExecResult = SystemProcessLauncher.execCommandWithSecret(
                            cmd, taskDir, consoleLogFile, taskParamYaml.task.timeoutBeforeTerminate, functionPrepareResult.function.code, schedule,
                            globals.processor.taskConsoleOutputMaxLines, List.of(execContextDeletionCheck), null,
                            secretHandoff);
                } else {
                    systemExecResult = SystemProcessLauncher.execCommand(
                            cmd, taskDir, consoleLogFile, taskParamYaml.task.timeoutBeforeTerminate, functionPrepareResult.function.code, schedule,
                            globals.processor.taskConsoleOutputMaxLines, List.of(execContextDeletionCheck), null);
                }
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
