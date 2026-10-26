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
import ai.metaheuristic.ai.yaml.api_analyzers.ApiAnalyzersYamlUtils;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
     * Why a Processor should be shown as blacklisted, or null when it should not.
     *
     * <p>❗ A dispatcher too old to talk to this Processor WINS over a quarantine when both apply. A
     * quarantine expires on its own; a version mismatch never does, so reporting the expiring one would
     * send an operator to wait for a deadline that fixes nothing.
     */
    public static GateData.@Nullable Blacklist blacklistOf(
            int processorTaskParamsVersion, int dispatcherTaskParamsVersion,
            GateData.@Nullable GateRecord liveRecord, long now) {

        if (processorTaskParamsVersion > dispatcherTaskParamsVersion) {
            return new GateData.Blacklist(
                    "809.400 Dispatcher is too old and can't communicate to this processor, needs to be upgraded", 0L);
        }
        if (liveRecord != null) {
            return new GateData.Blacklist(
                    "809.420 Processor is quarantined, reason: " + liveRecord.reasonCode(),
                    liveRecord.blockedUntil() - now);
        }
        return null;
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
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.git_required, String.valueOf(pacp.psy().gitStatusInfo.status));
        }

        if (!CollectionUtils.checkTagAllowed(queuedTask.tag, pacp.csy().tags)) {
            log.debug("01.322.060 task #{} carries tag '{}', core #{} declares '{}'",
                    queuedTask.taskId, queuedTask.tag, pacp.coreId(), pacp.csy().tags);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.tags_arent_allowed, queuedTask.tag);
        }

        if (!S.b(tpy.task.function.env) && pacp.psy().env != null
                && pacp.psy().env.getEnvs().get(tpy.task.function.env) == null) {
            log.error("01.322.080 can't give task #{} to core #{}, this processor has no interpreter for the function's env {}",
                    queuedTask.taskId, pacp.coreId(), tpy.task.function.env);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.interpreter_is_undefined, tpy.task.function.env);
        }

        final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(tpy.task.function.metas);
        if (pacp.psy().os != null && !supportedOS.isEmpty() && !supportedOS.contains(pacp.psy().os)) {
            log.info("01.322.100 can't give task #{} to core #{}, this processor doesn't support the required OS. processor: {}, function: {}",
                    queuedTask.taskId, pacp.coreId(), pacp.psy().os, supportedOS);
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.not_supported_operating_system, pacp.psy().os + " vs " + supportedOS);
        }

        if (isAcceptOnlySigned && !trustedFunc.test(tpy.task.function)) {
            if (tpy.task.function.checksumMap == null
                    || tpy.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                log.warn("01.322.120 function with code {} wasn't signed", tpy.task.function.getCode());
                return GateData.Admission.rejected(Enums.TaskRejectingStatus.accept_only_signed, tpy.task.function.getCode());
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
            return GateData.Admission.rejected(Enums.TaskRejectingStatus.downgrade_not_supported, "taskParams v" + pacp.psy().taskParamsVersion);
        }
        return GateData.Admission.ADMITTED;
    }

    /** The classpath resource holding the analyzer rules MH ships for every Function. */
    public static final String MH_API_ANALYZERS_YAML = "mh-api-analyzers.yaml";

    /**
     * The rules of an mh-api-analyzers.yaml document, as private deep copies (see {@link #copyAnalyzers}).
     *
     * <p>Validated on the way in, with the rules a Function descriptor is held to - a broken rule throws
     * here rather than loading cleanly and never firing.
     */
    public static List<FunctionConfigYaml.Analyzer> parseApiAnalyzers(String yaml) {
        return copyAnalyzers(ApiAnalyzersYamlUtils.BASE_YAML_UTILS.to(yaml).analyzers);
    }

    /**
     * The rules of the named classpath resource, read once when the Dispatcher starts.
     *
     * <p>❗ A missing or broken resource FAILS the start rather than being treated as "no rules". The file
     * ships inside MH, so either outcome is a build defect, and a Dispatcher that quietly runs without the
     * rules would retry into the very walls they exist to recognise.
     */
    public static List<FunctionConfigYaml.Analyzer> readApiAnalyzers(String resourceName) {
        try (InputStream is = ExecutionGateUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IllegalStateException("01.322.140 classpath resource " + resourceName + " wasn't found");
            }
            return parseApiAnalyzers(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Throwable th) {
            throw new IllegalStateException("01.322.160 classpath resource " + resourceName + " can't be read: " + th.getMessage(), th);
        }
    }

    /**
     * Every analyzer rule that applies to one Function, in the order they are consulted.
     *
     * <p>⚠️ CONCATENATED, not merged - every rule from every source applies and none is discarded; a label
     * is not an identity. The order is the precedence, because first hit wins downstream:
     * <ol>
     * <li>the operator's rules ({@code mh.dispatcher.execution-gate.analyzers}) - whoever runs this
     *     Dispatcher has the last word;</li>
     * <li>the Function's own rules - its author knows its console best;</li>
     * <li>the rules MH ships for every Function (mh-api-analyzers.yaml) - the most general, so consulted
     *     last, where the two above can still refine what the same console text means.</li>
     * </ol>
     *
     * <p>❗ One exception to "none is discarded": a Function rule NAMED like a rule MH ships is skipped - see
     * {@link #shadowedByMhRules}. The name of a shipped rule is reserved: a Function cannot redefine it,
     * and the shipped rule applies in its place. Operator rules are not affected.
     */
    public static List<FunctionConfigYaml.Analyzer> joinAnalyzers(
            List<FunctionConfigYaml.Analyzer> dispatcherLevel,
            List<FunctionConfigYaml.Analyzer> functionLevel,
            List<FunctionConfigYaml.Analyzer> mhLevel) {
        final List<FunctionConfigYaml.Analyzer> shadowed = shadowedByMhRules(functionLevel, mhLevel);
        final List<FunctionConfigYaml.Analyzer> all = new ArrayList<>(dispatcherLevel.size() + functionLevel.size() + mhLevel.size());
        all.addAll(dispatcherLevel);
        functionLevel.stream().filter(a -> !shadowed.contains(a)).forEach(all::add);
        all.addAll(mhLevel);
        return List.copyOf(all);
    }

    /**
     * The Function's own rules whose name is also the name of a rule MH ships in mh-api-analyzers.yaml, in
     * the Function's order. These are skipped by {@link #joinAnalyzers}; the caller reports them.
     *
     * <p>Matched by NAME, whatever the rules contain - an identical copy and a redefinition are treated the
     * same, because a shipped rule's name is reserved. Every copy of a shadowed rule is skipped, including a
     * rule the Function declares twice.
     */
    public static List<FunctionConfigYaml.Analyzer> shadowedByMhRules(
            List<FunctionConfigYaml.Analyzer> functionLevel, List<FunctionConfigYaml.Analyzer> mhLevel) {
        if (functionLevel.isEmpty() || mhLevel.isEmpty()) {
            return List.of();
        }
        final Set<String> reserved = mhLevel.stream().map(a -> a.name).collect(Collectors.toSet());
        return functionLevel.stream().filter(a -> reserved.contains(a.name)).toList();
    }
}
