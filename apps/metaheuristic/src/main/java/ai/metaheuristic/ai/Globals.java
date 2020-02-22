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
package ai.metaheuristic.ai;

import ai.metaheuristic.ai.exceptions.GlobalConfigurationException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.SecUtils;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@Slf4j
@ToString
@RequiredArgsConstructor
public class Globals {

    private final Environment env;

    // Globals' globals

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.thread-number'), 1, 16, 4) }")
    public int threadNumber;

    @Value("${mh.is-testing:#{false}}")
    public boolean isUnitTesting = false;

    @Value("${mh.system-owner:#{null}}")
    public String systemOwner;

    @Value("${mh.branding:#{'Metaheuristic project'}}")
    public String branding;

    @Value("${mh.cors-allowed-origins:#{null}}")
    public String allowedOriginsStr;

    @Value("${mh.is-event-enabled:#{false}}")
    public boolean isEventEnabled = false;

    // Launchpad's globals

    @Value("${mh.launchpad.is-security-enabled:#{true}}")
    public boolean isSecurityEnabled;

    @Value("${mh.launchpad.is-ssl-required:#{true}}")
    public boolean isSslRequired = true;

    @Value("${mh.launchpad.function-checksum-required:#{true}}")
    public boolean isFunctionChecksumRequired = true;

    @Value("${mh.launchpad.function-signature-required:#{true}}")
    public boolean isFunctionSignatureRequired = true;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.master-username')) }")
    public String launchpadMasterUsername;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.master-password')) }")
    public String launchpadMasterPassword;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.public-key')) }")
    public String launchpadPublicKeyStr;

    @Value("${mh.launchpad.enabled:#{false}}")
    public boolean isLaunchpadEnabled = true;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.launchpad.dir' )) }")
    public File launchpadDir;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.max-tasks-per-exec-context'), 1, 100000, 5000) }")
    public int maxTasksPerExecContext;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.global-variable-table-rows-limit'), 5, 100, 20) }")
    public int globalVariableRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.experiment-table-rows-limit'), 5, 30, 10) }")
    public int experimentRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.atlas-experiment-table-rows-limit'), 5, 100, 20) }")
    public int atlasExperimentRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.source-code-table-rows-limit'), 5, 50, 10) }")
    public int sourceCodeRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.exec-context-table-rows-limit'), 5, 50, 20) }")
    public int execContextRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.station-table-rows-limit'), 5, 100, 50) }")
    public int stationRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.launchpad.account-table-rows-limit'), 5, 100, 20) }")
    public int accountRowsLimit;

    // left here for compatibility
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @Value("${mh.launchpad.is-replace-snapshot:#{null}}")
    public Boolean isReplaceSnapshot;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.default-result-file-extension')) }")
    public String defaultResultFileExtension;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.chunk-size')) }")
    public String chunkSizeStr;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.asset.mode')) }")
    public String assetModeStr;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.asset.source-url')) }")
    public String assetSourceUrl;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.asset.username')) }")
    public String assetUsername;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.launchpad.asset.password')) }")
    public String assetPassword;

    // Station's globals

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.logging.file' )) }")
    public File logFile;

    @Value("${mh.station.enabled:#{false}}")
    public boolean isStationEnabled = false;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.station.dir' )) }")
    public File stationDir;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.station.default-launchpad-yaml-file' )) }")
    public File defaultLaunchpadYamlFile;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.station.default-env-yaml-file' )) }")
    public File defaultEnvYamlFile;

    @Value("${mh.station.env-hot-deploy-supported:#{false}}")
    public boolean stationEnvHotDeploySupported = false;


    // some fields
    public File launchpadTempDir;
    public File launchpadResourcesDir;

    public File stationResourcesDir;
    public File stationTaskDir;
    public File stationEnvHotDeployDir;

    public PublicKey launchpadPublicKey = null;

    public Long chunkSize = null;

    public EnumsApi.OS os = EnumsApi.OS.unknown;
    public List<String> allowedOrigins;
    public EnumsApi.DispatcherAssetMode assetMode = EnumsApi.DispatcherAssetMode.local;

    // TODO 2019-07-28 need to handle this case
    //  https://stackoverflow.com/questions/37436927/utf-8-encoding-of-application-properties-attributes-in-spring-boot

    @PostConstruct
    public void init() {
        if (!isSecurityEnabled) {
            throw new GlobalConfigurationException("mh.launchpad.is-security-enabled==false isn't supported any more\nNeed to change to true and set up master's login/password");
        }
        String publicKeyAsStr = env.getProperty("MH_PUBLIC_KEY");
        if (publicKeyAsStr!=null && !publicKeyAsStr.isBlank()) {
            launchpadPublicKeyStr = publicKeyAsStr;
        }

        if (launchpadPublicKeyStr!=null) {
            launchpadPublicKey = SecUtils.getPublicKey(launchpadPublicKeyStr);
        }
        String threadNumberAsStr = env.getProperty("MH_THREAD_NUMBER");
        if (threadNumberAsStr!=null && !threadNumberAsStr.isBlank()) {
            try {
                threadNumber = Integer.parseInt(threadNumberAsStr);
            } catch (Throwable th) {
                log.error("Wrong value in env MH_THREAD_NUMBER, must be digit, " +
                        "actual: " + threadNumberAsStr+". Will be used a 4 thread value.");
                threadNumber = 4;
            }
        }

        String origins = env.getProperty("MH_CORS_ALLOWED_ORIGINS");
        if (!S.b(origins)) {
            allowedOriginsStr = origins;
        }
        if (S.b(allowedOriginsStr)) {
            allowedOriginsStr = "*";
        }
        allowedOrigins = Arrays.stream(StringUtils.split(allowedOriginsStr, ','))
                .map(String::strip)
                .filter(String::isBlank)
                .collect(Collectors.toList());

        if (allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("*");
        }

        String size = env.getProperty("MH_CHUNK_SIZE");
        chunkSize = parseChunkSizeValue(size);
        if (chunkSize==null) {
            chunkSize = parseChunkSizeValue(chunkSizeStr);
        }
        // we will use 10mb size of chunk by default
        if (chunkSize==null) {
            chunkSize = parseChunkSizeValue("10m");
        }

        if (!S.b(env.getProperty("MH_LAUNCHPAD_ASSET_MODE"))) {
            assetModeStr = env.getProperty("MH_LAUNCHPAD_ASSET_MODE");
        }
        if (!S.b(assetModeStr)) {
            try {
                assetMode = EnumsApi.DispatcherAssetMode.valueOf(assetModeStr);
            } catch (Throwable th) {
                throw new GlobalConfigurationException("Wrong value of assertMode, must be one of "+ Arrays.toString(EnumsApi.DispatcherAssetMode.values()) + ", " +
                        "actual value: " + assetModeStr);
            }
        }
        if (!S.b(env.getProperty("MH_LAUNCHPAD_ASSET_SOURCE_URL"))) {
            assetSourceUrl = env.getProperty("MH_LAUNCHPAD_ASSET_SOURCE_URL");
        }
        if (!S.b(env.getProperty("MH_LAUNCHPAD_ASSET_USERNAME"))) {
            assetUsername = env.getProperty("MH_LAUNCHPAD_ASSET_USERNAME");
        }
        if (!S.b(env.getProperty("MH_LAUNCHPAD_ASSET_PASSWORD"))) {
            assetPassword = env.getProperty("MH_LAUNCHPAD_ASSET_PASSWORD");
        }

        if (assetMode== EnumsApi.DispatcherAssetMode.replicated && S.b(assetSourceUrl)) {
            throw new GlobalConfigurationException("Wrong value of assertSourceUrl, must be not null when launchpadAssetMode==EnumsApi.DispatcherAssetMode.replicate");
        }
        if (!isLaunchpadEnabled) {
            log.warn("Launchpad wasn't enabled, assetMode will be set to DispatcherAssetMode.local");
            assetMode = EnumsApi.DispatcherAssetMode.local;
        }

        String stationEnabledAsStr = env.getProperty("MH_IS_STATION_ENABLED");
        if (!S.b(stationEnabledAsStr)) {
            try {
                isStationEnabled = Boolean.parseBoolean(stationEnabledAsStr.toLowerCase());
            } catch (Throwable th) {
                log.error("Wrong value in env MH_IS_STATION_ENABLED, must be boolean (true/false), " +
                        "actual: " + stationEnabledAsStr+". Will be used 'false' as value.");
                isStationEnabled = false;
            }
        }

        String eventEnabledAsStr = env.getProperty("MH_IS_EVENT_ENABLED");
        if (!S.b(eventEnabledAsStr)) {
            try {
                isEventEnabled = Boolean.parseBoolean(eventEnabledAsStr.toLowerCase());
            } catch (Throwable th) {
                log.error("Wrong value in env MH_IS_EVENT_ENABLED, must be boolean (true/false), " +
                        "actual: " + eventEnabledAsStr+". Will be used 'false' as value.");
                isEventEnabled = false;
            }
        }

        if (stationDir==null) {
            log.warn("Station is disabled, stationDir is null, isStationEnabled: {}", isStationEnabled);
            isStationEnabled = false;
        }
        if (isStationEnabled) {
            stationResourcesDir = new File(stationDir, Consts.RESOURCES_DIR);
            stationResourcesDir.mkdirs();
            stationTaskDir = new File(stationDir, Consts.TASK_DIR);
            stationTaskDir.mkdirs();
            if (stationEnvHotDeploySupported) {
                stationEnvHotDeployDir = new File(stationDir, Consts.ENV_HOT_DEPLOY_DIR);
                stationEnvHotDeployDir.mkdirs();
            }

            // TODO 2019.04.26 right now the change of ownership is disabled
            //  but maybe will be required in future
//            checkOwnership(stationEnvHotDeployDir);
        }

        String launchpadDirAsStr = env.getProperty("MH_LAUNCHPAD_DIR");
        if (launchpadDirAsStr!=null && !launchpadDirAsStr.isBlank()) {
            try {
                launchpadDir = new File(launchpadDirAsStr);
            } catch (Throwable th) {
                log.error("Wrong value in env MH_LAUNCHPAD_DIR, must be a correct path to dir, " +
                        "actual: " + launchpadDirAsStr);
                launchpadDir = null;
            }
        }
        String tempBranding = env.getProperty("MH_BRANDING");
        if (tempBranding!=null && !tempBranding.isBlank()) {
            branding = tempBranding;
        }
        if (branding==null || branding.isBlank()) {
            branding = "Metaheuristic project";
        }

        if (isLaunchpadEnabled && launchpadDir==null) {
            launchpadDir = new File("target/mh-launchpad");
            log.warn("Launchpad is enabled, but launchpadDir in null. " +
                    "Will be used a default value as: {}", launchpadDir.getAbsolutePath());
        }

        if (isLaunchpadEnabled) {
            if (launchpadMasterUsername==null || launchpadMasterPassword==null) {
                throw new GlobalConfigurationException(
                        "if mh.secure-rest-url=true, then mh.launchpad.master-username, " +
                                "and mh.launchpad.master-password have to be not null");
            }

            launchpadTempDir = new File(launchpadDir, "temp");
            launchpadTempDir.mkdirs();

            launchpadResourcesDir = new File(launchpadDir, Consts.RESOURCES_DIR);
            launchpadResourcesDir.mkdirs();

            String ext = env.getProperty("MH_DEFAULT_RESULT_FILE_EXTENSION");
            if (ext!=null && !ext.isBlank()) {
                defaultResultFileExtension = ext;
            }
            if (defaultResultFileExtension==null || defaultResultFileExtension.isBlank()) {
                defaultResultFileExtension = ".bin";
            }

            String sslRequiredAsStr = env.getProperty("MH_IS_SSL_REQUIRED");
            if (sslRequiredAsStr!=null && !sslRequiredAsStr.isBlank()) {
                try {
                    isSslRequired = Boolean.parseBoolean(sslRequiredAsStr);
                } catch (Throwable th) {
                    log.error("Wrong value in env MH_IS_SSL_REQUIRED, must be boolean (true/false), " +
                            "actual: " + sslRequiredAsStr+". Will be used 'true' as value.");
                    isSslRequired = true;
                }
            }
        }
        initOperationSystem();

        logGlobals();
        logSystemEnvs();
        logDepricated();
    }

    private void logDepricated() {
        if (isReplaceSnapshot!=null) {
            log.warn("property 'mh.launchpad.is-replace-snapshot' isn't supported any more and need to be deleted");
        }
    }

    private static final Map<Character, Long> sizes = Map.of(
            'b',1L, 'k',1024L, 'm', 1024L*1024, 'g', 1024L*1024*1024);

    private Long parseChunkSizeValue(String str) {
        if (str==null || str.isBlank()) {
            return null;
        }

        final char ch = Character.toLowerCase(str.charAt(str.length() - 1));
        if (Character.isLetter(ch)) {
            if (str.length()==1 || !sizes.containsKey(ch)) {
                throw new GlobalConfigurationException("Wrong value of chunkSize: " + str);
            }
            return Long.parseLong(str.substring(0, str.length()-1)) * sizes.get(ch);
        }
        return Long.parseLong(str);
    }

    private void initOperationSystem() {
        if (SystemUtils.IS_OS_WINDOWS) {
            os = EnumsApi.OS.windows;
        }
        else if (SystemUtils.IS_OS_LINUX) {
            os = EnumsApi.OS.linux;
        }
        else if (SystemUtils.IS_OS_MAC_OSX) {
            os = EnumsApi.OS.macos;
        }
        else {
            os = EnumsApi.OS.unknown;
        }
    }

    private void checkOwnership(File file) {
        if (!stationEnvHotDeploySupported) {
            return;
        }
        try {
            Path path = file.toPath();

            FileSystem fileSystem = path.getFileSystem();
            UserPrincipalLookupService service = fileSystem.getUserPrincipalLookupService();

            log.info("Check ownership of {}", stationEnvHotDeployDir.getPath());
            log.info("File: " + path);
            log.info("Exists: " + Files.exists(path));
            log.info("-- owner before --");
            UserPrincipal owner = Files.getOwner(path);
            log.info("Owner: " + owner);

            final String username = System.getProperty("user.name");
            log.info("current user name from env: " + username);
            log.info("current user name from lookup service: " + service.lookupPrincipalByName(username));
            if (systemOwner!=null) {
                UserPrincipal userPrincipal = service.lookupPrincipalByName(systemOwner);

                log.info("-- lookup '"+systemOwner+"' --");
                log.info("Found UserPrincipal: " + userPrincipal);

                //changing owner
                Files.setOwner(path, userPrincipal);

                log.info("-- owner after --");
                owner = Files.getOwner(path);
                log.info("Owner: " + owner.getName());
            }
            else {
                log.info("new system owner wasn't defined");
            }
        } catch (IOException e) {
            log.error("An error while checking ownership of path " + file.getPath(), e);
        }
    }

    private void logSystemEnvs() {
        System.getProperties().forEach( (o, o2) -> log.info("{}: {}", o, o2));
    }

    private void logGlobals() {
        log.info("Current globals:");
        log.info("'\tOS: {}", os);
        log.info("'\tthreadNumber: {}", threadNumber);
        log.info("'\tallowedOrigins: {}", allowedOriginsStr);
        log.info("'\tbranding: {}", branding);
        log.info("'\tisUnitTesting: {}", isUnitTesting);
        log.info("'\tisSecurityEnabled: {}", isSecurityEnabled);
        log.info("'\tisSslRequired: {}", isSslRequired);
        log.info("'\tisLaunchpadEnabled: {}", isLaunchpadEnabled);
        log.info("'\tlaunchpadDir: {}", launchpadDir!=null ? launchpadDir.getAbsolutePath() : null);
        log.info("'\tassetMode: {}", assetMode);
        log.info("'\tassetUsername: {}", assetUsername);
        log.info("'\tassetSourceUrl: {}", assetSourceUrl);
        log.info("'\tchunkSize: {}", chunkSize);
        log.info("'\tresourceRowsLimit: {}", globalVariableRowsLimit);
        log.info("'\texperimentRowsLimit: {}", experimentRowsLimit);
        log.info("'\tsourceCodeRowsLimit: {}", sourceCodeRowsLimit);
        log.info("'\texecContextRowsLimit: {}", execContextRowsLimit);
        log.info("'\tstationRowsLimit: {}", stationRowsLimit);
        log.info("'\taccountRowsLimit: {}", accountRowsLimit);
        log.info("'\tisStationEnabled: {}", isStationEnabled);
        log.info("'\tstationDir: {}", stationDir);
    }

    // TODO 2019-07-20 should method createTempFileForLaunchpad() be moved to other class/package?
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
