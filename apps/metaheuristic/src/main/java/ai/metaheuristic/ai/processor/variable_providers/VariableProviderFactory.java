/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.variable_providers;

import ai.metaheuristic.ai.exceptions.VariableProviderException;
import ai.metaheuristic.api.EnumsApi;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableProviderFactory {

    private final DiskVariableProvider diskVariableProvider;
    private final DispatcherVariableProvider dispatcherVariableProvider;

    private final Map<EnumsApi.DataSourcing, VariableProvider> variableProviders = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        variableProviders.put(diskVariableProvider.getSourcing(), diskVariableProvider);
        variableProviders.put(dispatcherVariableProvider.getSourcing(), dispatcherVariableProvider);
    }

    public VariableProvider getVariableProvider(EnumsApi.DataSourcing sourcing) {
        VariableProvider provider = variableProviders.get(sourcing);
        if (provider!=null) {
            return provider;
        }
        throw new VariableProviderException("Unknown DataSourcing: " + sourcing);
    }

}
