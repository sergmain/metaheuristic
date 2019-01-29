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
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.station.StationService;
import aiai.ai.station.actors.DownloadResourceActor;
import aiai.ai.station.tasks.DownloadResourceTask;
import aiai.ai.yaml.env.DiskStorage;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class DiskResourceProvider implements ResourceProvider {

    private final StationService stationService;

    private final DownloadResourceActor downloadResourceActor;

    public DiskResourceProvider(StationService stationService, DownloadResourceActor downloadResourceActor) {
        this.stationService = stationService;
        this.downloadResourceActor = downloadResourceActor;
    }

    @Override
    public List<AssetFile> prepareDataFile(File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad, StationTask task, Metadata.LaunchpadInfo launchpadCode, String resourceCode, String storageUrl) {
        DiskStorageUri storageUri = parseStorageUrl(storageUrl);
        if (!storageUri.resourceCode.equals("*")) {
            throw new ResourceProviderException("#015.018 The disk storage supports the only one type - '*', actual" + storageUri.resourceCode);
        }
        EnvYaml env = stationService.getEnvYaml();
        DiskStorage diskStorage = env.findDiskStorageByCode(storageUri.envCode);
        if (diskStorage==null) {
            throw new ResourceProviderException("#015.020 The disk storage wasn't found for code: " + storageUri.envCode);
        }
        File path = new File(diskStorage.path);
        if (!path.exists()) {
            throw new ResourceProviderException("#015.024 The path of disk storage doesn't exist: " + path.getAbsolutePath());
        }

/*
        try {
            Files.list(path.toPath()).forEach(t -> {
                try {
                    File taskYaml = new File(t.toFile(), Consts.TASK_YAML);
                    if (!taskYaml.exists()) {
                        FileUtils.deleteDirectory(t.toFile());
                        // IDK is that bug or side-effect. so delete one more time
                        FileUtils.deleteDirectory(t.toFile());
                    }
                } catch (IOException e) {
                    log.error("#090.01 Error while deleting path {}, this isn't fatal error.", t);
                }
            });
        } catch (IOException e) {
            throw new ResourceProviderException("#015.030 Error while getting list of files in " + path.getAbsolutePath()+", error: " + e.toString());
        }
*/

        // return empty because snippet will use allfiles
        // which it'll find in path is defined by storageUrl
        return new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class DiskStorageUri {
        public String envCode;
        public String resourceCode;
    }

    public static DiskStorageUri parseStorageUrl(String storageUrl) {
        if (!storageUrl.startsWith(Consts.DISK_STORAGE_URL)) {
            throw new ResourceProviderException("#015.01 Wrong storageUrl format: " + storageUrl);
        }
        String uri = storageUrl.substring(Consts.DISK_STORAGE_URL.length());
        if (uri.indexOf('/')!=uri.lastIndexOf('/')) {
            throw new ResourceProviderException("#015.05 Wrong storageUrl format: " + storageUrl);
        }
        return new DiskStorageUri(uri.substring(0, uri.indexOf('/')), uri.substring(uri.indexOf('/')+1));
    }
}
