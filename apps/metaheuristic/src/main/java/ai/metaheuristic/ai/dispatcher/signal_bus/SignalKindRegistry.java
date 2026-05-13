/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-kind configuration: {@link TopicBuilder} and {@link CoalescePolicy}.
 * In production, wired as a Spring bean listing every registered kind.
 * In unit tests, constructed directly with the kinds under test.
 */
public class SignalKindRegistry {

    private final Map<SignalKind, TopicBuilder> topicBuilders;
    private final Map<SignalKind, CoalescePolicy> coalescePolicies;

    public SignalKindRegistry(Map<SignalKind, TopicBuilder> topicBuilders,
                              Map<SignalKind, CoalescePolicy> coalescePolicies) {
        this.topicBuilders = new HashMap<>(topicBuilders);
        this.coalescePolicies = new HashMap<>(coalescePolicies);
    }

    public TopicBuilder topicBuilderFor(SignalKind kind) {
        TopicBuilder b = topicBuilders.get(kind);
        if (b == null) {
            throw new IllegalStateException("668.010 No TopicBuilder registered for kind " + kind);
        }
        return b;
    }

    public CoalescePolicy coalescePolicyFor(SignalKind kind) {
        return coalescePolicies.getOrDefault(kind, CoalescePolicy.NONE);
    }

    /**
     * All kinds with a registered TopicBuilder. Used by {@link SignalBus#query}
     * to expand "no kinds filter" → "every known kind", and by the REST
     * controller to validate the {@code kinds} query parameter.
     */
    public Set<SignalKind> knownKinds() {
        return Set.copyOf(topicBuilders.keySet());
    }

    /**
     * Production wiring for v1 — every kind in {@link SignalKind} with its
     * topic builder per signal-bus-06-topics.md §3 and its coalesce policy
     * per signal-bus-01-architecture.md §7.1. All kinds are NONE in v1
     * except DOCUMENT_EXPORT (100ms).
     */
    public static SignalKindRegistry v1Default() {
        TopicBuilder batchTopic = (k, id, info) -> "batch." + id + ".state";
        TopicBuilder execContextTopic = (k, id, info) -> "execContext." + id + ".state";
        TopicBuilder documentExportTopic = (k, id, info) ->
            "document.export." + info.get("projectId") + ".progress";
        TopicBuilder systemNoticeTopic = (k, id, info) -> "system.notice";

        Map<SignalKind, TopicBuilder> topics = new HashMap<>();
        topics.put(SignalKind.BATCH, batchTopic);
        topics.put(SignalKind.EXEC_CONTEXT, execContextTopic);
        topics.put(SignalKind.DOCUMENT_EXPORT, documentExportTopic);
        topics.put(SignalKind.SYSTEM_NOTICE, systemNoticeTopic);

        Map<SignalKind, CoalescePolicy> coalesce = new HashMap<>();
        coalesce.put(SignalKind.BATCH, CoalescePolicy.NONE);
        coalesce.put(SignalKind.EXEC_CONTEXT, CoalescePolicy.NONE);
        coalesce.put(SignalKind.DOCUMENT_EXPORT,
            new CoalescePolicy(java.time.Duration.ofMillis(100)));
        coalesce.put(SignalKind.SYSTEM_NOTICE, CoalescePolicy.NONE);

        return new SignalKindRegistry(topics, coalesce);
    }

}
