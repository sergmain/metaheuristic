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
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

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
    public static Long blockedUntil(Map<GateData.GateKey, GateData.GateRecord> records, Enums.GateScope scope, String refKey, long now) {
        final GateData.GateRecord record = records.get(new GateData.GateKey(scope, refKey));
        if (record == null || !isLive(record.blockedUntil(), now)) {
            return null;
        }
        return record.blockedUntil();
    }

    /**
     * Records a block in memory, extending an existing live one rather than adding a second.
     * Returns the record now in force.
     */
    public static GateData.GateRecord putOrExtend(
            Map<GateData.GateKey, GateData.GateRecord> records,
            Enums.GateScope scope, String refKey, long blockedUntil, String reasonCode, long now) {

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

        final TaskParamsYaml tpy = queuedTask.taskParamYaml;

        if (TaskUtils.gitUnavailable(tpy.task, pacp.psy().gitStatusInfo.status != EnumsApi.GitStatus.installed)) {
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.git_required);
        }

        if (!CollectionUtils.checkTagAllowed(queuedTask.tag, pacp.csy().tags)) {
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.tags_arent_allowed);
        }

        if (!S.b(tpy.task.function.env) && pacp.psy().env != null
                && pacp.psy().env.getEnvs().get(tpy.task.function.env) == null) {
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.interpreter_is_undefined);
        }

        final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(tpy.task.function.metas);
        if (pacp.psy().os != null && !supportedOS.isEmpty() && !supportedOS.contains(pacp.psy().os)) {
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.not_supported_operating_system);
        }

        if (isAcceptOnlySigned && !trustedFunc.test(tpy.task.function)) {
            if (tpy.task.function.checksumMap == null
                    || tpy.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                return GateData.Admission.rejected(Enums.TaskRejectingStatus.accept_only_signed);
            }
        }

        // the stored document may be an older version than the one just parsed, so the version to
        // compare against comes from the raw params rather than from the parsed object, whose version
        // is always the latest by the time it is in hand
        if (queuedTask.task != null) {
            try {
                final ParamsVersion v = YamlForVersioning.getParamsVersion(queuedTask.task.getParams());
                if (v.getActualVersion() != pacp.psy().taskParamsVersion) {
                    //noinspection unused
                    final String ignored = TaskParamsYamlUtils.UTILS.toStringAsVersion(tpy, pacp.psy().taskParamsVersion);
                }
            }
            catch (DowngradeNotSupportedException e) {
                log.warn("01.322.020 task #{} can't be given to core #{}, downgrade to taskParams level {} isn't supported",
                        queuedTask.taskId, pacp.coreId(), pacp.psy().taskParamsVersion);
                return GateData.Admission.rejected(Enums.TaskRejectingStatus.downgrade_not_supported);
            }
        }

        return GateData.Admission.ADMITTED;
    }
}
