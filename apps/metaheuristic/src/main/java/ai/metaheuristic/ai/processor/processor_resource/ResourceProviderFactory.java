/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.processor.processor_resource;

import ai.metaheuristic.ai.exceptions.ResourceProviderException;
import ai.metaheuristic.api.EnumsApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("processor")
public class ResourceProviderFactory {

    private final DiskResourceProvider diskResourceProvider;
    private final DispatcherResourceProvider dispatcherResourceProvider;

    public ResourceProviderFactory(DiskResourceProvider diskResourceProvider, DispatcherResourceProvider dispatcherResourceProvider) {
        this.diskResourceProvider = diskResourceProvider;
        this.dispatcherResourceProvider = dispatcherResourceProvider;
    }

    public ResourceProvider getResourceProvider(EnumsApi.DataSourcing sourcing) {
        switch(sourcing) {
            case dispatcher:
                return dispatcherResourceProvider;
            case disk:
                return diskResourceProvider;
            default:
                throw new ResourceProviderException("Unknown DataSourcing: " + sourcing);
        }
    }

}
