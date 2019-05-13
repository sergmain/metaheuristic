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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadSchedule;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfigUtils;
import lombok.Data;
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
public class LaunchpadLookupExtendedService {

    private final Globals globals;

    public Map<String, LaunchpadLookupExtended> lookupExtendedMap = null;

    public LaunchpadLookupExtendedService(Globals globals) {
        this.globals = globals;
    }

    @Data
    public static class LaunchpadLookupExtended {
        public LaunchpadLookupConfig.LaunchpadLookup launchpadLookup;
        public LaunchpadSchedule schedule;
    }

    @PostConstruct
    public void init() {
        final File launchpadFile = new File(globals.stationDir, Consts.LAUNCHPAD_YAML_FILE_NAME);
        if (!launchpadFile.exists()) {
            log.warn("Station's launchpad config file doesn't exist: {}", launchpadFile.getPath());
            return;
        }
        try {
            final String cfg = FileUtils.readFileToString(launchpadFile, Charsets.UTF_8);
            LaunchpadLookupConfig launchpadLookupConfig = LaunchpadLookupConfigUtils.to(cfg);
            ;
            if (launchpadLookupConfig == null) {
                log.error("{} wasn't found or empty. path: {}{}{}",
                        Consts.LAUNCHPAD_YAML_FILE_NAME, globals.stationDir,
                        File.separatorChar, Consts.LAUNCHPAD_YAML_FILE_NAME);
                throw new IllegalStateException("Station isn't configured, launchpad.yaml is empty or doesn't exist");
            }
            final Map<String, LaunchpadLookupExtended> map = new HashMap<>();
            for (LaunchpadLookupConfig.LaunchpadLookup launchpad : launchpadLookupConfig.launchpads) {
                LaunchpadLookupExtended lookupExtended = new LaunchpadLookupExtended();
                lookupExtended.launchpadLookup = launchpad;
                lookupExtended.schedule = new LaunchpadSchedule(launchpad.taskProcessingTime);
                map.put(launchpad.url, lookupExtended);
            }
            lookupExtendedMap = Collections.unmodifiableMap(map);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + launchpadFile.getPath(), e);
        }

    }

}
