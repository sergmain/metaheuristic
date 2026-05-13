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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signal Bus bean wiring. Active on the dispatcher profile only.
 * See docs/mh/signal-bus-01-architecture.md.
 *
 * The registry is assembled by starting from {@link SignalKindRegistry#v1Default()}
 * (MH's core kinds) and merging any {@link SignalKindContributor} beans on top.
 * Spring auto-collects every bean implementing the contributor interface into
 * the {@code contributors} list — empty list if none. This is the same
 * auto-collection pattern as ShutdownService / ShutdownInterface.
 */
@Configuration
@Profile("dispatcher")
public class SignalBusConfig {

    @Bean
    public SignalKindRegistry signalKindRegistry(List<SignalKindContributor> contributors) {
        SignalKindRegistry base = SignalKindRegistry.v1Default();

        Map<SignalKind, TopicBuilder> topics = new HashMap<>();
        Map<SignalKind, CoalescePolicy> coalesce = new HashMap<>();

        // Seed with the core kinds from v1Default() so the merged registry
        // includes both MH's own kinds and contributor-provided kinds.
        for (SignalKind kind : base.knownKinds()) {
            topics.put(kind, base.topicBuilderFor(kind));
            coalesce.put(kind, base.coalescePolicyFor(kind));
        }

        for (SignalKindContributor contributor : contributors) {
            topics.putAll(contributor.topicBuilders());
            coalesce.putAll(contributor.coalescePolicies());
        }

        return new SignalKindRegistry(topics, coalesce);
    }

    @Bean
    public SignalBus signalBus(SignalKindRegistry signalKindRegistry) {
        return new SignalBus(signalKindRegistry);
    }

    @Bean
    public SignalBusSweeper signalBusSweeper(SignalBus signalBus,
                                             ai.metaheuristic.ai.Globals globals) {
        return new SignalBusSweeper(signalBus, globals.dispatcher.timeout.getSignalBusTtl(),
            java.time.Clock.systemUTC());
    }
}
