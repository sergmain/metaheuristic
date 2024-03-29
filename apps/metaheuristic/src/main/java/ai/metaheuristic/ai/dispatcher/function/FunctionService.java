/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ArtifactCommonUtils;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Element;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Scheme;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionService {

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

    @Nullable
    public FunctionData.SimpleFunctionResult getFunction(String code) {
        Function f = functionRepository.findByCode(code);
        if (f==null) {
            return null;
        }
        return new FunctionData.SimpleFunctionResult(f.id, f.code, f.type, f.getParams());
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
                    "295.060 Can't delete function while 'replicated' mode of asset is active");
        }
        final Function function = functionCache.findById(id);
        if (function == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "295.080 function wasn't found, functionId: " + id);
        }
        functionTxService.deleteFunction(function.getId(), function.getCode());
        try {
            deleteResourceDirForFunction(function.getCode());
        } catch (IOException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                "295.120 Can't delete resource dir for function "+function.getCode());
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void loadFunctionInternal(Path srcDir, BundleData.UploadingStatus status, FunctionConfigYaml functionConfigYaml) throws IOException {
        FunctionConfigYaml.FunctionConfig functionConfig = functionConfigYaml.function;
        if (functionConfigYaml.system == null) {
            final String es = S.f("295.220 Config yaml for function %s is broken, field system or system.archive is empty ", functionConfig.code);
            status.addErrorMessage(es);
            return;
        }

        deleteResourceDirForFunction(functionConfig.code);

        Function function = functionRepository.findByCode(functionConfig.code);
        // the function was already uploaded
        if (function !=null) {
            FunctionConfigYaml cfg = function.getFunctionConfigYaml();
            final String checksumInfo = getChecksumInfo(functionConfigYaml, cfg);
            final String es = S.f("295.240 Function %s was already uploaded. Checksum %s", function.code, checksumInfo);
            status.addInfoMessage(es);
            return;
        }

        FunctionApiData.FunctionConfigStatus validated = FunctionCoreUtils.validate(functionConfig);
        if (!validated.isOk) {
            status.addErrorMessage(validated.error);
            log.error(validated.error);
            return;
        }

        if (functionConfig.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            if (S.b(functionConfigYaml.system.archive)) {
                final String es = S.f("295.260 Config yaml for function %s is broken, field system or system.archive is empty ", functionConfig.code);
                status.addErrorMessage(es);
                return;
            }
            Path file = srcDir.resolve(functionConfigYaml.system.archive);
            if (Files.notExists(file)) {
                final String es = "295.300 Function has broken functionConfigYaml.system.archive, file not found " + file;
                status.addErrorMessage(es);
                log.warn(es + " Temp dir: " + srcDir.normalize());
                return;
            }

            if (checksumFailed(status, functionConfigYaml, functionConfig, file)) {
                return;
            }
            try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                functionTxService.persistFunction(functionConfigYaml, bis, Files.size(file));
            }
        }
        else if (functionConfig.sourcing== EnumsApi.FunctionSourcing.git) {
            if (!trusted(functionConfig.sourcing, functionConfig.git)) {
                final String es = "295.310 Function " + functionConfig.code +" can't be uploaded from git repo " + (functionConfig.git!=null ? functionConfig.git.repo : "<unknown>") +" because this repo isn't trusted";
                status.addErrorMessage(es);
                log.warn(es);
                return;
            }
            functionTxService.persistFunction(functionConfigYaml, null, 0);
        }
        else {
            throw new IllegalStateException("Not supported");
        }
    }

    public boolean trusted(EnumsApi.FunctionSourcing sourcing, @Nullable GitInfo git) {
        if (globals.function.securityCheck==Enums.FunctionSecurityCheck.none) {
            return true;
        }
        if (sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            return globals.function.trusted.dispatcher;
        }
        if (sourcing==EnumsApi.FunctionSourcing.git) {
            if (globals.function.trusted.gitRepo==null || git==null) {
                return false;
            }
            for (String repo : globals.function.trusted.gitRepo) {
                if (git.repo.startsWith(repo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checksumFailed(BundleData.UploadingStatus status, FunctionConfigYaml functionConfigYaml, FunctionConfigYaml.FunctionConfig functionConfig, Path file) throws IOException {
        String sum=null;
        if (globals.function.securityCheck== Enums.FunctionSecurityCheck.always ||
            (globals.function.securityCheck== Enums.FunctionSecurityCheck.skip_trusted && !globals.function.trusted.dispatcher)) {

            // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing right now
            final EnumsApi.HashAlgo hashAlgo = EnumsApi.HashAlgo.SHA256WithSignature;

            if (functionConfigYaml.system.checksumMap.keySet().stream().noneMatch(o->o==hashAlgo)) {
                String es = S.f("295.320 Global isFunctionSignatureRequired==true but function %s isn't signed with HashAlgo.SHA256WithSignature", functionConfig.code);
                status.addErrorMessage(es);
                log.error(es);
                return true;
            }
            String data = functionConfigYaml.system.checksumMap.entrySet().stream()
                    .filter(o -> o.getKey() == hashAlgo)
                    .findFirst()
                    .map(Map.Entry::getValue).orElse(null);

            if (S.b(data)) {
                String es = S.f("295.340 Global isFunctionSignatureRequired==true but function %s has empty SHA256WithSignature value", functionConfig.code);
                status.addErrorMessage(es);
                log.warn(es);
                return true;
            }
            ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
            if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
                String es = S.f("295.360 Global isFunctionSignatureRequired==true but function %s has empty checksum or signature", functionConfig.code);
                status.addErrorMessage(es);
                log.warn(es);
                return true;
            }

            switch(functionConfig.sourcing) {
                case dispatcher:
                    try (InputStream inputStream = Files.newInputStream(file)) {
                        sum = Checksum.getChecksum(hashAlgo, inputStream);
                    }
                    break;
                case git:
                    String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
                    sum = Checksum.getChecksum(hashAlgo, new ByteArrayInputStream(s.getBytes()));
                    break;
            }
            if (!checksumWithSignature.checksum.equals(sum)) {
                String es = S.f("295.380 Function %s has wrong checksum", functionConfig.code);
                status.addErrorMessage(es);
                log.warn(es);
                return true;
            }
            final PublicKey publicKey = globals.getPublicKey(Consts.DEFAULT_PUBLIC_KEY_CODE);

            // ###idea### why?
            //noinspection ConstantConditions
            EnumsApi.SignatureState st = ChecksumWithSignatureUtils.isValid(
                    hashAlgo.signatureAlgo, sum.getBytes(), checksumWithSignature.signature, publicKey);

            if (st!= EnumsApi.SignatureState.correct) {
                if (!checksumWithSignature.checksum.equals(sum)) {
                    String es = S.f("295.400 Function %s has wrong signature", functionConfig.code);
                    status.addErrorMessage(es);
                    log.warn(es);
                    return true;
                }
            }
        }
        return false;
    }

    public void deleteResourceDirForFunction(String functionCode) throws IOException {
        Path baseFunctionDir = ArtifactCommonUtils.prepareFunctionPath(globals.dispatcherResourcesPath);
        String functionCodeAsNormal = ArtifactCommonUtils.normalizeCode(functionCode);
        Path p = baseFunctionDir.resolve(functionCodeAsNormal);
        if (Files.exists(p)) {
            PathUtils.deleteDirectory(p);
        }
    }

    private static String getChecksumInfo(FunctionConfigYaml functionConfigYaml, FunctionConfigYaml cfg) {
        String checksumInfo = " not present.";
        if (cfg.system!=null && cfg.system.checksumMap!=null) {
            if (functionConfigYaml.system.checksumMap==null || functionConfigYaml.system.checksumMap.isEmpty()) {
                //
            }
            else {
                for (Map.Entry<EnumsApi.HashAlgo, String> en : cfg.system.checksumMap.entrySet()) {
                    ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature2 = ChecksumWithSignatureUtils.parse(en.getValue());
                    for (Map.Entry<EnumsApi.HashAlgo, String> o : functionConfigYaml.system.checksumMap.entrySet()) {
                        if (o.getKey() == en.getKey()) {
                            ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature1 = ChecksumWithSignatureUtils.parse(o.getValue());
                            if (Objects.equals(checksumWithSignature1.checksum, checksumWithSignature2.checksum)) {
                                return " is the same.";
                            }
                        }
                    }
                }
                return " is different.";
            }
        }
        return checksumInfo;
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
                functionConfig.params = produceFinalCommandLineParams(functionConfig.params, functionDef.getParams());
            } else {
                log.warn("295.440 Can't find function for code {}", functionDef.getCode());
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
            log.warn("295.460 Function {} wasn't found", functionCode);
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
            log.warn("295.480 Function {} wasn't found", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return Map.of();
        }
        FunctionConfigYaml sc = function.getFunctionConfigYaml();
        log.info("295.500 Send checksum {} for function {}", sc.system!=null ? sc.system.checksumMap : null, sc.function.getCode());
        return sc.system == null ? Map.of() : sc.system.checksumMap;
    }

}
