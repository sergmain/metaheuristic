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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterFunctionCodesForStartedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 11/14/2023
 * Time: 11:20 PM
 */
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionRepositoryDispatcherService {

    private final SourceCodeRepository sourceCodeRepository;
    private final ExecContextRepository execContextRepository;
    private final FunctionRepository functionRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final ExecutionGateService executionGateService;

    // which Processor has which function ready is no longer held here - it is an admission input and
    // lives with the rest of them. What stays is which functions are ACTIVE, which is this class's own
    // bookkeeping about the source codes it has seen.
    private static final Set<String> activeFunctions = new HashSet<>();
    private static final LinkedHashMap<String, FunctionRepositoryResponseParams.ShortFunctionConfig> shortFunctionConfigCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FunctionRepositoryResponseParams.ShortFunctionConfig> eldest) {
            return size()>100;
        }
    };

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    /**
     * ❗ No readiness filtering here any more. Readiness is keyed by what was reported, and for a
     * git-sourced Function that key carries the revision - which is not known until the Function's
     * descriptor has been loaded and its pinned shas looked up. Filtering on the bare code here would
     * never match a git report, so the Function would be advertised for ever: the Processor answers "I
     * have it", the Dispatcher offers it again, and the protocol's own check fires as
     * {@code 778.050 isNotEmpty(p)} on every poll.
     *
     * <p>The filtering now happens in {@link #processRequest}, per advertised entry, where the sha is in
     * hand.
     */
    public Set<String> getActiveFunctionCode() {
        readLock.lock();
        try {
            return new HashSet<>(activeFunctions);
        } finally {
            readLock.unlock();
        }
    }

    public String processRequest(String data, String remoteAddr) {
        FunctionRepositoryRequestParams p = FunctionRepositoryRequestParamsUtils.UTILS.to(data);
        FunctionRepositoryResponseParams r = new FunctionRepositoryResponseParams();
        r.success = true;
        boolean newlyReady = registerReadyFunctionCodesOnProcessor(p);
        if (newlyReady) {
            // a processor just became ready for a function it wasn't ready for before;
            // re-notify processors so tasks waiting on that function get picked up
            eventPublisher.publishEvent(new ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketEvent(ai.metaheuristic.ai.Enums.WebsocketEventType.task));
            eventPublisher.publishEvent(new ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent());
        }

        final Set<String> activeFunctionCodes = getActiveFunctionCode();
        if (CollectionUtils.isNotEmpty(activeFunctionCodes)) {
            r.functions = new ArrayList<>();
            for (String activeFunctionCode : activeFunctionCodes) {
                // Cheap skip first, and it covers the overwhelmingly common case: a dispatcher-sourced
                // Function is reported under its bare code, so a match here settles it without loading the
                // descriptor at all. Only a Function that is unreported, or reported under a key carrying a
                // revision, needs the lookup below to find out which it is.
                if (alreadyReported(p.processorId, activeFunctionCode, null)) {
                    continue;
                }
                FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig = toShortFunctionConfig(activeFunctionCode);
                if (shortFunctionConfig == null) {
                    log.warn("479.040 Function wasn't found for code " + activeFunctionCode);
                    continue;
                }
                if (unresolvedGitRevision(shortFunctionConfig)) {
                    // A descriptor naming HEAD has no revision to advertise - HEAD is not a revision, and
                    // asking a Processor to prepare it would invite it to pick one of its own, which is the
                    // thing pinning exists to prevent. What CAN be advertised is what an ExecContext already
                    // resolved: registerResolvedGitRevisions records each sha as it is pinned, and one
                    // advertisement per pinned sha goes out here.
                    //
                    // ❗ Without this the Function is never advertised, so the Processor never reports it,
                    // so allFunctionsReady never passes and the Task is withheld as functions_not_ready -
                    // while the per-Task path that would have prepared it only runs on a Task the Processor
                    // was given. That is a deadlock, and a git-sourced Function pinned at HEAD could never
                    // run at all.
                    final List<GitInfo> pinned = resolvedGitRevisionsOf(activeFunctionCode);
                    if (pinned.isEmpty()) {
                        log.debug("479.045 Function {} is git-sourced at HEAD and no ExecContext has pinned a "
                            + "revision yet, so there is nothing to advertise", activeFunctionCode);
                        continue;
                    }
                    for (GitInfo git : pinned) {
                        if (alreadyReported(p.processorId, activeFunctionCode, git.commit)) {
                            continue;
                        }
                        final FunctionRepositoryResponseParams.ShortFunctionConfig resolved =
                            new FunctionRepositoryResponseParams.ShortFunctionConfig();
                        resolved.code = shortFunctionConfig.code;
                        resolved.sourcing = shortFunctionConfig.sourcing;
                        resolved.git = git;
                        r.functions.add(resolved);
                    }
                    continue;
                }
                if (shortFunctionConfig.sourcing==EnumsApi.FunctionSourcing.git && shortFunctionConfig.git!=null
                        && alreadyReported(p.processorId, activeFunctionCode, shortFunctionConfig.git.commit)) {
                    continue;
                }
                r.functions.add(shortFunctionConfig);
            }
        }

        String response = FunctionRepositoryResponseParamsUtils.UTILS.toString(r);
        return response;
    }

    /** Whether this Processor has already said it holds exactly this Function at exactly this revision. */
    private boolean alreadyReported(@Nullable Long processorId, String functionCode, @Nullable String commit) {
        return processorId!=null
                && executionGateService.isProcessorReady(ExecutionGateService.readinessKey(functionCode, commit), processorId);
    }

    /**
     * Every revision a live ExecContext has pinned a git-sourced Function to.
     *
     * <p>Keyed by function code, then by sha, because the same Function legitimately runs at different
     * revisions in different ExecContexts and each of those is separately worth advertising.
     */
    private static final Map<String, Map<String, GitInfo>> resolvedGitRevisions = new ConcurrentHashMap<>();

    private static List<GitInfo> resolvedGitRevisionsOf(String functionCode) {
        final Map<String, GitInfo> bySha = resolvedGitRevisions.get(functionCode);
        return bySha==null ? List.of() : List.copyOf(bySha.values());
    }

    /**
     * Called when an ExecContext has resolved HEAD to a concrete sha, which is the first moment the
     * revision exists at all. Records it so the broadcast can advertise it, seeds its readiness entry,
     * and nudges Processors to ask again rather than waiting out their poll interval.
     */
    public void registerResolvedGitRevisions(ExecContextParamsYaml.@Nullable GitSources gitSources) {
        if (gitSources==null || gitSources.gitSourceInfos.isEmpty()) {
            return;
        }
        boolean anyNew = false;
        for (ExecContextParamsYaml.GitSourceInfo info : gitSources.gitSourceInfos) {
            if (info.git==null || !GtiUtils.isSha(info.git.commit)) {
                continue;
            }
            final GitInfo git = new GitInfo();
            git.repo = info.git.repo;
            git.branch = info.git.branch;
            git.commit = info.git.commit;
            git.path = info.git.path;

            final Map<String, GitInfo> bySha =
                resolvedGitRevisions.computeIfAbsent(info.functionCode, o -> new ConcurrentHashMap<>());
            if (bySha.putIfAbsent(git.commit, git)==null) {
                anyNew = true;
            }
            executionGateService.seedFunctionReadiness(
                ExecutionGateService.readinessKey(info.functionCode, git.commit));
        }
        if (anyNew) {
            eventPublisher.publishEvent(new ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketEvent(
                ai.metaheuristic.ai.Enums.WebsocketEventType.function));
        }
    }

    private static boolean unresolvedGitRevision(FunctionRepositoryResponseParams.ShortFunctionConfig f) {
        if (f.sourcing!=EnumsApi.FunctionSourcing.git) {
            return false;
        }
        return f.git==null || GtiUtils.isHeadRevision(f.git.commit);
    }

    private FunctionRepositoryResponseParams.@Nullable ShortFunctionConfig toShortFunctionConfig(String functionCode) {
        FunctionRepositoryResponseParams.ShortFunctionConfig f = shortFunctionConfigCache.computeIfAbsent(functionCode, this::loadShortFunctionConfig);
        return f;
    }

    private FunctionRepositoryResponseParams.@Nullable ShortFunctionConfig loadShortFunctionConfig(String functionCode) {
        Function f = functionRepository.findByCode(functionCode);
        if (f==null) {
            return null;
        }
        FunctionConfigYaml params = f.getFunctionConfigYaml();
        return new FunctionRepositoryResponseParams.ShortFunctionConfig(functionCode, params.function.sourcing, params.function.git);
    }

    private boolean registerReadyFunctionCodesOnProcessor(FunctionRepositoryRequestParams p) {
        if (p.processorId==null || CollectionUtils.isEmpty(p.functionCodes)) {
            return false;
        }
        boolean anyNew = false;
        for (String readinessKey : p.functionCodes) {
            // the report is keyed - plain code for dispatcher sourcing, code+revision for git - so the
            // activeness check has to look at the code inside it, while the record keeps the whole key
            if (!isActiveFunction(ExecutionGateService.functionCodeOfReadinessKey(readinessKey))) {
                continue;
            }
            if (executionGateService.recordFunctionReadiness(readinessKey, p.processorId)) {
                anyNew = true;
            }
        }
        return anyNew;
    }

    /**
     * The {@code force} flag stays HERE rather than moving with the registry, because what it
     * overrides is the {@code activeFunctions} check, and that set is this class's own bookkeeping.
     */
    public void registerReadyFunctionCodesOnProcessor(String functionCode, Long processorId, boolean force) {
        if (!force && !isActiveFunction(functionCode)) {
            return;
        }
        executionGateService.recordFunctionReadiness(functionCode, processorId);
    }

    private static boolean isActiveFunction(String functionCode) {
        readLock.lock();
        try {
            return activeFunctions.contains(functionCode);
        } finally {
            readLock.unlock();
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void activateFunctions(SourceCodeGraph scg) {
        Set<String> funcCodes = collectFunctionCodes(scg);
        registerCodes(funcCodes, false);
    }

    private void registerCodes(Set<String> funcCodes, boolean clean) {
        for (String funcCode : funcCodes) {
            writeLock.lock();
            try {
                activeFunctions.add(funcCode);
            } finally {
                writeLock.unlock();
            }
            executionGateService.seedFunctionReadiness(funcCode);
        }
        if (clean) {
            List<String> forDeletion = new ArrayList<>(100);
            readLock.lock();
            try {
                for (String activeFunction : activeFunctions) {
                    if (!funcCodes.contains(activeFunction)) {
                        forDeletion.add(activeFunction);
                    }
                }
            } finally {
                readLock.unlock();
            }
            if (!forDeletion.isEmpty()) {
                writeLock.lock();
                try {
                    for (String funcCode : forDeletion) {
                        activeFunctions.remove(funcCode);
                    }
                } finally {
                    writeLock.unlock();
                }
                for (String funcCode : forDeletion) {
                    executionGateService.forgetFunctionReadiness(funcCode);
                }
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Async
    @EventListener
    public void registerFunctionCodesForStartedExecContext(RegisterFunctionCodesForStartedExecContextEvent event) {
        final SourceCodeImpl sc = sourceCodeRepository.findByIdExecContextId(event.execContextId());
        if (sc==null) {
            return;
        }
        SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);
        Set<String> funcCodes = collectFunctionCodes(scg);
        registerCodes(funcCodes, false);
    }

    private static Set<String> collectFunctionCodes(SourceCodeGraph scg) {
        Set<String> codes = new HashSet<>();
        for (ExecContextParamsYaml.Process process : scg.processes) {
            collectFunctionCodesForProcess(codes, process);
        }
        return codes;
    }

    @SuppressWarnings("ConstantValue")
    public static void collectFunctionCodesForProcess(Set<String> codes, ExecContextParamsYaml.Process process) {
        if (process.function !=null && process.function.context==EnumsApi.FunctionExecContext.external) {
            codes.add(process.function.code);
        }
    }

    public void updateActiveFunctions(Set<String> funcCodes) {

    }

    public void collectActiveFunctionCodes() {
        List<Long> sourceCodeIds = execContextRepository.findAllSourceCodeIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        Set<String> funcCodes = new HashSet<>();
        for (Long sourceCodeId : sourceCodeIds) {
            SourceCodeImpl sc = sourceCodeRepository.findByIdNullable(sourceCodeId);
            if (sc==null) {
                continue;
            }
            var scspy = sc.getSourceCodeStoredParamsYaml();
            SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);
            funcCodes.addAll(collectFunctionCodes(scg));
        }
        registerCodes(funcCodes, true);
    }

}
