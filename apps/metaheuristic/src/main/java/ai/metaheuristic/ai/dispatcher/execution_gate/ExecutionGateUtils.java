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
import ai.metaheuristic.ai.dispatcher.data.GateData;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The decision arithmetic behind the admission gate, as pure functions over a map that is passed in.
 *
 * <p>Kept static and Spring-less so the rules that matter — when a block is still live, what deadline
 * results from blocking a key that is already blocked — can be tested without a context. The owning
 * bean holds the map; nothing here holds state.
 *
 * <p>Error code prefix: {@code 01.322.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Slf4j
public class ExecutionGateUtils {

    /**
     * A block is live while now has not reached its deadline. The boundary is deliberately exclusive:
     * at exactly {@code blockedUntil} the block is over, which makes a zero-length block a no-op
     * rather than something that lingers for one millisecond.
     */
    public static boolean isLive(long blockedUntil, long now) {
        return blockedUntil > now;
    }

    /**
     * The deadline that results from blocking a key. The longer of the two always wins, so a short
     * block can never shorten a long one that is already in force — an analyzer declaring 30 seconds
     * must not release a key an operator blocked for a day.
     */
    public static long resolveDeadline(GateData.@Nullable GateRecord existing, long requested, long now) {
        if (existing == null || !isLive(existing.blockedUntil(), now)) {
            return requested;
        }
        return Math.max(existing.blockedUntil(), requested);
    }

    /**
     * The deadline covering a key right now, or null when nothing does. An entry whose deadline has
     * passed is treated as absent; it is not removed here, because a read must not mutate.
     */
    @Nullable
    public static Long blockedUntil(Map<GateData.GateKey, GateData.GateRecord> records, EnumsApi.GateScope scope, String refKey, long now) {
        final GateData.GateRecord record = records.get(new GateData.GateKey(scope, refKey));
        if (record == null || !isLive(record.blockedUntil(), now)) {
            return null;
        }
        return record.blockedUntil();
    }

    /** How long a readiness entry survives after it was last consulted. */
    public static final long READINESS_TTL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(2);

    /**
     * Whether a readiness entry has gone stale. A reported fact has a lifetime because the Processor
     * that reported it may simply be gone — and unlike a durable block, nothing will ever come along
     * to retract it.
     */
    public static boolean readinessEntryExpired(long lastTouchedMills, long now) {
        return now - lastTouchedMills > READINESS_TTL_MILLIS;
    }

    /**
     * Records a block in memory, extending an existing live one rather than adding a second.
     * Returns the record now in force.
     */
    public static GateData.GateRecord putOrExtend(
            Map<GateData.GateKey, GateData.GateRecord> records,
            EnumsApi.GateScope scope, String refKey, long blockedUntil, String reasonCode, long now) {

        final GateData.GateKey key = new GateData.GateKey(scope, refKey);
        final long deadline = resolveDeadline(records.get(key), blockedUntil, now);
        final GateData.GateRecord record = new GateData.GateRecord(scope, refKey, deadline, reasonCode);
        records.put(key, record);
        return record;
    }

    /**
     * Drops every entry whose deadline has passed. Returns how many went.
     */
    public static int dropExpired(Map<GateData.GateKey, GateData.GateRecord> records, long now) {
        final int before = records.size();
        records.values().removeIf(r -> !isLive(r.blockedUntil(), now));
        return before - records.size();
    }

    /**
     * An independent, immutable copy of a Function's declared analyzer rules.
     *
     * <p>❗ Every element is a fresh {@link FunctionConfigYaml.Analyzer}, never the instance inside the
     * parsed descriptor. Two reasons, both of which bite only later: holding the descriptor's own list
     * keeps the entire parsed {@code FunctionConfigYaml} — targets, checksums, metas — reachable for as
     * long as the cache lives, and it leaves the cached rules mutable through anything else holding the
     * same descriptor.
     *
     * <p>Returns an empty list rather than null for "declares none", so callers have one shape to
     * handle.
     */
    public static List<FunctionConfigYaml.Analyzer> copyAnalyzers(@Nullable List<FunctionConfigYaml.Analyzer> declared) {
        if (declared == null || declared.isEmpty()) {
            return List.of();
        }
        final List<FunctionConfigYaml.Analyzer> copy = new ArrayList<>(declared.size());
        for (FunctionConfigYaml.Analyzer analyzer : declared) {
            copy.add(analyzer.clone());
        }
        return List.copyOf(copy);
    }

    /**
     * What a block of this scope is keyed on, for this Task. Null when the key cannot be built, which
     * is a reason to skip opening the block rather than to fail.
     *
     * <p>❗ The {@code api} key joins the COMPANY to the key code. An API key is a per-tenant credential:
     * keying on the code alone would let one tenant exhausting its quota withhold work from every other
     * tenant using the same named key. The company id comes from the ExecContext — never from
     * {@code TaskParamsYaml.companyId}, which nothing writes and which is {@code 0L} on every Task, so
     * every tenant would collapse onto one key.
     */
    @Nullable
    public static String refKeyFor(EnumsApi.GateScope scope, @Nullable Long companyId, @Nullable Long processorId, TaskParamsYaml tpy) {
        return switch (scope) {
            case function -> tpy.task.function.code;
            case api -> (tpy.task.function.api == null || companyId == null)
                    ? null
                    : companyId + ":" + tpy.task.function.api.keyCode;
            case processor -> processorId == null ? null : String.valueOf(processorId);
            // dispatcher-only; a descriptor declaring one is rejected at load, so this is unreachable
            // from an analyzer hit and is here only so the switch stays exhaustive
            case global, company -> null;
        };
    }

    /**
     * May this core be given this Task, judged only on facts computable right now?
     *
     * <p>Every check here is stateless — it compares what the Task declares against what the
     * Processor reports, and nothing else. ❗ None of them may open a durable block: a Task-scoped
     * misconfiguration is grounds to skip that Task, never to withhold work from the Processor. That
     * confusion is the root cause of the silent stalls this component exists to end, so it is worth
     * stating where the checks live rather than only where they are called from.
     *
     * <p>The order matches the existing filter chain exactly, so redirecting the assignment loop onto
     * this method produces the same reason for the same Task.
     *
     * @param trustedFunc supplied by the caller because trust is configuration, not a property of the
     *                    Task — passing it in is what keeps this method testable without a context
     */
    public static GateData.Admission admit(
            ProcessorData.ProcessorAndCoreParams pacp,
            TaskQueue.QueuedTask queuedTask,
            boolean isAcceptOnlySigned,
            Predicate<TaskParamsYaml.FunctionConfig> trustedFunc) {

        final GateData.Admission statelessFacts = admitStatelessFacts(pacp, queuedTask, isAcceptOnlySigned, trustedFunc);
        if (!statelessFacts.admitted()) {
            return statelessFacts;
        }
        return admitParamsVersion(pacp, queuedTask);
    }

    /**
     * The five checks that compare what the Task DECLARES against what the Processor REPORTS.
     *
     * <p>Split from the params-version check below because the existing filter chain evaluates two
     * other conditions between the two — whether the Processor has the Functions ready, and whether it
     * has quota left — and a Task failing both must still report the reason it reported before.
     */
    public static GateData.Admission admitStatelessFacts(
            ProcessorData.ProcessorAndCoreParams pacp,
            TaskQueue.QueuedTask queuedTask,
            boolean isAcceptOnlySigned,
            Predicate<TaskParamsYaml.FunctionConfig> trustedFunc) {

        final TaskParamsYaml tpy = queuedTask.taskParamYaml;

        if (TaskUtils.gitUnavailable(tpy.task, pacp.psy().gitStatusInfo.status != EnumsApi.GitStatus.installed)) {
            log.warn("01.322.040 can't give task #{} to core #{}, this processor has no working git, git status info: {}",
                    queuedTask.taskId, pacp.coreId(), pacp.psy().gitStatusInfo);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.git_required);
        }

        if (!CollectionUtils.checkTagAllowed(queuedTask.tag, pacp.csy().tags)) {
            log.debug("01.322.060 task #{} carries tag '{}', core #{} declares '{}'",
                    queuedTask.taskId, queuedTask.tag, pacp.coreId(), pacp.csy().tags);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.tags_arent_allowed);
        }

        if (!S.b(tpy.task.function.env) && pacp.psy().env != null
                && pacp.psy().env.getEnvs().get(tpy.task.function.env) == null) {
            log.error("01.322.080 can't give task #{} to core #{}, this processor has no interpreter for the function's env {}",
                    queuedTask.taskId, pacp.coreId(), tpy.task.function.env);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.interpreter_is_undefined);
        }

        final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(tpy.task.function.metas);
        if (pacp.psy().os != null && !supportedOS.isEmpty() && !supportedOS.contains(pacp.psy().os)) {
            log.info("01.322.100 can't give task #{} to core #{}, this processor doesn't support the required OS. processor: {}, function: {}",
                    queuedTask.taskId, pacp.coreId(), pacp.psy().os, supportedOS);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.not_supported_operating_system);
        }

        if (isAcceptOnlySigned && !trustedFunc.test(tpy.task.function)) {
            if (tpy.task.function.checksumMap == null
                    || tpy.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                log.warn("01.322.120 function with code {} wasn't signed", tpy.task.function.getCode());
                return GateData.Admission.rejected(Enums.TaskRejectingStatus.accept_only_signed);
            }
        }

        return GateData.Admission.ADMITTED;
    }

    /**
     * Whether the Task's params can be expressed at the version this Processor understands.
     *
     * <p>The stored document may be an older version than the one just parsed, so the version to
     * compare against comes from the RAW params rather than from the parsed object, whose version is
     * always the latest by the time it is in hand.
     */
    public static GateData.Admission admitParamsVersion(ProcessorData.ProcessorAndCoreParams pacp, TaskQueue.QueuedTask queuedTask) {
        if (queuedTask.task == null) {
            return GateData.Admission.ADMITTED;
        }
        try {
            final ParamsVersion v = YamlForVersioning.getParamsVersion(queuedTask.task.getParams());
            if (v.getActualVersion() != pacp.psy().taskParamsVersion) {
                //noinspection unused
                final String ignored = TaskParamsYamlUtils.UTILS.toStringAsVersion(queuedTask.taskParamYaml, pacp.psy().taskParamsVersion);
            }
        }
        catch (DowngradeNotSupportedException e) {
            log.warn("01.322.020 task #{} can't be given to core #{}, downgrade to taskParams level {} isn't supported",
                    queuedTask.taskId, pacp.coreId(), pacp.psy().taskParamsVersion);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.downgrade_not_supported);
        }
        return GateData.Admission.ADMITTED;
    }
}
