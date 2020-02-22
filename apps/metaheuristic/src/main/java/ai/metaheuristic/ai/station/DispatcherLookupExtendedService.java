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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.LaunchpadLookupConfigUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Profile("station")
@RequiredArgsConstructor
public class DispatcherLookupExtendedService {

    private final Globals globals;

    // Collections.unmodifiableMap
    public Map<String, DispatcherLookupExtended> lookupExtendedMap = null;

    @Data
    public static class DispatcherLookupExtended {
        public DispatcherLookupConfig.DispatcherLookup dispatcherLookup;
        public LaunchpadSchedule schedule;
        public final DispatcherContext context = new DispatcherContext();
    }

    @PostConstruct
    public void init() {
        final File dispatcherFile = new File(globals.stationDir, Consts.DISPATCHER_YAML_FILE_NAME);
        final String cfg;
        if (!dispatcherFile.exists()) {
            if (globals.defaultDispatcherYamlFile == null) {
                log.warn("Station's dispatcher config file {} doesn't exist and default file wasn't specified", dispatcherFile.getPath());
                return;
            }
            if (!globals.defaultDispatcherYamlFile.exists()) {
                log.warn("Station's default dispatcher.yaml file doesn't exist: {}", globals.defaultDispatcherYamlFile.getAbsolutePath());
                return;
            }
            try {
                FileUtils.copyFile(globals.defaultDispatcherYamlFile, dispatcherFile);
            } catch (IOException e) {
                log.error("Error", e);
                throw new IllegalStateException("Error while copying "+ globals.defaultDispatcherYamlFile.getAbsolutePath()+" to " + dispatcherFile.getAbsolutePath(), e);
            }
        }

        try {
            cfg = FileUtils.readFileToString(dispatcherFile, Charsets.UTF_8);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while reading file: " + dispatcherFile.getAbsolutePath(), e);
        }

        DispatcherLookupConfig dispatcherLookupConfig = LaunchpadLookupConfigUtils.to(cfg);

        if (dispatcherLookupConfig == null) {
            log.error("{} wasn't found or empty. path: {}{}{}",
                    Consts.DISPATCHER_YAML_FILE_NAME, globals.stationDir,
                    File.separatorChar, Consts.DISPATCHER_YAML_FILE_NAME);
            throw new IllegalStateException("Station isn't configured, dispatcher.yaml is empty or doesn't exist");
        }
        final Map<String, DispatcherLookupExtended> map = new HashMap<>();
        for (DispatcherLookupConfig.DispatcherLookup dispatcher : dispatcherLookupConfig.dispatchers) {
            DispatcherLookupExtended lookupExtended = new DispatcherLookupExtended();
            lookupExtended.dispatcherLookup = dispatcher;
            lookupExtended.schedule = new LaunchpadSchedule(dispatcher.taskProcessingTime);
            map.put(dispatcher.url, lookupExtended);
        }
        lookupExtendedMap = Collections.unmodifiableMap(map);
    }

    public File prepareBaseResourceDir(Metadata.DispatcherInfo dispatcherCode) {
        final File dispatcherDir = new File(globals.stationResourcesDir, dispatcherCode.code);
        if (dispatcherDir.exists()) {
            return dispatcherDir;
        }
        //noinspection unused
        boolean status = dispatcherDir.mkdirs();
        return dispatcherDir;
    }


}
