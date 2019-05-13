/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.core;

import lombok.Getter;
import lombok.Value;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.util.Collection;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 15:59
 */
public class TestConfig {


    @DomainEvents
    Collection<Object> domainEvents() {
        return null;
    }

    @AfterDomainEventPublication
    void callbackMethod() {
        // … potentially clean up domain events list
    }

    @Value(staticConstructor = "of")
    static class OneEvent {
        @Getter(onMethod = @__(@DomainEvents)) Object event;
    }
}
