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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergio Lissner
 * Plan 01 — Foundation, Step 10. Smoke test per kind asserting the
 * generated topic string matches signal-bus-06-topics.md §3.
 */
class SignalKindRegistryTest {

    private final SignalKindRegistry registry = SignalKindRegistry.v1Default();

    @Test
    void topicBuilder_batch_matchesCatalogue() {
        String topic = registry.topicBuilderFor(SignalKind.BATCH)
            .build(SignalKind.BATCH, "42", Map.of());
        assertThat(topic).isEqualTo("batch.42.state");
    }

    @Test
    void topicBuilder_execContext_matchesCatalogue() {
        String topic = registry.topicBuilderFor(SignalKind.EXEC_CONTEXT)
            .build(SignalKind.EXEC_CONTEXT, "100",
                Map.of("infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"));
        assertThat(topic).isEqualTo("execContext.DRONE.mhdg-rg-flat.state");
    }

    @Test
    void topicBuilder_execContext_cvWorkflow_matchesCatalogue() {
        String topic = registry.topicBuilderFor(SignalKind.EXEC_CONTEXT)
            .build(SignalKind.EXEC_CONTEXT, "101",
                Map.of("infoBank", "DRONE", "sourceCodeUid", "cv-redundancy-1.0.0"));
        assertThat(topic).isEqualTo("execContext.DRONE.cv-redundancy.state");
    }

    @Test
    void topicBuilder_documentExport_matchesCatalogue() {
        String topic = registry.topicBuilderFor(SignalKind.DOCUMENT_EXPORT)
            .build(SignalKind.DOCUMENT_EXPORT, "export:42:xyz",
                Map.of("projectId", 42L));
        assertThat(topic).isEqualTo("document.export.42.progress");
    }

    @Test
    void topicBuilder_systemNotice_matchesCatalogue() {
        String topic = registry.topicBuilderFor(SignalKind.SYSTEM_NOTICE)
            .build(SignalKind.SYSTEM_NOTICE, "uuid-here", Map.of());
        assertThat(topic).isEqualTo("system.notice");
    }

    @Test
    void coalescePolicy_documentExport_is100ms() {
        CoalescePolicy policy = registry.coalescePolicyFor(SignalKind.DOCUMENT_EXPORT);
        assertThat(policy.minInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void coalescePolicy_otherKinds_areNone() {
        assertThat(registry.coalescePolicyFor(SignalKind.BATCH)).isEqualTo(CoalescePolicy.NONE);
        assertThat(registry.coalescePolicyFor(SignalKind.EXEC_CONTEXT)).isEqualTo(CoalescePolicy.NONE);
        assertThat(registry.coalescePolicyFor(SignalKind.SYSTEM_NOTICE)).isEqualTo(CoalescePolicy.NONE);
    }
}
