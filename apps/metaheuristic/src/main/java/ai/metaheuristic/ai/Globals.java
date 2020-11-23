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
import ai.metaheuristic.ai.utils.EnvProperty;
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
import org.springframework.lang.Nullable;
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
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@ToString
@RequiredArgsConstructor
public class Globals {

    private static final List<String> POSSIBLE_PROFILES = List.of("dispatcher", "processor", "quickstart");

    private final Environment env;

    @Value("${spring.profiles.active}")
    private String activeProfiles;

    // Globals' globals

    @Value("${mh.thread-number:#{null}}")
    public Integer oldThreadNumber;

    @Value("${mh.thread-number.scheduler:#{null}}")
    public Integer schedulerThreadNumber;

    @Value("${mh.thread-number.event:#{null}}")
    public Integer eventThreadNumber;

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

    // Globals of Dispatcher

    @Value("${mh.dispatcher.is-ssl-required:#{true}}")
    public boolean isSslRequired = true;

    @Value("${mh.dispatcher.function-signature-required:#{true}}")
    public boolean isFunctionSignatureRequired = true;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.master-username')) }")
    public String dispatcherMasterUsername;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.master-password')) }")
    public String dispatcherMasterPassword;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.public-key')) }")
    public String dispatcherPublicKeyStr;

    @Value("${mh.dispatcher.enabled:#{false}}")
    public boolean dispatcherEnabled = true;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.dispatcher.dir' )) }")
    public File dispatcherDir;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.max-tasks-per-exec-context'), 1, 100000, 5000) }")
    public int maxTasksPerExecContext;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.global-variable-table-rows-limit'), 5, 100, 20) }")
    public int globalVariableRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-table-rows-limit'), 5, 30, 10) }")
    public int experimentRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-result-table-rows-limit'), 5, 100, 20) }")
    public int experimentResultRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.source-code-table-rows-limit'), 5, 50, 25) }")
    public int sourceCodeRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.exec-context-table-rows-limit'), 5, 50, 20) }")
    public int execContextRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.processor-table-rows-limit'), 5, 100, 50) }")
    public int processorRowsLimit;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.account-table-rows-limit'), 5, 100, 20) }")
    public int accountRowsLimit;

    // left here for compatibility
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @Value("${mh.dispatcher.is-replace-snapshot:#{null}}")
    public Boolean isReplaceSnapshot;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.default-result-file-extension')) }")
    public String defaultResultFileExtension;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.chunk-size')) }")
    public String chunkSizeStr;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.asset.mode')) }")
    public @Nullable String assetModeStr;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.asset.source-url')) }")
    public @Nullable String assetSourceUrl;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.asset.username')) }")
    public @Nullable String assetUsername;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.asset.password')) }")
    public @Nullable String assetPassword;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).strIfNotBlankElseNull( environment.getProperty('mh.dispatcher.asset.sync-timeout')) }")
    public @Nullable String assetSyncTimeout;

    // Processor's globals

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.logging.file' )) }")
    public File logFile;

    @Value("${mh.processor.enabled:#{false}}")
    public boolean processorEnabled = false;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.processor.dir' )) }")
    public File processorDir;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.processor.default-dispatcher-yaml-file' )) }")
    public File defaultDispatcherYamlFile;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).toFile( environment.getProperty('mh.processor.default-env-yaml-file' )) }")
    public File defaultEnvYamlFile;

    @Value("${mh.processor.env-hot-deploy-supported:#{false}}")
    public boolean processorEnvHotDeploySupported = false;

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.task-console-output-max-lines'), 1000, 100000, 1000) }")
    public int taskConsoleOutputMaxLines;


    // some fields
    public File dispatcherTempDir;
    public File dispatcherResourcesDir;

    public File processorResourcesDir;
    public File processorTaskDir;
    public File processorEnvHotDeployDir;
//    public File processorTempDir;

    public PublicKey dispatcherPublicKey = null;

    public Long chunkSize = null;

    public EnumsApi.OS os = EnumsApi.OS.unknown;
    public List<String> allowedOrigins;
    public EnumsApi.DispatcherAssetMode assetMode = EnumsApi.DispatcherAssetMode.local;

    // TODO 2019-07-28 need to handle this case
    //  https://stackoverflow.com/questions/37436927/utf-8-encoding-of-application-properties-attributes-in-spring-boot

    @PostConstruct
    public void init() {
        checkProfiles();

        String publicKeyAsStr = env.getProperty("MH_PUBLIC_KEY");
        if (publicKeyAsStr!=null && !publicKeyAsStr.isBlank()) {
            dispatcherPublicKeyStr = publicKeyAsStr;
        }

        if (dispatcherPublicKeyStr !=null) {
            dispatcherPublicKey = SecUtils.getPublicKey(dispatcherPublicKeyStr);
        }
        if (dispatcherEnabled && isFunctionSignatureRequired && dispatcherPublicKey==null ) {
            throw new GlobalConfigurationException("mh.dispatcher.public-key wasn't configured for dispatcher (file application.properties) but mh.dispatcher.function-signature-required is true (default value)");
        }

        initSchedulerThreadNumber();

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
        Long tempChunkSize = parseChunkSizeValue(size);
        if (tempChunkSize==null) {
            tempChunkSize = parseChunkSizeValue(chunkSizeStr);
        }
        // we will use 10mb size of chunk by default
        if (tempChunkSize==null) {
            tempChunkSize = parseChunkSizeValue("10m");
        }
        chunkSize = Objects.requireNonNull(tempChunkSize);

        if (!S.b(env.getProperty("MH_DISPATCHER_ASSET_MODE"))) {
            assetModeStr = env.getProperty("MH_DISPATCHER_ASSET_MODE");
        }
        if (!S.b(assetModeStr)) {
            try {
                assetMode = EnumsApi.DispatcherAssetMode.valueOf(assetModeStr);
            } catch (Throwable th) {
                throw new GlobalConfigurationException("Wrong value of assertMode, must be one of "+ Arrays.toString(EnumsApi.DispatcherAssetMode.values()) + ", " +
                        "actual value: " + assetModeStr);
            }
        }
        if (!S.b(env.getProperty("MH_DISPATCHER_ASSET_SOURCE_URL"))) {
            assetSourceUrl = env.getProperty("MH_DISPATCHER_ASSET_SOURCE_URL");
        }
        if (!S.b(env.getProperty("MH_DISPATCHER_ASSET_USERNAME"))) {
            assetUsername = env.getProperty("MH_DISPATCHER_ASSET_USERNAME");
        }
        if (!S.b(env.getProperty("MH_DISPATCHER_ASSET_PASSWORD"))) {
            assetPassword = env.getProperty("MH_DISPATCHER_ASSET_PASSWORD");
        }

        if (assetMode== EnumsApi.DispatcherAssetMode.replicated && S.b(assetSourceUrl)) {
            throw new GlobalConfigurationException("Wrong value of assertSourceUrl, must be not null when dispatcherAssetMode==EnumsApi.DispatcherAssetMode.replicate");
        }
        if (!dispatcherEnabled) {
            log.warn("Dispatcher wasn't enabled, assetMode will be set to DispatcherAssetMode.local");
            assetMode = EnumsApi.DispatcherAssetMode.local;
        }

        String processorEnabledAsStr = env.getProperty("MH_IS_PROCESSOR_ENABLED");
        if (!S.b(processorEnabledAsStr)) {
            try {
                processorEnabled = Boolean.parseBoolean(processorEnabledAsStr.toLowerCase());
            } catch (Throwable th) {
                log.error("Wrong value in env MH_IS_PROCESSOR_ENABLED, must be boolean (true/false), " +
                        "actual: " + processorEnabledAsStr+". Will be used 'false' as value.");
                processorEnabled = false;
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

        if (processorDir ==null) {
            log.warn("Processor is disabled, processorDir is null, processorEnabled: {}", processorEnabled);
            processorEnabled = false;
        }
        if (processorEnabled) {
            processorResourcesDir = new File(processorDir, Consts.RESOURCES_DIR);
            processorResourcesDir.mkdirs();
            processorTaskDir = new File(processorDir, Consts.TASK_DIR);
            processorTaskDir.mkdirs();
            if (processorEnvHotDeploySupported) {
                processorEnvHotDeployDir = new File(processorDir, Consts.ENV_HOT_DEPLOY_DIR);
                processorEnvHotDeployDir.mkdirs();
            }

            // TODO 2019.04.26 right now the change of ownership is disabled
            //  but maybe will be required in future
//            checkOwnership(processorEnvHotDeployDir);
        }

        String tempBranding = env.getProperty("MH_BRANDING");
        if (tempBranding!=null && !tempBranding.isBlank()) {
            branding = tempBranding;
        }
        if (branding==null || branding.isBlank()) {
            branding = "Metaheuristic project";
        }

        String dispatcherDirAsStr = env.getProperty("MH_DISPATCHER_DIR");
        File dispatcherDirTemp = dispatcherDir;
        if (!S.b(dispatcherDirAsStr)) {
            try {
                dispatcherDirTemp = new File(dispatcherDirAsStr);
            } catch (Throwable th) {
                log.error("Wrong value in env MH_DISPATCHER_DIR, must be a correct path to dir, " +
                        "actual: " + dispatcherDirAsStr);
            }
        }
        if (dispatcherDirTemp==null) {
            dispatcherDirTemp = new File("target/mh-dispatcher");
            log.warn("DispatcherDir in null. " +
                    "Will be used a default value as: {}", dispatcherDirTemp.getAbsolutePath());
        }
        dispatcherDir = Objects.requireNonNull(dispatcherDirTemp);

        if (dispatcherEnabled) {
            if (dispatcherMasterUsername ==null || dispatcherMasterPassword ==null) {
                throw new GlobalConfigurationException(
                        "if mh.secure-rest-url=true, then mh.dispatcher.master-username, " +
                                "and mh.dispatcher.master-password have to be not null");
            }

            dispatcherTempDir = new File(dispatcherDir, "temp");
            dispatcherTempDir.mkdirs();

            dispatcherResourcesDir = new File(dispatcherDir, Consts.RESOURCES_DIR);
            dispatcherResourcesDir.mkdirs();

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

    private void initSchedulerThreadNumber() {
        String threadNumberAsStr = env.getProperty("MH_THREAD_NUMBER");
        Integer oldValue = oldThreadNumber;
        if (!S.b(threadNumberAsStr)) {
            try {
                oldThreadNumber = Integer.parseInt(threadNumberAsStr);
            } catch (Throwable th) {
                log.error("Wrong value in env MH_THREAD_NUMBER, must be digit, " +
                        "actual: " + threadNumberAsStr+". Will be used "+oldValue+" as a value for number of threads.");
                oldThreadNumber = oldValue;
            }
        }

        String schedulerThreadNumberAsStr = env.getProperty("MH_THREAD_NUMBER_SCHEDULER");
        Integer schedulerOldValue = schedulerThreadNumber;
        if (!S.b(threadNumberAsStr)) {
            try {
                schedulerThreadNumber = Integer.parseInt(threadNumberAsStr);
            } catch (Throwable th) {
                log.error("Wrong value in env MH_THREAD_NUMBER_SCHEDULER, must be digit, " +
                        "actual: " + schedulerThreadNumberAsStr+". Will be used "+schedulerOldValue+" as a value for number of threads.");
                schedulerThreadNumber = schedulerOldValue;
            }
        }
        if (schedulerThreadNumber==null) {
            schedulerThreadNumber = oldThreadNumber;
        }
        if (schedulerThreadNumber==null) {
            schedulerThreadNumber = 4;
        }
        else {
            schedulerThreadNumber = EnvProperty.minMax( schedulerThreadNumber, 1, 16);
        }
    }

    private void checkProfiles() {
        List<String> profiles = Arrays.stream(StringUtils.split(activeProfiles, ", "))
                .filter(o-> !POSSIBLE_PROFILES.contains(o))
                .peek(o-> log.error(S.f("\n!!! Unknown profile: %s\n", o)))
                .collect(Collectors.toList());

        if (!profiles.isEmpty()) {
            log.error("\nUnknown profile(s) was encountered in property spring.profiles.active.\nNeed to be fixed.\n" +
                    "Allowed profiles are: " + POSSIBLE_PROFILES);
            System.exit(-1);
        }
    }

    private void logDepricated() {
        if (isReplaceSnapshot!=null) {
            log.warn("property 'mh.dispatcher.is-replace-snapshot' isn't supported any more and need to be deleted");
        }
        if (oldThreadNumber!=null) {
            log.warn("property 'mh.thread-number' is deprecated, use 'mh.thread-number.scheduler' instead of. Or env 'MH_THREAD_NUMBER_SCHEDULER' instead of 'MH_THREAD_NUMBER' ");
        }
    }

    private static final Map<Character, Long> sizes = Map.of(
            'b',1L, 'k',1024L, 'm', 1024L*1024, 'g', 1024L*1024*1024);

    private @Nullable Long parseChunkSizeValue(@Nullable String str) {
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
        if (!processorEnvHotDeploySupported) {
            return;
        }
        try {
            Path path = file.toPath();

            FileSystem fileSystem = path.getFileSystem();
            UserPrincipalLookupService service = fileSystem.getUserPrincipalLookupService();

            log.info("Check ownership of {}", processorEnvHotDeployDir.getPath());
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
        final Runtime rt = Runtime.getRuntime();
        log.warn("Memory, free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        log.info("Current globals:");
        log.info("'\tOS: {}", os);
        log.info("'\toldThreadNumber: {}", oldThreadNumber);
        log.info("'\tschedulerThreadNumber: {}", schedulerThreadNumber);
        log.info("'\teventThreadNumber: {}", eventThreadNumber);
        log.info("'\tallowedOrigins: {}", allowedOriginsStr);
        log.info("'\tbranding: {}", branding);
        log.info("'\tisUnitTesting: {}", isUnitTesting);
        log.info("'\tisSslRequired: {}", isSslRequired);
        log.info("'\tdispatcherEnabled: {}", dispatcherEnabled);
        log.info("'\tisFunctionSignatureRequired: {}", isFunctionSignatureRequired);
        log.info("'\tdispatcherDir: {}", dispatcherDir!=null ? dispatcherDir.getAbsolutePath() : "<dispatcher dir is null>");
        log.info("'\tdispatcherMasterUsername: {}", dispatcherMasterUsername);
        log.info("'\tdispatcherPublicKey: {}", dispatcherPublicKey!=null ? "provided" : "wasn't provided");
        log.info("'\tassetMode: {}", assetMode);
        log.info("'\tassetUsername: {}", assetUsername);
        log.info("'\tassetSourceUrl: {}", assetSourceUrl);
        log.info("'\tassetSyncTimeout: {}", assetSyncTimeout);
        log.info("'\tchunkSize: {}", chunkSize);
        log.info("'\tresourceRowsLimit: {}", globalVariableRowsLimit);
        log.info("'\texperimentRowsLimit: {}", experimentRowsLimit);
        log.info("'\tsourceCodeRowsLimit: {}", sourceCodeRowsLimit);
        log.info("'\texecContextRowsLimit: {}", execContextRowsLimit);
        log.info("'\tprocessorRowsLimit: {}", processorRowsLimit);
        log.info("'\taccountRowsLimit: {}", accountRowsLimit);
        log.info("'\tprocessorEnabled: {}", processorEnabled);
        log.info("'\tprocessorDir: {}", processorDir !=null ? processorDir.getAbsolutePath() : "<processor dir is null>");
    }

    // TODO 2019-07-20 should method createTempFileForDispatcher() be moved to other class/package?
    private static final Random r = new Random();
    public File createTempFileForDispatcher(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            throw new IllegalStateException("Prefix is blank");
        }
        File tempFile = new File(dispatcherTempDir,
                prefix + r.nextInt(99999999) + '-' + System.nanoTime()
        );
        return tempFile;
    }
}
