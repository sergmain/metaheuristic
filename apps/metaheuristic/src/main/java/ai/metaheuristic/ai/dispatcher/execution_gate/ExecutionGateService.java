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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import ai.metaheuristic.ai.dispatcher.data.GateData;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.event.events.ExecutionGateChangedEvent;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecutionGateRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Durable decisions, keyed by what they cover. Mutated only by the post-commit listener below. */
    private final Map<GateData.GateKey, GateData.GateRecord> records = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCommittedRecords() {
        try {
            final long now = System.currentTimeMillis();
            final List<ExecutionGate> live = executionGateRepository.findAllLive(now);
            for (ExecutionGate gate : live) {
                final Enums.GateScope scope = toScope(gate.scope);
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
     * The deadline covering a key, or null when nothing does. In-memory, no database access.
     */
    @Nullable
    public Long blockedUntil(Enums.GateScope scope, String refKey) {
        return ExecutionGateUtils.blockedUntil(records, scope, refKey, System.currentTimeMillis());
    }

    /**
     * Blocks a key until a deadline, extending an existing block rather than stacking a second.
     *
     * <p>The decision of whether there is anything to do happens here, before a transaction is
     * opened: re-blocking a key that is already blocked for longer is common — every failing sibling
     * Task of a quarantined Function asks for it — and it must not cost a write each time.
     */
    public void quarantine(Enums.GateScope scope, String refKey, long blockedUntil, String reasonCode, ExecutionGateParamsYaml params) {
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
    public void release(Enums.GateScope scope, String refKey) {
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

    /** How many durable records are held right now, expired ones included. For diagnostics only. */
    public int recordCount() {
        return records.size();
    }

    private static Enums.@Nullable GateScope toScope(String scope) {
        try {
            return Enums.GateScope.valueOf(scope);
        }
        catch (IllegalArgumentException e) {
            log.error("01.320.080 unknown execution gate scope in db: {}", scope);
            return null;
        }
    }
}
