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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import ai.metaheuristic.ai.dispatcher.data.GateData;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.event.events.ExecutionGateChangedEvent;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecutionGateRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Owns the question "may work be handed out for this key right now?".
 *
 * <p>The shape is a component that is QUERIED constantly and WRITTEN rarely, so the two paths are
 * built differently:
 *
 * <ul>
 * <li><b>Reads are memory only.</b> {@link #blockedUntil} never touches the database. It is called
 *     for every candidate on every poll, and a query there would put the database on the hot path of
 *     task assignment.</li>
 * <li><b>Writes are database first.</b> {@link #quarantine} commits the row and lets the post-commit
 *     event update memory. Memory is a cache of committed truth, never the origin of it — writing
 *     memory first would mean a rolled-back transaction leaves a block that no row justifies, and
 *     nothing would ever clear it.</li>
 * </ul>
 *
 * <p>The map here holds DURABLE decisions only, loaded from one table. Facts a Processor reports
 * about itself belong in a separate structure with its own lifetime: those are rebuilt from the
 * Processors on reconnect and backed by no table, so mixing them into a map loaded from the database
 * at startup would confuse a decision with an observation.
 *
 * <p>Error code prefix: {@code 01.320.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecutionGateService {

    private final ExecutionGateRepository executionGateRepository;
    private final ExecutionGateTxService executionGateTxService;
    private final FunctionService functionService;
    private final ExecContextCache execContextCache;

    /** Durable decisions, keyed by what they cover. Mutated only by the post-commit listener below. */
    private final Map<GateData.GateKey, GateData.GateRecord> records = new ConcurrentHashMap<>();

    /**
     * Reported facts: which Processors have said they are ready to run which Function.
     *
     * <p>❗ A SEPARATE structure from {@link #records}, and not by accident. Those are decisions this
     * dispatcher made and committed; these are claims the Processors made about themselves. Mixing
     * them would put something rebuilt on every reconnect into a map loaded from the database at
     * startup, where a stale copy could only ever be wrong — the Processor is the authority and
     * re-reports.
     *
     * <p>Entries expire two hours after they were last consulted, which is why this is a
     * {@code LinkedHashMap} with an eviction rule rather than a plain map: a fleet that churns
     * Processors would otherwise grow this without bound.
     */
    private final LinkedHashMap<String, GateData.FunctionReadiness> functionReadiness = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GateData.FunctionReadiness> eldest) {
            return ExecutionGateUtils.readinessEntryExpired(eldest.getValue().mills, System.currentTimeMillis());
        }
    };

    private final ReentrantReadWriteLock readinessLock = new ReentrantReadWriteLock();

    /**
     * A Function's declared analyzer rules, by Function code.
     *
     * <p>Never invalidated, and it does not need to be: a Function's code carries its version, so one
     * code always names the same content. Correcting a rule means publishing a new version, which is a
     * new code and therefore a new entry.
     *
     * <p>❗ The value is a private deep copy, NOT the list inside the parsed descriptor — see
     * {@link ExecutionGateUtils#copyAnalyzers}. This is read on the recovery path, once per failed Task
     * per pass, which is why it is cached at all.
     */
    private final Map<String, List<FunctionConfigYaml.Analyzer>> analyzersByFunctionCode = new ConcurrentHashMap<>();

    /**
     * The company an ExecContext belongs to. Cached for the same reason as the analyzers above: this is
     * consulted for every candidate Task on every poll, and an ExecContext's company never changes.
     */
    private final Map<Long, Long> companyIdByExecContextId = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCommittedRecords() {
        try {
            final long now = System.currentTimeMillis();
            final List<ExecutionGate> live = executionGateRepository.findAllLive(now);
            for (ExecutionGate gate : live) {
                final EnumsApi.GateScope scope = toScope(gate.scope);
                if (scope == null) {
                    continue;
                }
                records.put(new GateData.GateKey(scope, gate.refKey),
                        new GateData.GateRecord(scope, gate.refKey, gate.blockedUntil, gate.reasonCode));
            }
            log.info("01.320.020 loaded {} live execution gate record(s)", records.size());
        }
        catch (Throwable th) {
            // a dispatcher that cannot read this table must still start: an empty map means nothing
            // is blocked, which is the permissive failure and the correct one - the alternative is a
            // dispatcher that refuses to boot because of a table nothing has written to yet
            log.error("01.320.040 can't load execution gate records, continuing with none", th);
        }
    }

    /**
     * May this core be given this Task? Stateless checks only for now — the durable blocks this
     * component already holds are consulted by the caller separately until the assignment loop is
     * redirected here.
     */
    public GateData.Admission admit(ProcessorData.ProcessorAndCoreParams pacp, TaskQueue.QueuedTask queuedTask, boolean isAcceptOnlySigned) {
        return ExecutionGateUtils.admit(pacp, queuedTask, isAcceptOnlySigned,
                fc -> functionService.trusted(fc.sourcing, fc.git));
    }

    /**
     * Is this Task covered by a live block on its Function or on the API key it would spend?
     *
     * <p>This is where a block opened from a Function's own console analysis actually withholds work.
     * The recovery pass opens the block and resets the Task normally; the Task then sits in the queue
     * and is passed over here until the block clears.
     *
     * <p>❗ Both keys are built by {@link ExecutionGateUtils#refKeyFor}, the same function the recovery
     * pass uses to open the block. A second, hand-rolled key here would be the worst kind of bug —
     * blocks would be opened under one key and looked up under another, so nothing would ever be
     * withheld and no test of either side alone would notice.
     */
    public GateData.Admission admitQuarantine(Long execContextId, TaskParamsYaml tpy) {
        final String functionKey = ExecutionGateUtils.refKeyFor(EnumsApi.GateScope.function, null, null, tpy);
        if (functionKey != null && blockedUntil(EnumsApi.GateScope.function, functionKey) != null) {
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.function_is_quarantined);
        }
        if (tpy.task.function.api != null) {
            final String apiKey = ExecutionGateUtils.refKeyFor(EnumsApi.GateScope.api, companyIdOf(execContextId), null, tpy);
            if (apiKey != null && blockedUntil(EnumsApi.GateScope.api, apiKey) != null) {
                return GateData.Admission.rejected(Enums.TaskRejectingStatus.api_is_quarantined);
            }
        }
        return GateData.Admission.ADMITTED;
    }

    @Nullable
    private Long companyIdOf(Long execContextId) {
        // a null result is not cached - ConcurrentHashMap.computeIfAbsent doesn't store one - which is
        // the behaviour wanted: a missing ExecContext is abnormal and shouldn't be remembered
        return companyIdByExecContextId.computeIfAbsent(execContextId, id -> {
            final ExecContextImpl ec = execContextCache.findById(id, true);
            return ec == null ? null : ec.companyId;
        });
    }

    /**
     * The stateless half of {@link #admit}. Called separately by the assignment loop because two
     * other conditions are evaluated between this half and the params-version half.
     */
    public GateData.Admission admitStatelessFacts(ProcessorData.ProcessorAndCoreParams pacp, TaskQueue.QueuedTask queuedTask, boolean isAcceptOnlySigned) {
        return ExecutionGateUtils.admitStatelessFacts(pacp, queuedTask, isAcceptOnlySigned,
                fc -> functionService.trusted(fc.sourcing, fc.git));
    }

    /** The params-version half of {@link #admit}. */
    public GateData.Admission admitParamsVersion(ProcessorData.ProcessorAndCoreParams pacp, TaskQueue.QueuedTask queuedTask) {
        return ExecutionGateUtils.admitParamsVersion(pacp, queuedTask);
    }

    /**
     * Has this Processor reported every Function this Task needs — main, pre and post — as ready?
     *
     * <p>A REPORTED FACT, deliberately not part of {@link #admit}: a Processor lacking a Function is
     * grounds to skip this Task for this Processor, never to withhold work from it more broadly.
     */
    public boolean allFunctionsReady(Long processorId, TaskParamsYaml tpy) {
        if (!isProcessorReadyLogged(tpy.task.function.code, processorId)) {
            return false;
        }
        for (TaskParamsYaml.FunctionConfig preFunction : tpy.task.preFunctions) {
            if (!isProcessorReadyLogged(preFunction.code, processorId)) {
                return false;
            }
        }
        for (TaskParamsYaml.FunctionConfig postFunction : tpy.task.postFunctions) {
            if (!isProcessorReadyLogged(postFunction.code, processorId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isProcessorReadyLogged(String functionCode, Long processorId) {
        final boolean ready = isProcessorReady(functionCode, processorId);
        if (!ready) {
            log.debug("01.320.100 function {} at processor #{} isn't ready.", functionCode, processorId);
        }
        return ready;
    }

    /**
     * The deadline covering a key, or null when nothing does. In-memory, no database access.
     */
    @Nullable
    public Long blockedUntil(EnumsApi.GateScope scope, String refKey) {
        return ExecutionGateUtils.blockedUntil(records, scope, refKey, System.currentTimeMillis());
    }

    /**
     * The live record covering a key, or null. Where {@link #blockedUntil} answers "for how long",
     * this answers "and why" — which is what a human looking at a stalled Processor actually needs.
     */
    public GateData.@Nullable GateRecord liveRecord(EnumsApi.GateScope scope, String refKey) {
        final GateData.GateRecord record = records.get(new GateData.GateKey(scope, refKey));
        return (record != null && ExecutionGateUtils.isLive(record.blockedUntil(), System.currentTimeMillis())) ? record : null;
    }

    /**
     * Blocks a key until a deadline, extending an existing block rather than stacking a second.
     *
     * <p>The decision of whether there is anything to do happens here, before a transaction is
     * opened: re-blocking a key that is already blocked for longer is common — every failing sibling
     * Task of a quarantined Function asks for it — and it must not cost a write each time.
     */
    public void quarantine(EnumsApi.GateScope scope, String refKey, long blockedUntil, String reasonCode, ExecutionGateParamsYaml params) {
        final long now = System.currentTimeMillis();
        if (!ExecutionGateUtils.isLive(blockedUntil, now)) {
            log.warn("01.320.060 refusing to open an already-expired execution gate, scope: {}, refKey: {}, blockedUntil: {}",
                    scope, refKey, blockedUntil);
            return;
        }
        final GateData.GateRecord existing = records.get(new GateData.GateKey(scope, refKey));
        if (existing != null && ExecutionGateUtils.isLive(existing.blockedUntil(), now) && existing.blockedUntil() >= blockedUntil) {
            // already covered for at least as long; nothing to commit
            return;
        }
        executionGateTxService.createOrExtend(scope, refKey, ExecutionGateUtils.resolveDeadline(existing, blockedUntil, now), reasonCode, params);
    }

    /**
     * Clears a block ahead of its deadline. A key that is not blocked costs nothing.
     */
    public void release(EnumsApi.GateScope scope, String refKey) {
        if (!records.containsKey(new GateData.GateKey(scope, refKey))) {
            return;
        }
        executionGateTxService.delete(scope, refKey);
    }

    /**
     * The ONLY writer of the in-memory map. It runs after the transaction committed, so anything it
     * applies is already durable.
     */
    @Async
    @EventListener
    public void handleExecutionGateChangedEvent(ExecutionGateChangedEvent event) {
        if (event.removed) {
            records.remove(new GateData.GateKey(event.scope, event.refKey));
            return;
        }
        records.put(new GateData.GateKey(event.scope, event.refKey),
                new GateData.GateRecord(event.scope, event.refKey, event.blockedUntil, event.reasonCode));
    }

    /**
     * Records that a Processor has this Function ready.
     *
     * @return true when the pair was NOT registered before. ❗ The caller uses this to decide whether
     *         to re-notify Processors, so a {@code void} signature here would silently drop the wake-up
     *         that lets Tasks waiting on a newly-ready Function get picked up.
     */
    public boolean recordFunctionReadiness(String functionCode, Long processorId) {
        readinessLock.writeLock().lock();
        try {
            return functionReadiness.computeIfAbsent(functionCode, o -> new GateData.FunctionReadiness()).ids.add(processorId);
        } finally {
            readinessLock.writeLock().unlock();
        }
    }

    /**
     * The analyzer rules declared by a Function, or an empty list when it declares none.
     *
     * <p>⚠️ A Function that cannot be found is treated as declaring none rather than as an error. This
     * runs while handling a failure that has already happened, and a Function deleted since the Task
     * was created must not turn one failed Task into a failed recovery pass for the whole ExecContext.
     */
    public List<FunctionConfigYaml.Analyzer> analyzersOf(String functionCode) {
        return analyzersByFunctionCode.computeIfAbsent(functionCode, this::loadAnalyzers);
    }

    private List<FunctionConfigYaml.Analyzer> loadAnalyzers(String functionCode) {
        final ai.metaheuristic.ai.dispatcher.beans.Function function = functionService.findByCode(functionCode);
        if (function == null) {
            log.warn("01.320.120 function {} wasn't found while reading its analyzers", functionCode);
            return List.of();
        }
        return ExecutionGateUtils.copyAnalyzers(function.getFunctionConfigYaml().function.analyzers);
    }

    /** Has this Processor reported this Function ready? Consulting an entry also renews its lifetime. */
    public boolean isProcessorReady(String functionCode, Long processorId) {
        readinessLock.readLock().lock();
        try {
            final GateData.FunctionReadiness readiness = functionReadiness.get(functionCode);
            if (readiness == null) {
                return false;
            }
            readiness.mills = System.currentTimeMillis();
            return readiness.contains(processorId);
        } finally {
            readinessLock.readLock().unlock();
        }
    }

    /** Creates an empty entry for a Function so its lifetime starts now. No Processor is marked ready. */
    public void seedFunctionReadiness(String functionCode) {
        readinessLock.writeLock().lock();
        try {
            functionReadiness.computeIfAbsent(functionCode, o -> new GateData.FunctionReadiness());
        } finally {
            readinessLock.writeLock().unlock();
        }
    }

    /** Drops everything reported about a Function, for when it stops being active. */
    public void forgetFunctionReadiness(String functionCode) {
        readinessLock.writeLock().lock();
        try {
            functionReadiness.remove(functionCode);
        } finally {
            readinessLock.writeLock().unlock();
        }
    }

    /** How many Functions have a readiness entry right now. For diagnostics and tests only. */
    public int functionReadinessCount() {
        readinessLock.readLock().lock();
        try {
            return functionReadiness.size();
        } finally {
            readinessLock.readLock().unlock();
        }
    }

    /**
     * Every block in force right now, newest deadline last.
     *
     * <p>Reads the in-memory copy, not the table: this answers an admin screen, and the memory is a
     * cache of committed truth anyway, so a database round trip would buy nothing but latency.
     * Expired entries are filtered rather than removed — a read must not mutate.
     */
    public List<GateData.GateRecord> liveRecords() {
        final long now = System.currentTimeMillis();
        return records.values().stream()
                .filter(r -> ExecutionGateUtils.isLive(r.blockedUntil(), now))
                .sorted(Comparator.comparingLong(GateData.GateRecord::blockedUntil))
                .toList();
    }

    /**
     * Drops what has expired, from memory and from the table.
     *
     * <p>Correctness does not depend on this running — every read already treats an expired record as
     * absent — so it is housekeeping, and a failure is logged rather than propagated. What it prevents
     * is unbounded growth of a table that is read in full at every startup.
     */
    public void sweepExpired() {
        final long now = System.currentTimeMillis();
        final int droppedFromMemory = ExecutionGateUtils.dropExpired(records, now);
        try {
            final int deleted = executionGateTxService.deleteExpired(now);
            if (droppedFromMemory > 0 || deleted > 0) {
                log.info("01.320.140 execution gate sweep: {} dropped from memory, {} rows deleted", droppedFromMemory, deleted);
            }
        }
        catch (Throwable th) {
            log.error("01.320.160 execution gate sweep failed, will retry on the next pass", th);
        }
    }

    /** How many durable records are held right now, expired ones included. For diagnostics only. */
    public int recordCount() {
        return records.size();
    }

    private static EnumsApi.@Nullable GateScope toScope(String scope) {
        try {
            return EnumsApi.GateScope.valueOf(scope);
        }
        catch (IllegalArgumentException e) {
            log.error("01.320.080 unknown execution gate scope in db: {}", scope);
            return null;
        }
    }
}
