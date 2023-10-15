/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.*;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Element;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Scheme;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionTopLevelService {

    private static final String SEE_MORE_INFO = "See https://github.com/sergmain/metaheuristic/wiki/function#configuration\n";
    public static final YamlSchemeValidator<String> FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
            List.of( new Scheme(
                    List.of( new Element(
                            "functions",
                            true, false,
                            List.of(
                                    new Element("code"),
                                    new Element("env", false, false),
                                    new Element("file", false, false),
                                    new Element("git", false, false),
                                    new Element("params", false, false),
                                    new Element("metas", false, false),
                                    new Element("skipParams", false, false),
                                    new Element("sourcing"),
                                    new Element("type", false, false),
                                    new Element("checksumMap", false, false))
                    ) ),
            1, SEE_MORE_INFO, false),
                    new Scheme(
                    List.of( new Element(
                            "function",
                            true, false,
                            List.of(
                                    new Element("code"),
                                    new Element("env", false, false),
                                    new Element("exec", false, false),
                                    new Element("git", false, false),
                                    new Element("metas", false, false),
                                    new Element("sourcing"),
                                    new Element("type", false, false)
                            )
                    ), new Element(
                        "system",
                        true, false,
                        List.of(
                            new Element("archive"),
                            new Element("checksumMap", false, false))
                    )),
            2, SEE_MORE_INFO, true)
            ),
            "the config file function.yaml",
            (es)-> es, SEE_MORE_INFO
    );

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionTxService functionTxService;
    private final ApplicationEventPublisher eventPublisher;

    private static final long FUNCTION_INFOS_TIMEOUT_REFRESH = TimeUnit.SECONDS.toMillis(30);
    private List<Pair<EnumsApi.FunctionSourcing, String>> functionInfosCache = null;
    private long mills = 0L;

    public static class RefreshInfoAboutFunctionsEvent {}

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    public Map<EnumsApi.FunctionSourcing, String> toMapOfFunctionInfos() {
        Map<EnumsApi.FunctionSourcing, List<String>> map = new HashMap<>();
        for (Pair<EnumsApi.FunctionSourcing, String> pair : getFunctionInfos()) {
            map.computeIfAbsent(pair.getKey(), (k)->new ArrayList<>()).add(pair.getValue());
        }
        Map<EnumsApi.FunctionSourcing, String> result = new HashMap<>();
        for (Map.Entry<EnumsApi.FunctionSourcing, List<String>> e : map.entrySet()) {
            result.put(e.getKey(), String.join(",", e.getValue()));
        }
        return result;
    }

    public List<Pair<EnumsApi.FunctionSourcing, String>> getFunctionInfos() {
        final ReentrantReadWriteLock.ReadLock readLock = rwl.readLock();
        final ReentrantReadWriteLock.WriteLock writeLock = rwl.writeLock();

        readLock.lock();
        try {
            if (functionInfosCache != null) {
                if (System.currentTimeMillis() - mills > FUNCTION_INFOS_TIMEOUT_REFRESH) {
                    eventPublisher.publishEvent(new RefreshInfoAboutFunctionsEvent());
                }
                return functionInfosCache;
            }
        }
        finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            if (functionInfosCache == null) {
                functionInfosCache = collectInfoAboutFunction();
                mills = System.currentTimeMillis();
            }
        }
        finally {
            writeLock.unlock();
        }

        if (System.currentTimeMillis() - mills > FUNCTION_INFOS_TIMEOUT_REFRESH) {
            eventPublisher.publishEvent(new RefreshInfoAboutFunctionsEvent());
        }

        return functionInfosCache;
    }

    private final LinkedHashMap<Long, Pair<EnumsApi.FunctionSourcing, String>> sourcingInfoCache = new LinkedHashMap<>(1000) {
        protected boolean removeEldestEntry(Map.Entry<Long, Pair<EnumsApi.FunctionSourcing, String>> entry) {
            return this.size()>700;
        }
    };

    public List<Pair<EnumsApi.FunctionSourcing, String>> collectInfoAboutFunction() {
        final List<Long> allIds = functionRepository.findAllIds();

        List<Pair<EnumsApi.FunctionSourcing, String>> result = new ArrayList<>(allIds.size());
        for (Long id : allIds) {
            Pair<EnumsApi.FunctionSourcing, String> pair = sourcingInfoCache.get(id);
            if (pair==null) {
                final Function f = functionRepository.findByIdNullable(id);
                if (f!=null) {
                    FunctionConfigYaml fcy = f.getFunctionConfigYaml();
                    pair = Pair.of(fcy.function.sourcing, f.code);
                    sourcingInfoCache.put(id, pair);
                }
            }
            if (pair!=null) {
                result.add(pair);
            }
        }
        if (allIds.size()!=result.size()) {
            throw new IllegalStateException("(allIds.size()!=result.size())");
        }
        return result;
    }

    @SuppressWarnings("unused")
    @Async
    @EventListener
    public void handleRefreshInfoAboutFunctionsEvent(RefreshInfoAboutFunctionsEvent event) {
        rwl.writeLock().lock();
        try {
            if (System.currentTimeMillis() - mills > FUNCTION_INFOS_TIMEOUT_REFRESH) {
                functionInfosCache = collectInfoAboutFunction();
                mills = System.currentTimeMillis();
            }
        }
        finally {
            rwl.writeLock().unlock();
        }
    }

    public static String produceFinalCommandLineParams(@Nullable String functionConfigParams, @Nullable String functionDefParams) {
        String s;
        if (!S.b(functionConfigParams) && !S.b(functionDefParams)) {
            s = functionConfigParams + ' ' + functionDefParams;
        }
        else if (S.b(functionConfigParams) && !S.b(functionDefParams)) {
            s = functionDefParams;
        }
        else {
            s = functionConfigParams;
        }
        return S.b(s) ? "" : s;
    }

    public FunctionData.FunctionsResult getFunctions() {
        return getFunctions(functionRepository::findAllIds, functionRepository::findByIdNullable, globals.dispatcher.asset.mode);
    }

    public static FunctionData.FunctionsResult getFunctions(Supplier<List<Long>> getAllIdsFunc, java.util.function.Function<Long, Function> findByIdNullableFunc, EnumsApi.DispatcherAssetMode mode) {
        FunctionData.FunctionsResult result = new FunctionData.FunctionsResult();
        List<Long> ids = getAllIdsFunc.get();
        result.functions = ids.stream().map(findByIdNullableFunc).filter(Objects::nonNull).sorted((o1, o2)->o2.getId().compareTo(o1.getId())).collect(Collectors.toList());
        result.assetMode = mode;
        return result;
    }

    public OperationStatusRest deleteFunctionById(Long id) {
        return deleteFunctionById(id, true);
    }

    public OperationStatusRest deleteFunctionById(Long id, boolean checkReplicationMode) {
        log.info("Start deleting function with id: {}", id );
        if (checkReplicationMode  && globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.005 Can't delete function while 'replicated' mode of asset is active");
        }
        final Function function = functionCache.findById(id);
        if (function == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.010 function wasn't found, functionId: " + id);
        }
        functionTxService.deleteFunction(function.getId(), function.getCode());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadFunction(@Nullable final MultipartFile file) {
        log.debug("Start uploading a function");
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.020 Can't upload function while 'replicated' mode of asset is active");
        }
        if (file==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#424.025 File with function wasn't selected");
        }
        String originFilename = file.getOriginalFilename();
        if (S.b(originFilename)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#424.030 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (S.b(ext)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#424.040 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.050 only '.zip', '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        Path tempDir = null;
        try {
            tempDir = DirUtils.createMhTempPath("function-upload-");
            if (tempDir==null || !Files.isDirectory(tempDir)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#424.060 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
            }
            final Path zipFile = tempDir.resolve( "functions" + ext);
            log.debug("Start storing an uploaded function to disk");
            long size;
            try (InputStream is = file.getInputStream(); OutputStream os = Files.newOutputStream(zipFile)) {
                size = IOUtils.copy(is, os, 64000);
                os.flush();
            }
            log.debug("Uploaded bytes: {}, stored: {}", file.getSize(), size);

            List<FunctionApiData.FunctionConfigStatus> statuses;
            if (ZIP_EXT.equals(ext)) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(zipFile, tempDir);
                log.debug("Start loading function data to db");
                statuses = new ArrayList<>();
                loadFunctionsRecursively(statuses, tempDir);
            }
            else {
                log.debug("Start loading function data to db");
                statuses = loadFunctionsFromDir(tempDir);
            }
            if (isError(statuses)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        toErrorMessages(statuses));
            }
        }
        catch (Exception e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.070 can't load functions, Error: " + e.getMessage());
        }
        finally {
            DirUtils.deletePathAsync(tempDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static List<String> toErrorMessages(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private static boolean isError(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

    public void loadFunctionsRecursively(List<FunctionApiData.FunctionConfigStatus> statuses, Path startDir) throws IOException {
        try (final Stream<Path> list = Files.list(startDir)) {
            final List<Path> dirs = list.filter(Files::isDirectory).collect(Collectors.toList());

            for (Path dir : dirs) {
                log.info("#295.060 Load functions from {}", dir);
                statuses.addAll(loadFunctionsFromDir(dir));
                loadFunctionsRecursively(statuses, dir);
            }
        }
    }

    /**
     * load functions from directory
     *
     * @param srcDir File
     */
    @SuppressWarnings("Duplicates")
    private List<FunctionApiData.FunctionConfigStatus> loadFunctionsFromDir(Path srcDir) throws IOException {
        Path yamlConfigFile = srcDir.resolve("functions.yaml");
        if (Files.notExists(yamlConfigFile)) {
            log.error("#295.080 File 'functions.yaml' wasn't found in dir {}", srcDir.normalize());
            return Collections.emptyList();
        }

        String cfg = Files.readString(yamlConfigFile);
        String errorString = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        if (errorString!=null) {
            return List.of(new FunctionApiData.FunctionConfigStatus(false, errorString));
        }

        FunctionConfigYaml functionConfigList = FunctionConfigYamlUtils.UTILS.to(cfg);
        List<FunctionApiData.FunctionConfigStatus> statuses = new ArrayList<>();
        FunctionConfigYaml.FunctionConfig functionConfig = functionConfigList.function;
        {
            try {
                FunctionApiData.FunctionConfigStatus status = FunctionCoreUtils.validate(functionConfig);
                if (!status.isOk) {
                    statuses.add(status);
                    log.error(status.error);
                    return statuses;
                }
                String sum=null;
                Path file = S.b(functionConfig.file) ? null : srcDir.resolve(functionConfig.file);
                if (globals.dispatcher.functionSignatureRequired) {
                    // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing right noww
                    final EnumsApi.HashAlgo hashAlgo = EnumsApi.HashAlgo.SHA256WithSignature;

                    if (functionConfigList.system==null || functionConfigList.system.checksumMap.keySet().stream().noneMatch(o->o==hashAlgo)) {
                        String es = S.f("#295.100 Global isFunctionSignatureRequired==true but function %s isn't signed with HashAlgo.SHA256WithSignature", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.error(es);
                        return statuses;
                    }
                    String data = functionConfigList.system.checksumMap.entrySet().stream()
                            .filter(o -> o.getKey() == hashAlgo)
                            .findFirst()
                            .map(Map.Entry::getValue).orElse(null);

                    if (S.b(data)) {
                        String es = S.f("#295.120 Global isFunctionSignatureRequired==true but function %s has empty SHA256WithSignature value", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.warn(es);
                        return statuses;
                    }
                    ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
                    if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
                        String es = S.f("#295.140 Global isFunctionSignatureRequired==true but function %s has empty checksum or signature", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.warn(es);
                        return statuses;
                    }

                    switch(functionConfig.sourcing) {
                        case dispatcher:
                            if (S.b(functionConfig.file)) {
                                String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
                                sum = Checksum.getChecksum(hashAlgo, new ByteArrayInputStream(s.getBytes()));
                            }
                            else {
                                file = srcDir.resolve(functionConfig.file);
                                if (Files.notExists(file)) {
                                    final String es = "#295.160 Function has a sourcing as 'dispatcher' but file " + functionConfig.file + " wasn't found.";
                                    statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                                    log.warn(es+" Temp dir: " + srcDir.normalize());
                                    return statuses;
                                }
                                try (InputStream inputStream = Files.newInputStream(file)) {
                                    sum = Checksum.getChecksum(hashAlgo, inputStream);
                                }
                            }
                            break;
                        case processor:
                        case git:
                            String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
                            sum = Checksum.getChecksum(hashAlgo, new ByteArrayInputStream(s.getBytes()));
                            break;
                    }
                    if (!checksumWithSignature.checksum.equals(sum)) {
                        String es = S.f("#295.180 Function %s has wrong checksum", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.warn(es);
                        return statuses;
                    }
                    // ###idea### why?
                    //noinspection ConstantConditions
                    EnumsApi.SignatureState st = ChecksumWithSignatureUtils.isValid(
                            hashAlgo.signatureAlgo, sum.getBytes(), checksumWithSignature.signature, globals.dispatcher.publicKey);

                    if (st!= EnumsApi.SignatureState.correct) {
                        if (!checksumWithSignature.checksum.equals(sum)) {
                            String es = S.f("#295.200 Function %s has wrong signature", functionConfig.code);
                            statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                            log.warn(es);
                            return statuses;
                        }
                    }
                }

                Function function = functionRepository.findByCode(functionConfig.code);
                // there is a function with the same code
                if (function !=null) {
                    statuses.add(new FunctionApiData.FunctionConfigStatus(false,
                            "#295.220 Replacing of existing function isn't supported any more, need to upload a function as a new one. Function code: "+ function.code));
                    return statuses;
                }
                else {
                    FunctionConfigYaml scy = functionConfigList;
                    if (file != null) {
                        try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                            functionTxService.persistFunction(scy, bis, Files.size(file));
                        }
                    }
                    else {
                        functionTxService.persistFunction(scy, null, 0);
                    }
                }
            }
            catch(VariableSavingException e) {
                statuses.add(new FunctionApiData.FunctionConfigStatus(false, e.getMessage()));
            }
            catch(Throwable th) {
                final String es = "#295.240 Error " + th.getClass().getName() + " while processing function '" + functionConfig.code + "': " + th.getMessage();
                log.error(es, th);
                statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
            }
        }
        return statuses;
    }

    @Nullable
    public TaskParamsYaml.FunctionConfig getFunctionConfig(SimpleFunctionDefinition functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if (StringUtils.isNotBlank(functionDef.getCode())) {
            Function function = null;
            if (functionDef.getRefType()== EnumsApi.FunctionRefType.code) {
                function = findByCode(functionDef.getCode());
            }
            else if (functionDef.getRefType()== EnumsApi.FunctionRefType.type) {
                Long funcId = functionRepository.findIdByType(functionDef.getCode());
                if (funcId!=null) {
                    function = functionRepository.findById(funcId).orElse(null);
                }
            }
            else {
                throw new IllegalStateException("unknown refType: " + functionDef.getRefType());
            }
            if (function != null) {
                FunctionConfigYaml temp = function.getFunctionConfigYaml();
                functionConfig = TaskParamsUtils.toFunctionConfig(temp);
            } else {
                log.warn("#295.040 Can't find function for code {}", functionDef.getCode());
            }
        }
        return functionConfig;
    }

    @Data
    @AllArgsConstructor
    public static class FunctionSimpleCache {
        @Nullable
        public Long id;
        public long mills;
    }

    private final Map<String, FunctionSimpleCache> mappingCodeToId = new HashMap<>();
    private static final long FUNCTION_SIMPLE_CACHE_TTL = TimeUnit.MINUTES.toMillis(15);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Nullable
    public Function findByCode(String functionCode) {
        writeLock.lock();
        try {
            FunctionSimpleCache cache = mappingCodeToId.get(functionCode);
            if (cache!=null) {
                if (System.currentTimeMillis() - cache.mills > FUNCTION_SIMPLE_CACHE_TTL) {
                    cache.id = functionRepository.findIdByCode(functionCode);
                    cache.mills = System.currentTimeMillis();
                }
            }
            else {
                cache = new FunctionSimpleCache(functionRepository.findIdByCode(functionCode), System.currentTimeMillis());
                mappingCodeToId.put(functionCode, cache);
            }
            if (mappingCodeToId.size()>1000) {
                mappingCodeToId.clear();
            }

            if (cache.id==null) {
                return null;
            }
            Function function = functionCache.findById(cache.id);
            if (function == null) {
                cache.id = null;
                cache.mills = System.currentTimeMillis();
            }
            return function;
        } finally {
            writeLock.unlock();
        }
    }

    public ReplicationApiData.FunctionConfigsReplication getFunctionConfigs() {
        List<Long> ids = functionRepository.findAllIds();

        ReplicationApiData.FunctionConfigsReplication configs = new ReplicationApiData.FunctionConfigsReplication();
        ids.stream().map(functionCache::findById)
                .filter(Objects::nonNull).map(FunctionUtils::to)
                .collect(Collectors.toCollection(() -> configs.configs));

        log.info("Send all configs of functions");
        return configs;
    }

    public String getFunctionConfig(HttpServletResponse response, String functionCode) throws IOException {
        Function function = findByCode(functionCode);
        if (function ==null) {
            log.warn("#442.140 Function {} wasn't found", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return "";
        }
        FunctionConfigYaml sc = function.getFunctionConfigYaml();
        log.info("Send config of function {}", sc.function.getCode());
        return FunctionConfigYamlUtils.UTILS.toString(sc);
    }

    public Map<EnumsApi.HashAlgo, String> getFunctionChecksum(HttpServletResponse response, String functionCode) throws IOException {
        Function function = findByCode(functionCode);
        if (function ==null) {
            log.warn("#442.100 Function {} wasn't found", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return Map.of();
        }
        FunctionConfigYaml sc = function.getFunctionConfigYaml();
        log.info("#442.120 Send checksum {} for function {}", sc.system!=null ? sc.system.checksumMap : null, sc.function.getCode());
        return sc.system == null ? Map.of() : sc.system.checksumMap;
    }

}
