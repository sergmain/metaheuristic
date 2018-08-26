/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Component
@Slf4j
public class Globals {

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    public File stationDir;

    @Value("${aiai.station.launchpad.url:#{null}}")
    public String launchpadUrl;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.launchpad.dir' )) }")
    public File launchpadDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.thread-number'), 1, 8, 3) }")
    public int threadNumber;

    @Value("${aiai.launchpad.is-replace-snapshot:#{true}}")
    public boolean isReplaceSnapshot;

    public boolean isStationEnabled = true;
    public boolean isLaunchpadEnabled = true;
    public boolean isUnitTesting = false;

    @PostConstruct
    public void init() {
        if (stationDir==null || launchpadUrl==null) {
            log.info("station is disabled, stationDir: {}, launchpadUrl: {}", stationDir, launchpadUrl);
            isStationEnabled = false;
        }

        if (launchpadDir==null) {
            log.info("Launchpad is disabled, launchpadDir: {}", launchpadDir);
            isLaunchpadEnabled = false;
        }

    }
}
