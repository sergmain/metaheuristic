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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Random;

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

    @Value("${aiai.launchpad.store-data:#{'disk'}}")
    public String storeDataStr;

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
    public File launchpadTempDir;

    public File stationDatasetDir;
    public File stationExperimentDir;
    public File stationSnippetDir;
    public File stationDbDir;
    public File stationSystemDir;

    public TimePeriods timePeriods;

    public String serverRestUrl;
    public PublicKey publicKey = null;

    public Enums.StoreData[] storeData = new Enums.StoreData[0];

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
            stationDatasetDir.mkdirs();
            stationExperimentDir = new File(stationDir, Consts.EXPERIMENT_DIR);
            stationExperimentDir.mkdirs();
            stationSnippetDir = new File(stationDir, Consts.SNIPPET_DIR);
            stationSnippetDir.mkdirs();
            stationDbDir = new File(stationDir, Consts.DATABASE_DIR);
            stationDbDir.mkdirs();
            stationSystemDir = new File(stationDir, Consts.SYSTEM_DIR);
            stationSystemDir.mkdirs();

            timePeriods = TimePeriods.from(stationActiveTime);
        }

        if (isLaunchpadEnabled && launchpadDir==null) {
            log.warn("Launchpad is disabled, launchpadDir: {}, isLaunchpadEnabled: {}", launchpadDir, isLaunchpadEnabled);
            isLaunchpadEnabled = false;
        }

        if (isLaunchpadEnabled) {
            String[] split = StringUtils.split(storeDataStr, ',');
            storeData = new Enums.StoreData[split.length];
            for (int i = 0; i < split.length; i++) {
                storeData[i] = Enums.StoreData.valueOf(split[i].toUpperCase());
            }
            if (storeData.length==0) {
                throw new IllegalStateException("You have to define at least one storage type");
            }
            launchpadTempDir = new File(launchpadDir, "temp");
            launchpadTempDir.mkdirs();
        }
        //noinspection unused
        int i=0;
    }

    public boolean isStoreDataToDisk() {
        return isLaunchpadEnabled && isStoreDataTo(Enums.StoreData.DISK);
    }

    public boolean isStoreDataToDb() {
        return isLaunchpadEnabled && isStoreDataTo(Enums.StoreData.DB);
    }

    private boolean isStoreDataTo(Enums.StoreData type) {
        for (Enums.StoreData storeData : this.storeData) {
            if (storeData ==type) {
                return true;
            }
        }
        return false;
    }

    private static final Random r = new Random();
    public File createTempFileForLaunchpad(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            throw new IllegalStateException("Prefix is blank");
        }
        File tempFile = new File(launchpadTempDir,
                prefix + r.nextInt(99999999) + '-' + System.nanoTime()
        );
        return tempFile;
    }
}
