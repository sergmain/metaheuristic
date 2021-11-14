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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYaml;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.*;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionTopLevelService {

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/function#configuration.\n";
    public static final YamlSchemeValidator<String> FUNCTION_CONFIG_LIST_YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
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
            1, SEE_MORE_INFO),
                    new Scheme(
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
                                    new Element("content", false, false),
                                    new Element("checksumMap", false, false))
                    ) ),
            2, SEE_MORE_INFO)),
            "the config file functions.yaml",
            (es)-> es, SEE_MORE_INFO
    );

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionService functionService;

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
        FunctionData.FunctionsResult result = new FunctionData.FunctionsResult();

        List<Long> ids = functionRepository.findAllIds();
        result.functions = new ArrayList<>();

        ids.forEach(id -> result.functions.add(functionCache.findById(id)));

        result.functions.sort((o1, o2)->o2.getId().compareTo(o1.getId()));
        result.assetMode = globals.dispatcher.asset.mode;
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
        functionService.deleteFunction(function.getId(), function.getCode());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadFunction(@Nullable final MultipartFile file) {
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.020 Can't upload function while 'replicated' mode of asset is active");
        }
        if (file==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.025 File with function wasn't selected");
        }
        String originFilename = file.getOriginalFilename();
        if (S.b(originFilename)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.030 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (S.b(ext)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.040 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.050 only '.zip', '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir = null;
        try {
            tempDir = DirUtils.createMhTempDir("function-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#424.060 can't create temporary directory in " + location);
            }
            final File zipFile = new File(tempDir, "functions" + ext);
            log.debug("Start storing an uploaded function to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
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
                    "#424.070 can't load functions, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static List<String> toErrorMessages(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private static boolean isError(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

    private void loadFunctionsRecursively(List<FunctionApiData.FunctionConfigStatus> statuses, File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);

        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("#295.060 Load functions from {}", dir.getPath());
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
    private List<FunctionApiData.FunctionConfigStatus> loadFunctionsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "functions.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.080 File 'functions.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return Collections.emptyList();
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        String errorString = FUNCTION_CONFIG_LIST_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        if (errorString!=null) {
            return List.of(new FunctionApiData.FunctionConfigStatus(false, errorString));
        }

        FunctionConfigListYaml functionConfigList = FunctionConfigListYamlUtils.BASE_YAML_UTILS.to(cfg);
        List<FunctionApiData.FunctionConfigStatus> statuses = new ArrayList<>();
        for (FunctionConfigListYaml.FunctionConfig functionConfig : functionConfigList.functions) {
            try {
                FunctionApiData.FunctionConfigStatus status = FunctionCoreUtils.validate(functionConfig);
                if (!status.isOk) {
                    statuses.add(status);
                    log.error(status.error);
                    continue;
                }
                String sum=null;
                File file = null;
                if (globals.dispatcher.functionSignatureRequired) {
                    // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing right noww
                    final EnumsApi.HashAlgo hashAlgo = EnumsApi.HashAlgo.SHA256WithSignature;

                    if (functionConfig.checksumMap==null || functionConfig.checksumMap.keySet().stream().noneMatch(o->o== hashAlgo)) {
                        String es = S.f("#295.100 Global isFunctionSignatureRequired==true but function %s isn't signed with HashAlgo.SHA256WithSignature", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.error(es);
                        continue;
                    }
                    String data = functionConfig.checksumMap.entrySet().stream()
                            .filter(o -> o.getKey() == hashAlgo)
                            .findFirst()
                            .map(Map.Entry::getValue).orElse(null);

                    if (S.b(data)) {
                        String es = S.f("#295.120 Global isFunctionSignatureRequired==true but function %s has empty SHA256WithSignature value", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.warn(es);
                        continue;
                    }
                    ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
                    if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
                        String es = S.f("#295.140 Global isFunctionSignatureRequired==true but function %s has empty checksum or signature", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.warn(es);
                        continue;
                    }

                    switch(functionConfig.sourcing) {
                        case dispatcher:
                            if (S.b(functionConfig.file)) {
                                String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
                                sum = Checksum.getChecksum(hashAlgo, new ByteArrayInputStream(s.getBytes()));
                            }
                            else {
                                file = new File(srcDir, functionConfig.file);
                                if (!file.exists()) {
                                    final String es = "#295.160 Function has a sourcing as 'dispatcher' but file " + functionConfig.file + " wasn't found.";
                                    statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                                    log.warn(es+" Temp dir: " + srcDir.getAbsolutePath());
                                    continue;
                                }
                                try (InputStream inputStream = new FileInputStream(file)) {
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
                        continue;
                    }
                    // ###idea### why?
                    EnumsApi.SignatureState st = ChecksumWithSignatureUtils.isValid(
                            hashAlgo.signatureAlgo, sum.getBytes(), checksumWithSignature.signature, globals.dispatcher.publicKey);

                    if (st!= EnumsApi.SignatureState.correct) {
                        if (!checksumWithSignature.checksum.equals(sum)) {
                            String es = S.f("#295.200 Function %s has wrong signature", functionConfig.code);
                            statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                            log.warn(es);
                            continue;
                        }
                    }
                }

                Function function = functionRepository.findByCode(functionConfig.code);
                // there is a function with the same code
                if (function !=null) {
                    statuses.add(new FunctionApiData.FunctionConfigStatus(false,
                            "#295.220 Replacing of existing function isn't supported any more, need to upload a function as a new one. Function code: "+ function.code));
                    //noinspection UnnecessaryContinue
                    continue;
                }
                else {
                    FunctionConfigYaml scy = FunctionCoreUtils.to(functionConfig);
                    if (file != null) {
                        try (InputStream inputStream = new FileInputStream(file)) {
                            functionService.persistFunction(scy, inputStream, file.length());
                        }
                    }
                    else {
                        functionService.persistFunction(scy, null, 0);
                    }
                }
            }
            catch(VariableSavingException e) {
                statuses.add(new FunctionApiData.FunctionConfigStatus(false, e.getMessage()));
            }
            catch(Throwable th) {
                final String es = "#295.240 Error " + th.getClass().getName() + " while processing function '" + functionConfig.code + "': " + th.toString();
                log.error(es, th);
                statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
            }
        }
        return statuses;
    }

    @Nullable
    public TaskParamsYaml.FunctionConfig getFunctionConfig(SimpleFunctionDefinition functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if(StringUtils.isNotBlank(functionDef.getCode())) {
            Function function = findByCode(functionDef.getCode());
            if (function != null) {
                FunctionConfigYaml temp = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(function.params);
                functionConfig = TaskParamsUtils.toFunctionConfig(temp);
                if (!functionConfig.skipParams) {
                    functionConfig.params = produceFinalCommandLineParams(functionConfig.params, functionDef.getParams());
                }
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

    @Nullable
    public synchronized Function findByCode(String functionCode) {
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
        FunctionConfigYaml sc = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(function.params);
        log.info("Send config of function {}", sc.getCode());
        return FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);
    }

    public Map<EnumsApi.HashAlgo, String> getFunctionChecksum(HttpServletResponse response, String functionCode) throws IOException {
        Function function = findByCode(functionCode);
        if (function ==null) {
            log.warn("#442.100 Function {} wasn't found", functionCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return Map.of();
        }
        FunctionConfigYaml sc = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(function.params);
        log.info("#442.120 Send checksum {} for function {}", sc.checksumMap, sc.getCode());
        return sc.checksumMap==null ? Map.of() : sc.checksumMap;
    }

}
