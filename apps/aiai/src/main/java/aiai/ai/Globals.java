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

import aiai.ai.yaml.env.TimePeriods;
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

    // Globals' globals

    @Value("${aiai.public-key:#{null}}")
    public String publicKey;

    @Value("${aiai.station.launchpad.url:#{null}}")
    public String launchpadUrl;

    @Value("${aiai.rest-password:#{null}}")
    public String restPassword;

    @Value("${aiai.rest-username:#{null}}")
    public String restUsername;

    @Value("${aiai.rest-token:#{null}}")
    public String restToken;

    @Value("${aiai.secure-rest-url:#{true}}")
    public boolean isSecureRestUrl;

    // Launchpad's globals

    @Value("${aiai.master-username}")
    public String masterUsername;

    @Value("${aiai.master-token}")
    public String masterToken;

    @Value("${aiai.master-password}")
    public String masterPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.thread-number'), 1, 8, 3) }")
    public int threadNumber;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    public File stationDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.launchpad.dir' )) }")
    public File launchpadDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.dataset-table-rows-limit'), 5, 100, 20) }")
    public int datasetRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.experiment-table-rows-limit'), 5, 30, 10) }")
    public int experimentRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.station-table-rows-limit'), 5, 30, 10) }")
    public int stationRowsLimit;

    @Value("${aiai.launchpad.is-replace-snapshot:#{true}}")
    public boolean isReplaceSnapshot;

    // Station's globals

    @Value("${aiai.station.active-time:#{null}}")
    public String stationActiveTime;

    @Value("${aiai.accept.run-only-signed-snippets:#{true}}")
    public boolean isAcceptOnlySignedSnippets;


    public boolean isStationEnabled = true;
    public boolean isLaunchpadEnabled = true;

    @Value("${aiai.is-testing:#{false}}")
    public boolean isUnitTesting = false;

    public File stationDatasetDir;
    public File stationExperimentDir;
    public File stationSnippetDir;
    public File stationDbDir;
    public File stationSystemDir;

    public TimePeriods timePeriods;

    public String serverRestUrl;

    @PostConstruct
    public void init() {
        if (masterUsername!=null && masterUsername.equals(restUsername)) {
            throw new IllegalStateException("masterUsername can't be the same as restUsername, masterUsername: " + masterUsername + ", restUsername: " + restUsername);
        }

        serverRestUrl = launchpadUrl + (isSecureRestUrl ? Consts.SERVER_REST_AUTH_URL : Consts.SERVER_REST_ANON_URL );

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

            timePeriods = TimePeriods.from(stationActiveTime);
        }

        if (launchpadDir==null) {
            log.warn("Launchpad is disabled, launchpadDir: {}", launchpadDir);
            isLaunchpadEnabled = false;
        }

    }
}
