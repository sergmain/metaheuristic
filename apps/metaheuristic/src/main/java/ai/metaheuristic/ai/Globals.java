/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.PublicKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@ConfigurationProperties("mh")
@Getter
@Setter
@Slf4j
@RequiredArgsConstructor
public class Globals {

    private final Environment env;

    public static final String METAHEURISTIC_PROJECT = "Metaheuristic project";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DispatcherDir {
        public File dir = new File("target"+ File.separatorChar + "mh-dispatcher");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessorDir {
        public File dir = null;
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class PublicKeyConverter implements Converter<String, PublicKey> {
        @Nullable
        @Override
        public PublicKey convert(String from) {
            if (S.b(from)) {
                return null;
            }
            return SecUtils.getPublicKey(from);
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class AssetModeConverter implements Converter<String, EnumsApi.DispatcherAssetMode> {
        @Override
        public EnumsApi.DispatcherAssetMode convert(String from) {
            try {
                return EnumsApi.DispatcherAssetMode.valueOf(from);
            } catch (Throwable th) {
                throw new GlobalConfigurationException("Wrong value of assertMode, must be one of "+ Arrays.toString(EnumsApi.DispatcherAssetMode.values()) + ", " +
                        "actual value: " + from);
            }
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class DispatcherDirConverter implements Converter<String, DispatcherDir> {
        @Override
        public DispatcherDir convert(String from) {
            if (S.b(from)) {
                return new DispatcherDir();
            }
            return new DispatcherDir(toFile(from));
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class ProcessorDirConverter implements Converter<String, ProcessorDir> {
        @Override
        public ProcessorDir convert(String from) {
            return new ProcessorDir(toFile(from));
        }
    }

    @Getter
    @Setter
    public static class Asset {
        public EnumsApi.DispatcherAssetMode mode = EnumsApi.DispatcherAssetMode.local;

        @Nullable
        public String sourceUrl;

        @Nullable
        public String username;

        @Nullable
        public String password;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration syncTimeout = Duration.ofSeconds(20);
    }

    @Getter
    @Setter
    public static class RowsLimit {
        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.global-variable-table-rows-limit'), 5, 100, 20) }")
        public int globalVariableTable;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-table-rows-limit'), 5, 30, 10) }")
        public int experiment;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-result-table-rows-limit'), 5, 100, 20) }")
        public int experimentResult;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.source-code-table-rows-limit'), 5, 50, 25) }")
        public int sourceCode;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.exec-context-table-rows-limit'), 5, 50, 20) }")
        public int execContext;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.processor-table-rows-limit'), 5, 100, 50) }")
        public int processor;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.account-table-rows-limit'), 5, 100, 20) }")
        public int account;
    }

    @Getter
    @Setter
    public static class DispatcherTimeout {
        @DurationUnit(ChronoUnit.SECONDS)
        public Duration gc = Duration.ofSeconds(3600);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration artifactCleaner = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class Dispatcher {
        public Asset asset = new Asset();
        public RowsLimit rowsLimit = new RowsLimit();
        public DispatcherTimeout timeout = new DispatcherTimeout();

        @DurationUnit(ChronoUnit.DAYS)
        public Duration keepEventsInDb = Duration.ofDays(90);

        public boolean isSslRequired = true;

        public boolean functionSignatureRequired = true;
        public boolean enabled = false;

        @Nullable
        public String masterUsername;

        @Nullable
        public String masterPassword;

        @Nullable
        public PublicKey publicKey;

        public DispatcherDir dir = new DispatcherDir();

        public String defaultResultFileExtension = ".bin";

        @DataSizeUnit(DataUnit.MEGABYTES)
        public DataSize chunkSize = DataSize.ofMegabytes(10);

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.global-variable-table")
        @Deprecated
        public int getGlobalVariableRowsLimit() {
            return rowsLimit.globalVariableTable;
        }

        public void setExperimentRowsLimit(int experimentRowsLimit) {
            this.rowsLimit.experiment = experimentRowsLimit;
        }

        public void setExperimentResultRowsLimit(int experimentResultRowsLimit) {
            this.rowsLimit.experimentResult = experimentResultRowsLimit;
        }

        public void setSourceCodeRowsLimit(int sourceCodeRowsLimit) {
            this.rowsLimit.sourceCode = sourceCodeRowsLimit;
        }

        public void setExecContextRowsLimit(int execContextRowsLimit) {
            this.rowsLimit.execContext = execContextRowsLimit;
        }

        public void setProcessorRowsLimit(int processorRowsLimit) {
            this.rowsLimit.processor = processorRowsLimit;
        }

        public void setAccountRowsLimit(int accountRowsLimit) {
            this.rowsLimit.account = accountRowsLimit;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.experiment")
        @Deprecated
        public int getExperimentRowsLimit() {
            return rowsLimit.experiment;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.experiment-result")
        @Deprecated
        public int getExperimentResultRowsLimit() {
            return rowsLimit.experimentResult;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.source-code")
        @Deprecated
        public int getSourceCodeRowsLimit() {
            return rowsLimit.sourceCode;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.exec-context")
        @Deprecated
        public int getExecContextRowsLimit() {
            return rowsLimit.execContext;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.processor")
        @Deprecated
        public int getProcessorRowsLimit() {
            return rowsLimit.processor;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.dispatcher.rows-limit.account")
        @Deprecated
        public int getAccountRowsLimit() {
            return rowsLimit.account;
        }
    }

    @Getter
    @Setter
    public static class ProcessorTimeout {
        @DurationUnit(ChronoUnit.SECONDS)
        public Duration requestDispatcher = Duration.ofSeconds(6);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration taskAssigner = Duration.ofSeconds(5);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration taskProcessor = Duration.ofSeconds(9);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration downloadFunction = Duration.ofSeconds(11);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration prepareFunctionForDownloading = Duration.ofSeconds(31);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration downloadResource = Duration.ofSeconds(3);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration uploadResultResource = Duration.ofSeconds(3);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration getDispatcherContextInfo = Duration.ofSeconds(19);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration artifactCleaner = Duration.ofSeconds(29);

    }

    @Getter
    @Setter
    public static class Processor {
        public ProcessorTimeout timeout = new ProcessorTimeout();

        public boolean enabled = false;
        public ProcessorDir dir = null;

        @Nullable
        public File defaultDispatcherYamlFile = null;

        @Nullable
        public File defaultEnvYamlFile = null;

//        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.task-console-output-max-lines'), 1000, 100000, 1000) }")
        public int taskConsoleOutputMaxLines;

        public void setTaskConsoleOutputMaxLines(int taskConsoleOutputMaxLines) {
            this.taskConsoleOutputMaxLines = EnvProperty.minMax( taskConsoleOutputMaxLines, 1000, 100000);
        }

        public int initCoreNumber = 1;
    }

    @Getter
    public static class ThreadNumber {
        public int scheduler = 10;
        public int event =  Math.max(10, Runtime.getRuntime().availableProcessors()/2);

        public void setScheduler(int scheduler) {
            this.scheduler = EnvProperty.minMax( scheduler, 10, 32);
        }

        public void setEvent(int event) {
            this.event = EnvProperty.minMax( event, 10, 32);
        }
    }

    public final Dispatcher dispatcher = new Dispatcher();
    public final Processor processor = new Processor();
    public final ThreadNumber threadNumber = new ThreadNumber();

    public boolean testing = false;

    @Nullable
    public String systemOwner = null;

    public String branding = METAHEURISTIC_PROJECT;

    @Nullable
    public List<String> corsAllowedOrigins = new ArrayList<>(List.of("*"));

    public boolean isUnitTesting = false;
    public boolean isEventEnabled = false;


    // some fields
    public File dispatcherTempDir;
    public File dispatcherResourcesDir;
    public File processorResourcesDir;

    public EnumsApi.OS os = EnumsApi.OS.unknown;

    @PostConstruct
    public void postConstruct() {
        if (dispatcher.enabled && dispatcher.functionSignatureRequired && dispatcher.publicKey==null ) {
            throw new GlobalConfigurationException("mh.dispatcher.public-key wasn't configured for dispatcher (file application.properties) but mh.dispatcher.function-signature-required is true (default value)");
        }
        if (dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated && S.b(dispatcher.asset.sourceUrl)) {
            throw new GlobalConfigurationException("Wrong value of assertSourceUrl, must be not null when dispatcherAssetMode==EnumsApi.DispatcherAssetMode.replicate");
        }
        if (dispatcher.enabled) {
            if (S.b(dispatcher.masterUsername) || S.b(dispatcher.masterPassword)) {
                throw new GlobalConfigurationException(
                        "if mh.secure-rest-url=true, then mh.dispatcher.master-username, " +
                                "and mh.dispatcher.master-password have to be not null");
            }
        }

        if (processor.dir.dir ==null) {
            log.warn("Processor will be disabled, processorDir is null, processorEnabled: {}", processor.enabled);
            processor.enabled = false;
        }

        if (!dispatcher.enabled) {
            log.warn("Dispatcher wasn't enabled, assetMode will be set to DispatcherAssetMode.local");
            dispatcher.asset.mode = EnumsApi.DispatcherAssetMode.local;
        }

        if (processor.enabled) {
            processorResourcesDir = new File(processor.dir.dir, Consts.RESOURCES_DIR);
            processorResourcesDir.mkdirs();

            // TODO 2019.04.26 right now the change of ownership is disabled
            //  but maybe will be required in future
//            checkOwnership(processorEnvHotDeployDir);
        }

        if (dispatcher.enabled) {
            dispatcherTempDir = new File(dispatcher.dir.dir, "temp");
            dispatcherTempDir.mkdirs();

            dispatcherResourcesDir = new File(dispatcher.dir.dir, Consts.RESOURCES_DIR);
            dispatcherResourcesDir.mkdirs();
        }
        initOperationSystem();

        logGlobals();
        logSystemEnvs();
        logDeprecated();
    }

    public void setBranding(String branding) {
        this.branding = S.b(branding) ? METAHEURISTIC_PROJECT : branding;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        if (corsAllowedOrigins.isEmpty()) {
            return;
        }
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Nullable
    private static File toFile(@Nullable String dirAsString) {
        if (StringUtils.isBlank(dirAsString)) {
            return null;
        }

        // special case for ./some-dir
        if (dirAsString.charAt(0) == '.' && (dirAsString.charAt(1) == '\\' || dirAsString.charAt(1) == '/')) {
            return new File(dirAsString.substring(2));
        }
        return new File(dirAsString);
    }


    // TODO 2019-07-28 need to handle this case
    //  https://stackoverflow.com/questions/37436927/utf-8-encoding-of-application-properties-attributes-in-spring-boot

    private static final List<Pair<String,String>> checkEnvs = List.of(
            Pair.of("MH_CORS_ALLOWED_ORIGINS", "MH_CORSALLOWEDORIGINS"),
            Pair.of("MH_THREAD_NUMBER_SCHEDULER", "MH_THREADNUMBER_SCHEDULER"),
            Pair.of("MH_PUBLIC_KEY", "MH_DISPATCHER_PUBLICKEY"),
            Pair.of("MH_IS_SSL_REQUIRED", "MH_DISPATCHER_ISSSLREQUIRED"),
            Pair.of("MH_DEFAULT_RESULT_FILE_EXTENSION", "MH_DEFAULT_RESULTFILEEXTENSION"),
            Pair.of("MH_IS_EVENT_ENABLED", "MH_ISEVENTENABLED"),
            Pair.of("MH_CHUNK_SIZE", "MH_DISPATCHER_CHUNKSIZE"),
            Pair.of("MH_DISPATCHER_ASSET_SOURCE_URL", "MH_DISPATCHER_ASSET_SOURCE_URL")
    );

    private void logDeprecated() {
        boolean isError = false;
        for (Pair<String, String> checkEnv : checkEnvs) {
            if (env.getProperty(checkEnv.getKey())!=null) {
                isError = true;
                log.warn("environment variable "+checkEnv.getKey()+" must be replaced with "+checkEnv.getValue());
            }
        }
        if (isError) {
            throw new GlobalConfigurationException("there is some error in configuration of environment variable. sse log above");
        }
    }

    private static final Map<Character, Long> sizes = Map.of(
            'b',1L, 'k',1024L, 'm', 1024L*1024, 'g', 1024L*1024*1024);

    @Nullable
    private static Long parseChunkSizeValue(@Nullable String str) {
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

    private static void checkOwnership(File file, @Nullable String systemOwner) {
        try {
            Path path = file.toPath();

            FileSystem fileSystem = path.getFileSystem();
            UserPrincipalLookupService service = fileSystem.getUserPrincipalLookupService();

            log.info("Check ownership of {}", file.getAbsolutePath());
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

    private static void logSystemEnvs() {
        System.getProperties().forEach( (o, o2) -> log.info("{}: {}", o, o2));
    }

    private void logGlobals() {
        final Runtime rt = Runtime.getRuntime();
        log.warn("Memory, free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        log.info("Current globals:");
        log.info("'\tOS: {}", os);
        log.info("'\tschedulerThreadNumber: {}", threadNumber.scheduler);
        log.info("'\teventThreadNumber: {}", threadNumber.event);
        log.info("'\tallowedOrigins: {}", corsAllowedOrigins);
        log.info("'\tbranding: {}", branding);
        log.info("'\tisUnitTesting: {}", isUnitTesting);
        log.info("'\tdispatcher.isSslRequired: {}", dispatcher.isSslRequired);
        log.info("'\tdispatcher.enabled: {}", dispatcher.enabled);
        log.info("'\tdispatcher.functionSignatureRequired: {}", dispatcher.functionSignatureRequired);
        log.info("'\tdispatcher.dir: {}", dispatcher.dir.dir!=null ? dispatcher.dir.dir.getAbsolutePath() : "<dispatcher dir is null>");
        log.info("'\tdispatcher.masterUsername: {}", dispatcher.masterUsername);
        log.info("'\tdispatcher.publicKey: {}", dispatcher.publicKey!=null ? "provided" : "wasn't provided");
        log.info("'\tdispatcher.asset.mode: {}", dispatcher.asset.mode);
        log.info("'\tassetUsername: {}", dispatcher.asset.username);
        log.info("'\tassetSourceUrl: {}", dispatcher.asset.sourceUrl);
        log.info("'\tdispatcher.asset.syncTimeout: {}", dispatcher.asset.getSyncTimeout().toSeconds() +" seconds");
        log.info("'\tdispatcher.chunkSize: {}", dispatcher.chunkSize);
        log.info("'\tdispatcher.rowsLimit.globalVariableTable: {}", dispatcher.rowsLimit.globalVariableTable);
        log.info("'\tdispatcher.rowsLimit.experiment: {}", dispatcher.rowsLimit.experiment);
        log.info("'\tdispatcher.rowsLimit.sourceCode: {}", dispatcher.rowsLimit.sourceCode);
        log.info("'\tdispatcher.rowsLimit.execContext: {}", dispatcher.rowsLimit.execContext);
        log.info("'\tdispatcher.rowsLimit.processor: {}", dispatcher.rowsLimit.processor);
        log.info("'\tdispatcher.rowsLimit.account: {}", dispatcher.rowsLimit.account);
        log.info("'\tdispatcher.rowsLimit.processor: {}", dispatcher.rowsLimit.processor);
        log.info("'\tprocessor.dir: {}", processor.dir.dir !=null ? processor.dir.dir.getAbsolutePath() : "<processor dir is null>");
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
