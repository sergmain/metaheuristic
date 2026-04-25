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

package ai.metaheuristic.ai.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/24/2026
 * Time: 8:39 PM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ShutdownService {

    public final List<ShutdownInterface> shutdowns;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @PreDestroy
    public void preDestroy() {
        shutdowns.parallelStream().forEach(ShutdownInterface::shutdown);
    }
}
