/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.station.station_resource;

import aiai.ai.Enums;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.resource.ResourceUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("station")
public class ResourceProviderFactory {

    public final DiskResourceProvider diskResourceProvider;
    public final LaunchpadResourceProvider launchpadResourceProvider;

    public ResourceProviderFactory(DiskResourceProvider diskResourceProvider, LaunchpadResourceProvider launchpadResourceProvider) {
        this.diskResourceProvider = diskResourceProvider;
        this.launchpadResourceProvider = launchpadResourceProvider;
    }

    @SuppressWarnings("Duplicates")
    public ResourceProvider getResourceProvider(String storageUrl) {
        Enums.StorageType storageType = ResourceUtils.getStorageType(storageUrl);
        switch(storageType) {
            case launchpad:
                return launchpadResourceProvider;
            case disk:
                return diskResourceProvider;
            case hadoop:
            case ftp:
            default:
                throw new ResourceProviderException("Unknown storageType: " + storageType);
        }
    }

}
