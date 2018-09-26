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

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Component
@Slf4j
@ToString
public class Globals {

    @Value("${aiai.station.launchpad.url:#{null}}")
    public String launchpadUrl;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    public File stationDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.launchpad.dir' )) }")
    public File launchpadDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.thread-number'), 1, 8, 3) }")
    public int threadNumber;

    @Value("${aiai.launchpad.is-replace-snapshot:#{true}}")
    public boolean isReplaceSnapshot;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.dataset-table-rows-limit'), 5, 100, 20) }")
    public int datasetRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.experiment-table-rows-limit'), 5, 30, 10) }")
    public int experimentRowsLimit;


    public boolean isStationEnabled = true;
    public boolean isLaunchpadEnabled = true;

    @Value("${aiai.is-testing:#{false}}")
    public boolean isUnitTesting = false;

    public File stationDatasetDir;
    public File stationExperimentDir;
    public File stationSnippetDir;
    public File stationDbDir;
    public File stationSystemDir;

    public String serverRestUrl;

    @PostConstruct
    public void init() {
        serverRestUrl = launchpadUrl + Consts.SERVER_REST_URL;

        if (stationDir==null) {
            log.warn("Station is disabled, stationDir: {}", stationDir);
            isStationEnabled = false;
        }
        else {
            stationDatasetDir = new File(stationDir, Consts.DATASET_DIR);
            stationExperimentDir = new File(stationDir, Consts.EXPERIMENT_DIR);
            stationSnippetDir = new File(stationDir, Consts.SNIPPET_DIR);
            stationDbDir = new File(stationDir, Consts.DATABASE_DIR);
            stationSystemDir = new File(stationDir, Consts.SYSTEM_DIR);
        }

        if (launchpadDir==null) {
            log.warn("Launchpad is disabled, launchpadDir: {}", launchpadDir);
            isLaunchpadEnabled = false;
        }

    }
}
