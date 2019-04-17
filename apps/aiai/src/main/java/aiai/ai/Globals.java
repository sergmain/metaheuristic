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
package aiai.ai;

import aiai.apps.commons.utils.SecUtils;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.PublicKey;
import java.util.Random;

@Component
@Slf4j
@ToString
public class Globals {

    // Globals' globals

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.thread-number'), 1, 8, 3) }")
    public int threadNumber;

    @Value("${aiai.is-testing:#{false}}")
    public boolean isUnitTesting = false;

    // Launchpad's globals

    @Value("${aiai.launchpad.secure-rest-url:#{true}}")
    public boolean isSecureLaunchpadRestUrl;

    @Value("${aiai.launchpad.is-ssl-required:#{true}}")
    public boolean isSslRequired = true;

    @Value("${aiai.launchpad.snippet-checksum-required:#{true}}")
    public boolean isSnippetChecksumRequired = true;

    @Value("${aiai.launchpad.snippet-signature-required:#{true}}")
    public boolean isSnippetSignatureRequired = true;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.master-username')) }")
    public String launchpadMasterUsername;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.master-token')) }")
    public String launchpadMasterToken;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.master-password')) }")
    public String launchpadMasterPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.public-key')) }")
    public String launchpadPublicKeyStr;

    @Value("${aiai.launchpad.enabled:#{false}}")
    public boolean isLaunchpadEnabled = true;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.launchpad.dir' )) }")
    public File launchpadDir;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.max-tasks-per-flow'), 1, 10000, 2000) }")
    public int maxTasksPerFlow;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.resource-table-rows-limit'), 5, 100, 20) }")
    public int resourceRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.experiment-table-rows-limit'), 5, 30, 10) }")
    public int experimentRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.atlas-experiment-table-rows-limit'), 5, 100, 20) }")
    public int atlasExperimentRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.flow-table-rows-limit'), 5, 50, 10) }")
    public int flowRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.flow-instance-table-rows-limit'), 5, 50, 20) }")
    public int flowInstanceRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.station-table-rows-limit'), 5, 30, 10) }")
    public int stationRowsLimit;

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.account-table-rows-limit'), 5, 100, 20) }")
    public int accountRowsLimit;

    @Value("${aiai.launchpad.is-replace-snapshot:#{true}}")
    public boolean isReplaceSnapshot;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.rest-password')) }")
    public String launchpadRestPassword;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.rest-username')) }")
    public String launchpadRestUsername;

    @Value("#{ T(aiai.ai.utils.EnvProperty).strIfBlankThenNull( environment.getProperty('aiai.launchpad.rest-token')) }")
    public String launchpadRestToken;

    // Station's globals

    @Value("${aiai.station.enabled:#{false}}")
    public boolean isStationEnabled = true;

    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.station.dir' )) }")
    public File stationDir;


    // some fields
    public File launchpadTempDir;
    public File launchpadResourcesDir;

    public File stationResourcesDir;
    public File stationTaskDir;
    public File stationEnvHotDeployDir;

    public PublicKey launchpadPublicKey = null;

    @PostConstruct
    public void init() {
        if (launchpadPublicKeyStr!=null) {
            launchpadPublicKey = SecUtils.getPublicKey(launchpadPublicKeyStr);
        }

        if (launchpadMasterUsername!=null && launchpadMasterUsername.equals(launchpadRestUsername)) {
            throw new IllegalStateException("launchpadMasterUsername can't be the same as launchpadRestUsername, launchpadMasterUsername: " + launchpadMasterUsername + ", launchpadRestUsername: " + launchpadRestUsername);
        }

        if (stationDir==null) {
            log.warn("Station is disabled, stationDir: {}, isStationEnabled: {}", stationDir, isStationEnabled);
            isStationEnabled = false;
        }
        else {
            stationResourcesDir = new File(stationDir, Consts.RESOURCES_DIR);
            stationResourcesDir.mkdirs();
            stationTaskDir = new File(stationDir, Consts.TASK_DIR);
            stationTaskDir.mkdirs();
            stationEnvHotDeployDir = new File(stationDir, Consts.ENV_HOT_DEPLOY_DIR);
            stationEnvHotDeployDir.mkdirs();
        }

        if (isLaunchpadEnabled && launchpadDir==null) {
            log.warn("Launchpad is disabled, launchpadDir: {}, isLaunchpadEnabled: {}", launchpadDir, isLaunchpadEnabled);
            isLaunchpadEnabled = false;
        }

        if (isLaunchpadEnabled) {
            if (launchpadMasterUsername==null || launchpadMasterToken==null || launchpadMasterPassword==null) {
                throw new IllegalArgumentException("if aiai.secure-rest-url=true, then aiai.master-username, aiai.master-token, and aiai.master-password have to be not null");
            }

            launchpadTempDir = new File(launchpadDir, "temp");
            launchpadTempDir.mkdirs();

            launchpadResourcesDir = new File(launchpadDir, Consts.RESOURCES_DIR);
            launchpadResourcesDir.mkdirs();

        }
        logGlobals();
    }

    private void logGlobals() {
        log.info("Current globals:");
        log.info("\tisSecureLaunchpadRestUrl: {}", isSecureLaunchpadRestUrl);
        log.info("\tthreadNumber: {}", threadNumber);
        log.info("\tisUnitTesting: {}", isUnitTesting);
        log.info("\tisSslRequired: {}", isSslRequired);
        log.info("\tisLaunchpadEnabled: {}", isLaunchpadEnabled);
        log.info("\tlaunchpadDir: {}", launchpadDir);
        log.info("\tresourceRowsLimit: {}", resourceRowsLimit);
        log.info("\texperimentRowsLimit: {}", experimentRowsLimit);
        log.info("\tflowRowsLimit: {}", flowRowsLimit);
        log.info("\tflowInstanceRowsLimit: {}", flowInstanceRowsLimit);
        log.info("\tstationRowsLimit: {}", stationRowsLimit);
        log.info("\taccountRowsLimit: {}", accountRowsLimit);
        log.info("\tisReplaceSnapshot: {}", isReplaceSnapshot);
        log.info("\tisStationEnabled: {}", isStationEnabled);
        log.info("\tstationDir: {}", stationDir);
    }

    private static final Random r = new Random();
    public File createTempFileForLaunchpad(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            throw new IllegalStateException("Prefix is blank");
        }
        //noinspection UnnecessaryLocalVariable
        File tempFile = new File(launchpadTempDir,
                prefix + r.nextInt(99999999) + '-' + System.nanoTime()
        );
        return tempFile;
    }
}
