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

/**
 * Signal Bus bean wiring. Active on the dispatcher profile only.
 * See docs/mh/signal-bus-01-architecture.md.
 */
@Configuration
@Profile("dispatcher")
public class SignalBusConfig {

    @Bean
    public SignalKindRegistry signalKindRegistry() {
        return SignalKindRegistry.v1Default();
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
