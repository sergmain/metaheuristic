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

import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.dispatcher.data.KbData;
import ai.metaheuristic.ai.exceptions.GlobalConfigurationException;
import ai.metaheuristic.ai.utils.EnvProperty;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.SecUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.boot.convert.PeriodUnit;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties("mh")
@Getter
@Setter
@Slf4j
@RequiredArgsConstructor
public class Globals {
    public static final Duration SECONDS_3 = Duration.ofSeconds(3);
    public static final Duration SECONDS_5 = Duration.ofSeconds(5);
    public static final Duration SECONDS_6 = Duration.ofSeconds(6);
    public static final Duration SECONDS_9 = Duration.ofSeconds(9);
    public static final Duration SECONDS_10 = Duration.ofSeconds(10);
    public static final Duration SECONDS_11 = Duration.ofSeconds(11);
    public static final Duration SECONDS_19 = Duration.ofSeconds(19);
    public static final Duration SECONDS_23 = Duration.ofSeconds(23);
    public static final Duration SECONDS_29 = Duration.ofSeconds(29);
    public static final Duration SECONDS_31 = Duration.ofSeconds(31);
    public static final Duration SECONDS_60 = Duration.ofSeconds(60);
    public static final Duration SECONDS_120 = Duration.ofSeconds(120);
    public static final Duration SECONDS_3600 = Duration.ofSeconds(3600);
    public static final Duration DAYS_14 = Duration.ofDays(14);
    public static final Period DAYS_90 = Period.ofDays(90);
    public static final Period DAYS_IN_YEARS_3 = Period.ofDays(365*3);


    private final Environment env;

    public static final String METAHEURISTIC_PROJECT = "Metaheuristic project";

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
        public Duration syncTimeout = SECONDS_120;

        public Duration getSyncTimeout() {
            return syncTimeout.toSeconds() >= 60 && syncTimeout.toSeconds() <= 3600 ? syncTimeout : SECONDS_120;
        }
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KbPath {
        public String evals;
        public String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Git implements KbData.KbGit {
        public String repo;
        public String branch;
        public String commit;
        public List<KbPath> kbPaths = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KbFile {
        public String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kb {
        public String code;
        public String type;
        public boolean disabled = false;
        public Git git;
        public KbFile file;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Max {
        public int consoleOutputLines = 1000;

        // in unicode units. i.e. String.length()
        public int promptLength = 4096;
        public int errorsPerPart = 1;
        // has effect only with a local executor of requests
        public int errorsPerEvaluation = 5;
        public int promptsPerPart = 10000;
    }

    @Getter
    @Setter
    public static class RowsLimit {
        public int defaultLimit = 20;
        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.global-variable-table-rows-limit'), 5, 100, 20) }")
        public int globalVariableTable = 20;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-table-rows-limit'), 5, 30, 10) }")
        public int experiment = 10;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.experiment-result-table-rows-limit'), 5, 100, 20) }")
        public int experimentResult = 20;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.source-code-table-rows-limit'), 5, 50, 25) }")
        public int sourceCode = 25;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.exec-context-table-rows-limit'), 5, 50, 20) }")
        public int execContext = 20;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.processor-table-rows-limit'), 5, 100, 50) }")
        public int processor = 50;

        //        @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.dispatcher.account-table-rows-limit'), 5, 100, 20) }")
        public int account = 20;
    }

    @Setter
    public static class DispatcherTimeout {

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration gc = Duration.ofSeconds(3600);

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration artifactCleaner = SECONDS_60;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration updateBatchStatuses = Duration.ofSeconds(5);

        /**
         * period of time after which a virtually deleted batch will be deleted from db
         */
        @DurationUnit(ChronoUnit.DAYS)
        public Duration batchDeletion = DAYS_14;

        public Duration getBatchDeletion() {
            return batchDeletion.toSeconds() >= 7 && batchDeletion.toSeconds() <= 180 ? batchDeletion : DAYS_14;
        }

        public Duration getArtifactCleaner() {
            return artifactCleaner.toSeconds() >= 60 && artifactCleaner.toSeconds() <=600 ? artifactCleaner : SECONDS_60;
        }

        public Duration getGc() {
            return gc.toSeconds() >= 600 && gc.toSeconds() <= 3600*24*7 ? gc : SECONDS_3600;
        }

        public Duration getUpdateBatchStatuses() {
            return updateBatchStatuses.toSeconds() >= 5 && updateBatchStatuses.toSeconds() <=60 ? updateBatchStatuses : SECONDS_23;
        }

        public void setGc(Duration gc) {
            this.gc = gc;
        }

        public void setArtifactCleaner(Duration artifactCleaner) {
            this.artifactCleaner = artifactCleaner;
        }

        public void setUpdateBatchStatuses(Duration updateBatchStatuses) {
            this.updateBatchStatuses = updateBatchStatuses;
        }

        public void setBatchDeletion(Duration batchDeletion) {
            this.batchDeletion = batchDeletion;
        }
    }

    @Getter
    @Setter
    public static class Dispatcher {
        public Asset asset = new Asset();
        public RowsLimit rowsLimit = new RowsLimit();
        public DispatcherTimeout timeout = new DispatcherTimeout();

        @PeriodUnit(ChronoUnit.DAYS)
        public Period keepEventsInDb = Period.ofDays(90);

        public boolean functionSignatureRequired = true;
        public boolean enabled = false;

        @Nullable
        public String masterUsername;

        @Nullable
        public String masterPassword;

        @Nullable
        public PublicKey publicKey;

        public String defaultResultFileExtension = ".bin";

        public int maxTriesAfterError = 3;

        @DataSizeUnit(DataUnit.MEGABYTES)
        public DataSize chunkSize = DataSize.ofMegabytes(10);

        public Period getKeepEventsInDb() {
            return keepEventsInDb.getDays() >= 7 && keepEventsInDb.getDays() <= DAYS_IN_YEARS_3.getDays() ? keepEventsInDb : DAYS_90;
        }

        public void setMaxTriesAfterError(int maxTriesAfterError) {
            this.maxTriesAfterError = EnvProperty.minMax(maxTriesAfterError, 0, 10);
        }

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
        public Duration requestDispatcher = SECONDS_6;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration taskAssigner = SECONDS_5;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration taskProcessor = SECONDS_9;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration downloadFunction = SECONDS_11;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration prepareFunctionForDownloading = SECONDS_31;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration downloadResource = SECONDS_3;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration uploadResultResource = SECONDS_3;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration dispatcherContextInfo = SECONDS_19;

        @DurationUnit(ChronoUnit.SECONDS)
        public Duration artifactCleaner = SECONDS_29;

        public Duration getRequestDispatcher() {
            return requestDispatcher.toSeconds() >= 6 && requestDispatcher.toSeconds() <= 30 ? requestDispatcher : SECONDS_10;
        }

        public Duration getTaskAssigner() {
            return taskAssigner.toSeconds() >= 3 && taskAssigner.toSeconds() <= 20 ? taskAssigner : SECONDS_5;
        }

        public Duration getTaskProcessor() {
            return taskProcessor.toSeconds() >= 3 && taskProcessor.toSeconds() <= 20 ? taskProcessor : SECONDS_9;
        }

        public Duration getDownloadFunction() {
            return downloadFunction.toSeconds() >= 3 && downloadFunction.toSeconds() <= 20 ? downloadFunction : SECONDS_11;
        }

        public Duration getPrepareFunctionForDownloading() {
            return prepareFunctionForDownloading.toSeconds() >= 20 && prepareFunctionForDownloading.toSeconds() <= 60 ? prepareFunctionForDownloading : SECONDS_31;
        }

        public Duration getDownloadResource() {
            return downloadResource.toSeconds() >= 3 && downloadResource.toSeconds() <= 20 ? downloadResource : SECONDS_3;
        }

        public Duration getUploadResultResource() {
            return uploadResultResource.toSeconds() >= 3 && uploadResultResource.toSeconds() <= 20 ? uploadResultResource : SECONDS_3;
        }

        public Duration getDispatcherContextInfo() {
            return dispatcherContextInfo.toSeconds() >= 10 && dispatcherContextInfo.toSeconds() <= 60 ? dispatcherContextInfo : SECONDS_19;
        }

        public Duration getArtifactCleaner() {
            return artifactCleaner.toSeconds() >= 10 && artifactCleaner.toSeconds() <= 60 ? artifactCleaner : SECONDS_29;
        }

        @DeprecatedConfigurationProperty(replacement = "mh.processor.timeout.dispatcher-context-info")
        @Deprecated
        public Duration getGetDispatcherContextInfo() {
            return getDispatcherContextInfo();
        }

        @DeprecatedConfigurationProperty(replacement = "mh.processor.timeout.dispatcher-context-info")
        @Deprecated
        public void setGetDispatcherContextInfo(Duration getDispatcherContextInfo) {
            this.dispatcherContextInfo = getDispatcherContextInfo;
        }

    }

    @Getter
    @Setter
    public static class Processor {
        public ProcessorTimeout timeout = new ProcessorTimeout();

        public boolean enabled = false;

        @Nullable
        public File defaultDispatcherYamlFile = null;

        @Nullable
        public File defaultEnvYamlFile = null;

        public int taskConsoleOutputMaxLines = 1000;

        public void setTaskConsoleOutputMaxLines(int taskConsoleOutputMaxLines) {
            this.taskConsoleOutputMaxLines = EnvProperty.minMax( taskConsoleOutputMaxLines, 1000, 100000);
        }

        public int initCoreNumber = 1;
    }

    @Getter
    @Setter
    public static class Mhbp {
        public final Max max = new Max();
        public Kb[] kb;
    }

    public static class ThreadNumber {
        private int scheduler = 10;
        private int event =  Math.max(10, Runtime.getRuntime().availableProcessors()/2);
        public int queryApi =  2;

        public int getQueryApi() {
            return EnvProperty.minMax( queryApi, 2, 100);
        }

        public void setQueryApi(int queryApi) {
            this.queryApi = queryApi;
        }

        public int getScheduler() {
            return EnvProperty.minMax( scheduler, 10, 32);
        }

        public int getEvent() {
            return EnvProperty.minMax( event, 10, 32);
        }

        public void setScheduler(int scheduler) {
            this.scheduler = scheduler;
        }

        public void setEvent(int event) {
            this.event = event;
        }
    }

    public final Dispatcher dispatcher = new Dispatcher();
    public final Processor processor = new Processor();
    public final ThreadNumber threadNumber = new ThreadNumber();
    public final Mhbp mhbp = new Mhbp();

    @Nullable
    public String systemOwner = null;

    public String branding = METAHEURISTIC_PROJECT;

    @Nullable
    public List<String> corsAllowedOrigins = new ArrayList<>(List.of("*"));

    public boolean testing = false;
    public boolean eventEnabled = false;

    public boolean sslRequired = true;

    public Path home;

    public Path getHome() {
        if (home==null) {
            String mhHome = System.getenv("MH_HOME");
            if (S.b(mhHome)) {
                throw new IllegalArgumentException("mh.home isn't specified");
            }
            home = Path.of(mhHome);
        }
        return home;
    }

    // some fields, will be inited in postConstruct()
    public Path dispatcherTempPath;
    public Path dispatcherResourcesPath;
    public Path dispatcherPath;
    public Path processorResourcesPath;
    public Path processorPath;

    public EnumsApi.OS os = EnumsApi.OS.unknown;

    @SneakyThrows
    @PostConstruct
    public void postConstruct() {
        dispatcherPath = getHome().resolve("dispatcher");
        Files.createDirectories(dispatcherPath);
        processorPath = getHome().resolve("processor");
        Files.createDirectories(processorPath);

        if (dispatcher.enabled && dispatcher.functionSignatureRequired && dispatcher.publicKey==null ) {
            throw new GlobalConfigurationException("mh.dispatcher.public-key wasn't configured for dispatcher (file application.properties) but mh.dispatcher.function-signature-required is true (default value)");
        }
        if (dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated && S.b(dispatcher.asset.sourceUrl)) {
            throw new GlobalConfigurationException("Wrong value of assertSourceUrl, must be not null when dispatcherAssetMode==EnumsApi.DispatcherAssetMode.replicate");
        }
        if (dispatcher.enabled) {
            if (S.b(dispatcher.masterUsername) || S.b(dispatcher.masterPassword)) {
                throw new GlobalConfigurationException(
                        "if dispatcher.enabled, then mh.dispatcher.master-username, " +
                                "and mh.dispatcher.master-password have to be not null");
            }
        }

        if (!dispatcher.enabled) {
            log.warn("Dispatcher wasn't enabled, assetMode will be set to DispatcherAssetMode.local");
            dispatcher.asset.mode = EnumsApi.DispatcherAssetMode.local;
        }

        if (processor.enabled) {
            processorResourcesPath = processorPath.resolve(Consts.RESOURCES_DIR);
            Files.createDirectories(processorResourcesPath);

            // TODO 2019.04.26 right now the change of ownership is disabled
            //  but maybe will be required in future
//            checkOwnership(processorEnvHotDeployDir);
        }

        if (dispatcher.enabled) {
            dispatcherTempPath = dispatcherPath.resolve("temp");
            PathUtils.createParentDirectories(dispatcherTempPath);

            dispatcherResourcesPath = dispatcherPath.resolve(Consts.RESOURCES_DIR);
            Files.createDirectories(dispatcherResourcesPath);
        }
        initOperationSystem();

        logGlobals();
        logSystemEnvs();
        logGarbageCollectors();
//        logDeprecated();

    }

    private void logUlimitSh() {
        if (os==EnumsApi.OS.linux) {
            SystemProcessLauncher.ExecResult result = SystemProcessLauncher.execCmd(List.of("ulimit", "-Sn"), 10L, processor.taskConsoleOutputMaxLines);
            if (!result.ok) {
                log.warn("Error of getting ulimit");
                log.warn("\tresult.ok: {}",  result.ok);
                log.warn("\tresult.error: {}",  result.error);
                log.warn("\tresult.functionDir: {}",  result.functionDir !=null ? result.functionDir.getPath() : null);
                log.warn("\tresult.systemExecResult: {}",  result.systemExecResult);
                return;
            }

            // at this point result.systemExecResult must be not null, it can be null only if result.ok==false, but see above
            if (result.systemExecResult.exitCode!=0) {
                log.info("ulimit wasn't found");
                return;
            }
            log.info("ulimit -Sn: " + result.systemExecResult.console.toLowerCase().strip());
        }
    }

    private static void logGarbageCollectors() {
        log.info("Garbage collectors:");
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : beans) {
            log.info("'\t"+ bean.getName());
        }
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
    private static KbFile toFile(@Nullable String dirAsString) {
        if (S.b(dirAsString)) {
            return null;
        }

        // special case for ./some-dir
        if (dirAsString.charAt(0) == '.' && (dirAsString.charAt(1) == '\\' || dirAsString.charAt(1) == '/')) {
            return new KbFile(dirAsString.substring(2));
        }
        return new KbFile(dirAsString);
    }


    // TODO 2019-07-28 need to handle this case
    //  https://stackoverflow.com/questions/37436927/utf-8-encoding-of-application-properties-attributes-in-spring-boot

    private static final List<Pair<String,String>> checkEnvs = List.of(
            Pair.of("MH_CORS_ALLOWED_ORIGINS", "MH_CORSALLOWEDORIGINS"),
            Pair.of("MH_THREAD_NUMBER_SCHEDULER", "MH_THREADNUMBER_SCHEDULER"),
            Pair.of("MH_PUBLIC_KEY", "MH_DISPATCHER_PUBLICKEY"),
            Pair.of("MH_DISPATCHER_ISSSLREQUIRED", "MH_IS_SSL_REQUIRED"),
            Pair.of("MH_DEFAULT_RESULT_FILE_EXTENSION", "MH_DEFAULT_RESULTFILEEXTENSION"),
            Pair.of("MH_IS_EVENT_ENABLED", "MH_ISEVENTENABLED"),
            Pair.of("MH_CHUNK_SIZE", "MH_DISPATCHER_CHUNKSIZE"),
            Pair.of("MH_DISPATCHER_ASSET_SOURCE_URL", "MH_DISPATCHER_ASSET_SOURCEURL")
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
        log.info("Current system properties:");
        System.getProperties().forEach( (o, o2) -> {
            if (o instanceof String s && StringUtils.equalsAny(s, "java.class.path", "java.library.path", "line.separator")) {
                return;
            }
            log.info("'\t{}: {}", o, o2);
        });
    }

    private void logGlobals() {
        final Runtime rt = Runtime.getRuntime();
        log.warn("Memory, free: {}, max: {}, total: {}", rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
        log.info("Current globals:");
        log.info("'\tOS: {}", os);
        log.info("'\tmh.home: {}", getHome());
        log.info("'\tcorsAllowedOrigins: {}", corsAllowedOrigins);
        log.info("'\tbranding: {}", branding);
        log.info("'\ttesting: {}", testing);
        log.info("'\tsslRequired: {}", sslRequired);
        log.info("'\tthreadNumber.scheduler: {}", threadNumber.getScheduler());
        log.info("'\tthreadNumber.event: {}", threadNumber.getEvent());
        log.info("'\tdispatcher.enabled: {}", dispatcher.enabled);
        log.info("'\tdispatcher.dir: {}", dispatcherPath.toAbsolutePath().normalize());
        log.info("'\tdispatcher.functionSignatureRequired: {}", dispatcher.functionSignatureRequired);
        log.info("'\tdispatcher.masterUsername: {}", dispatcher.masterUsername);
        log.info("'\tdispatcher.publicKey: {}", dispatcher.publicKey!=null ? "provided" : "wasn't provided");
        log.info("'\tdispatcher.chunkSize: {}", dispatcher.chunkSize);
        log.info("'\tdispatcher.keepEventsInDb: {}", dispatcher.keepEventsInDb);
        log.info("'\tdispatcher.maxTriesAfterError: {}", dispatcher.maxTriesAfterError);

        log.info("'\tdispatcher.timeout.gc: {}", dispatcher.timeout.gc);
        log.info("'\tdispatcher.timeout.artifactCleaner: {}", dispatcher.timeout.artifactCleaner);
        log.info("'\tdispatcher.timeout.updateBatchStatuses: {}", dispatcher.timeout.updateBatchStatuses);

        log.info("'\tdispatcher.asset.mode: {}", dispatcher.asset.mode);
        log.info("'\tdispatcher.asset.username: {}", dispatcher.asset.username);
        log.info("'\tdispatcher.asset.sourceUrl: {}", dispatcher.asset.sourceUrl);
        log.info("'\tdispatcher.asset.syncTimeout: {}", dispatcher.asset.getSyncTimeout());

        log.info("'\tdispatcher.rowsLimit.globalVariableTable: {}", dispatcher.rowsLimit.globalVariableTable);
        log.info("'\tdispatcher.rowsLimit.experiment: {}", dispatcher.rowsLimit.experiment);
        log.info("'\tdispatcher.rowsLimit.sourceCode: {}", dispatcher.rowsLimit.sourceCode);
        log.info("'\tdispatcher.rowsLimit.execContext: {}", dispatcher.rowsLimit.execContext);
        log.info("'\tdispatcher.rowsLimit.processor: {}", dispatcher.rowsLimit.processor);
        log.info("'\tdispatcher.rowsLimit.account: {}", dispatcher.rowsLimit.account);
        log.info("'\tdispatcher.rowsLimit.processor: {}", dispatcher.rowsLimit.processor);

        log.info("'\tprocessor.enabled: {}", processor.enabled);
        log.info("'\tprocessor.dir: {}", processorPath.toAbsolutePath().normalize());
        log.info("'\tprocessor.taskConsoleOutputMaxLines: {}", processor.taskConsoleOutputMaxLines);
        log.info("'\tprocessor.timeout.artifactCleaner: {}", processor.timeout.artifactCleaner);
        log.info("'\tprocessor.timeout.downloadFunction: {}", processor.timeout.downloadFunction);
        log.info("'\tprocessor.timeout.downloadResource: {}", processor.timeout.downloadResource);
        log.info("'\tprocessor.timeout.prepareFunctionForDownloading: {}", processor.timeout.prepareFunctionForDownloading);
        log.info("'\tprocessor.timeout.requestDispatcher: {}", processor.timeout.requestDispatcher);
        log.info("'\tprocessor.timeout.taskAssigner: {}", processor.timeout.taskAssigner);
        log.info("'\tprocessor.timeout.taskProcessor: {}", processor.timeout.taskProcessor);
        log.info("'\tprocessor.timeout.dispatcherContextInfo: {}", processor.timeout.dispatcherContextInfo);
    }
}
