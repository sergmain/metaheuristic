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
import aiai.apps.commons.utils.SecUtils;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

@Component
@Slf4j
@ToString
public class Globals {

    // Globals' globals

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.public-key')) }")
    public String publicKeyStr;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.rest-password')) }")
    public String restPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.rest-username')) }")
    public String restUsername;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.rest-token')) }")
    public String restToken;

    @Value("${aiai.secure-rest-url:#{true}}")
    public boolean isSecureRestUrl;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.master-username')) }")
    public String masterUsername;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.master-token')) }")
    public String masterToken;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.master-password')) }")
    public String masterPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.thread-number'), 1, 8, 3) }")
    public int threadNumber;

    @Value("${aiai.is-testing:#{false}}")
    public boolean isUnitTesting = false;

    // Launchpad's globals

    @Value("${aiai.launchpad.enabled:#{false}}")
    public boolean isLaunchpadEnabled = true;

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

    @Value("${aiai.launchpad.accept-only-signed-env:#{true}}")
    public boolean isAcceptOnlySignedEnv;

    // Station's globals

    @Value("${aiai.station.enabled:#{false}}")
    public boolean isStationEnabled = true;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    public File stationDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.station.server-rest-password')) }")
    public String stationRestPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.station.active-time')) }")
    public String stationActiveTime;

    @Value("${aiai.station.accept-only-signed-snippets:#{true}}")
    public boolean isAcceptOnlySignedSnippets;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.station.launchpad-url')) }")
    public String launchpadUrl;

    // some fields

    public File stationDatasetDir;
    public File stationExperimentDir;
    public File stationSnippetDir;
    public File stationDbDir;
    public File stationSystemDir;

    public TimePeriods timePeriods;

    public String serverRestUrl;
    public PublicKey publicKey = null;


    @PostConstruct
    public void init() throws GeneralSecurityException {
        if (publicKeyStr!=null) {
            publicKey = SecUtils.getPublicKey(publicKeyStr);
        }

        if (masterUsername!=null && masterUsername.equals(restUsername)) {
            throw new IllegalStateException("masterUsername can't be the same as restUsername, masterUsername: " + masterUsername + ", restUsername: " + restUsername);
        }

        if (isSecureRestUrl) {
            if (masterUsername==null || masterToken==null || masterPassword==null) {
                throw new IllegalArgumentException("if aiai.secure-rest-url=true, then aiai.master-username, aiai.master-token, and aiai.master-password have to be not null");
            }
            if (isStationEnabled) {
                if (stationRestPassword==null) {
                    throw new IllegalArgumentException("if aiai.secure-rest-url=true and aiai.station.enabled=true, then aiai.station.server-rest-password has to be not null");
                }
            }
        }

        serverRestUrl = launchpadUrl + (isSecureRestUrl ? Consts.SERVER_REST_AUTH_URL : Consts.SERVER_REST_ANON_URL );

        if (stationDir==null) {
            log.warn("Station is disabled, stationDir: {}, isStationEnabled: {}", stationDir, isStationEnabled);
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
            log.warn("Launchpad is disabled, launchpadDir: {}, isLaunchpadEnabled: {}", launchpadDir, isLaunchpadEnabled);
            isLaunchpadEnabled = false;
        }

    }
}
