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

package ai.metaheuristic.ai.dispatcher.monitoring;

import ai.metaheuristic.ai.Enums;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the admission gate has been refusing lately, and which of it a human should care about.
 *
 * <p>❗ Raw rejection counts are the wrong thing to show an admin, because most rejections are normal.
 * A Task skipped because it is already running is not a problem; a Task skipped because no Processor
 * has an interpreter for it is. So every reason is classified, and only two of the three classes
 * surface at all.
 *
 * <p>💡 The signal is class x persistence, not class alone. A {@code transient} reason sitting at a
 * level for the whole window has stopped being transient — {@code functions_not_ready} for fifteen
 * minutes means a download is failing, not that a Processor is warming up. Conversely a brief
 * {@code actionable} spike during a deploy is noise. {@link #actionableView} therefore ranks by
 * persistence within class, not by raw count.
 *
 * <p>In memory only, and deliberately: this is diagnostic furniture, not a record. A table would need
 * migration, retention, and a write on the hot path — for data whose value expires in minutes.
 *
 * <p>Error code prefix: {@code 01.326.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
public class GateMonitoring {

    public static final int WINDOW_BUCKETS = 15;
    public static final int MAX_KEYS_PER_BUCKET = 200;
    public static final int EXEMPLARS_PER_REASON = 10;

    /** How a reason should be surfaced. */
    public enum RejectionClass {
        /** Ordinary queue churn. Counted so totals stay honest, never shown. */
        benign,
        /** Expected and self-resolving — unless it persists across the window. */
        transient_,
        /** A human must change something. */
        actionable
    }

    private static final Set<Enums.TaskRejectingStatus> BENIGN = Set.of(
            Enums.TaskRejectingStatus.internal_task,
            Enums.TaskRejectingStatus.task_was_finished,
            Enums.TaskRejectingStatus.task_in_progress_already,
            Enums.TaskRejectingStatus.task_for_cache_checking,
            Enums.TaskRejectingStatus.task_must_be_in_none_state,
            Enums.TaskRejectingStatus.exec_context_not_started,
            Enums.TaskRejectingStatus.exec_context_stopped_or_finished,
            Enums.TaskRejectingStatus.queued_task_or_params_is_null);

    private static final Set<Enums.TaskRejectingStatus> TRANSIENT = Set.of(
            Enums.TaskRejectingStatus.not_enough_quotas,
            Enums.TaskRejectingStatus.functions_not_ready,
            Enums.TaskRejectingStatus.function_is_quarantined,
            Enums.TaskRejectingStatus.api_is_quarantined);

    public static RejectionClass classify(Enums.TaskRejectingStatus reason) {
        if (BENIGN.contains(reason)) {
            return RejectionClass.benign;
        }
        if (TRANSIENT.contains(reason)) {
            return RejectionClass.transient_;
        }
        // ❗ Unknown reasons default to actionable. A reason added later and forgotten here should show
        // up and be noticed, not vanish into the class that is never displayed.
        return RejectionClass.actionable;
    }

    /** One row of the admin view. */
    public record ReasonLevel(Enums.TaskRejectingStatus reason, RejectionClass rejectionClass,
                              long count, int bucketsPresent,
                              List<ExemplarRing.Exemplar> exemplars) {}

    private final Map<Enums.TaskRejectingStatus, RejectionCounters> countersByReason =
            new EnumMap<>(Enums.TaskRejectingStatus.class);
    private final Map<Enums.TaskRejectingStatus, ExemplarRing> exemplarsByReason =
            new EnumMap<>(Enums.TaskRejectingStatus.class);

    private final Object lock = new Object();

    /**
     * Records one rejection. Called per evaluation, so it does no allocation beyond the first sighting
     * of a reason or a function code.
     *
     * @param offendingValue the thing a human would need to see — the undeclared env, the unsupported
     *                       OS. Copied and truncated, never a reference to anything larger.
     */
    public void recordRejection(Enums.TaskRejectingStatus reason, @Nullable String functionCode,
                                @Nullable Long taskId, @Nullable Long processorId,
                                @Nullable String offendingValue, long nowMillis) {
        synchronized (lock) {
            countersByReason
                    .computeIfAbsent(reason, r -> new RejectionCounters(WINDOW_BUCKETS, MAX_KEYS_PER_BUCKET))
                    .increment(functionCode == null ? "unknown" : functionCode, nowMillis);

            // benign reasons are counted, but keeping exemplars for them would be pure noise
            if (classify(reason) != RejectionClass.benign) {
                exemplarsByReason
                        .computeIfAbsent(reason, r -> new ExemplarRing(EXEMPLARS_PER_REASON))
                        .add(nowMillis, taskId, functionCode, processorId, offendingValue);
            }
        }
    }

    /**
     * What an admin should look at: everything non-benign with a non-zero level, ranked
     * {@code actionable} first and then by how much of the window it has been present for.
     *
     * <p>Persistence outranks raw count deliberately. A reason firing twice a minute for fifteen
     * minutes is a broken thing; one firing five hundred times in a single minute during a deploy is
     * usually not.
     */
    public List<ReasonLevel> actionableView(long nowMillis) {
        final List<ReasonLevel> out = new ArrayList<>();
        synchronized (lock) {
            for (Map.Entry<Enums.TaskRejectingStatus, RejectionCounters> e : countersByReason.entrySet()) {
                final RejectionClass rejectionClass = classify(e.getKey());
                if (rejectionClass == RejectionClass.benign) {
                    continue;
                }
                final Map<String, Long> totals = e.getValue().windowTotals(nowMillis);
                final long count = totals.values().stream().mapToLong(Long::longValue).sum();
                if (count == 0) {
                    continue;
                }
                int present = 0;
                for (String key : totals.keySet()) {
                    present = Math.max(present, e.getValue().bucketsPresent(key, nowMillis));
                }
                final ExemplarRing ring = exemplarsByReason.get(e.getKey());
                out.add(new ReasonLevel(e.getKey(), rejectionClass, count, present,
                        ring == null ? List.of() : ring.newestFirst()));
            }
        }
        out.sort((a, b) -> {
            if (a.rejectionClass() != b.rejectionClass()) {
                return a.rejectionClass() == RejectionClass.actionable ? -1 : 1;
            }
            if (a.bucketsPresent() != b.bucketsPresent()) {
                return Integer.compare(b.bucketsPresent(), a.bucketsPresent());
            }
            return Long.compare(b.count(), a.count());
        });
        return out;
    }

    /** Total for a reason across the window, benign included — totals must stay honest. */
    public long countOf(Enums.TaskRejectingStatus reason, long nowMillis) {
        synchronized (lock) {
            final RejectionCounters counters = countersByReason.get(reason);
            if (counters == null) {
                return 0;
            }
            return counters.windowTotals(nowMillis).values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
