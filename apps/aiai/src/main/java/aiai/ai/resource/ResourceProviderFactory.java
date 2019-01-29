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

package aiai.ai.resource;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.exceptions.ResourceProviderException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ResourceProviderFactory {

    public final DiskResourceProvider diskResourceProvider;
    public final LaunchpadResourceProvider launchpadResourceProvider;

    public ResourceProviderFactory(DiskResourceProvider diskResourceProvider, LaunchpadResourceProvider launchpadResourceProvider) {
        this.diskResourceProvider = diskResourceProvider;
        this.launchpadResourceProvider = launchpadResourceProvider;
    }

    public ResourceProvider getResourceProvider(String storageUrl) {
        int idx = StringUtils.indexOf(storageUrl, Consts.PROTOCOL_DELIMITER);
        if (idx==-1) {
            throw new ResourceProviderException("Bad format of storageUrl: " + storageUrl);
        }
        Enums.StorageType storageType = Enums.StorageType.valueOf( storageUrl.substring(0, idx) );
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
