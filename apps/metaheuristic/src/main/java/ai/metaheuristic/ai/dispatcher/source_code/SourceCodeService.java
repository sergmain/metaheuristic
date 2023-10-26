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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.ArtifactUtils;
import ai.metaheuristic.ai.utils.EnvServiceUtils;
import ai.metaheuristic.ai.utils.HttpUtils;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@SuppressWarnings("unused")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeService {

    private final SourceCodeTxService sourceCodeTxService;
    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;

    public SourceCodeApiData.SourceCodeResult createSourceCode(String sourceCodeYamlAsStr, Long companyUniqueId) {
        try {
            if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
                return new SourceCodeApiData.SourceCodeResult("560.030 Can't add a new sourceCode while 'replicated' mode of asset is active");
            }
            if (StringUtils.isBlank(sourceCodeYamlAsStr)) {
                return new SourceCodeApiData.SourceCodeResult("560.060 sourceCode yaml is empty");
            }

            SourceCodeParamsYaml ppy;
            try {
                ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);
            }
            catch (WrongVersionOfParamsException e) {
                String es = "560.090 An error parsing yaml: " + e.getMessage();
                log.error(es);
                return new SourceCodeApiData.SourceCodeResult(es);
            } catch (CheckIntegrityFailedException e) {
                String es = "560.120 An error of checking integrity: " + e.getMessage();
                log.error(es);
                return new SourceCodeApiData.SourceCodeResult(es);
            }

            final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(ppy);
            if (sourceCodeResult != null) {
                return sourceCodeResult;
            }

            try {
                return sourceCodeTxService.createSourceCode(sourceCodeYamlAsStr, ppy, companyUniqueId);
            } catch (DataIntegrityViolationException e) {
                final String error = ErrorUtils.getAllMessages(e, 1);
                final String es = "560.150 data integrity error: " + error;
                log.error(es, e);
                return new SourceCodeApiData.SourceCodeResult(es);
            }
        } catch (Throwable th) {
            final String error = ErrorUtils.getAllMessages(th);
            String es = "560.180 An unknown error: " + error;
            log.error(es, th);
            return new SourceCodeApiData.SourceCodeResult(es);
        }
    }

    public SourceCodeApiData.SourceCodeResult getSourceCode(Long sourceCodeId, DispatcherContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            String errorMessage = "560.210 sourceCode wasn't found, sourceCodeId: " + sourceCodeId;
            return new SourceCodeApiData.SourceCodeResult(
                    errorMessage,
                    new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR, errorMessage));
        }
        SourceCodeStoredParamsYaml storedParams = sourceCode.getSourceCodeStoredParamsYaml();
        return new SourceCodeApiData.SourceCodeResult(sourceCode, storedParams.lang, storedParams.source, globals.dispatcher.asset.mode);
    }

    public SourceCodeData.Development getSourceCodeDevs(Long sourceCodeId, DispatcherContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            String errorMessage = "560.240 sourceCode wasn't found, sourceCodeId: " + sourceCodeId;
            return new SourceCodeData.Development(errorMessage);
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        SourceCodeData.Development d = new SourceCodeData.Development();
        d.sourceCodeUid = scpy.source.uid;
        d.sourceCodeId = sourceCodeId;

        AtomicLong contextId = new AtomicLong();
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                EnumsApi.SourceCodeLang.yaml, scspy.source, () -> Long.toString(contextId.incrementAndGet()));

        for (ExecContextParamsYaml.Process process : sourceCodeGraph.processes) {
            if (process.function.context== EnumsApi.FunctionExecContext.internal) {
                continue;
            }

            SourceCodeData.SimpleProcess sp = new SourceCodeData.SimpleProcess();
            d.processes.add(sp);
            sp.code = process.processCode;
            if (process.preFunctions!=null) {
                process.preFunctions.stream().map(o->o.code).collect(Collectors.toCollection(()->sp.preFunctions));
            }
            sp.function = process.function.code;
            if (process.postFunctions!=null) {
                process.postFunctions.stream().map(o->o.code).collect(Collectors.toCollection(()->sp.postFunctions));
            }
        }
        return d;
    }

    @Nullable
    private SourceCodeApiData.SourceCodeResult checkSourceCodeExist(SourceCodeParamsYaml ppy) {
        final String code = ppy.source.uid;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("560.270 the code of sourceCode is empty");
        }
        SourceCode f = sourceCodeRepository.findByUid(code);
        if (f!=null) {
            final SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult();
            result.addInfoMessage("560.300 the sourceCode with code " + code + " already exists");
            return result;
        }
        return null;
    }

    public OperationStatusRest uploadSourceCode(MultipartFile file, DispatcherContext context) {
        if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "560.330 Can't upload sourceCode while 'replicated' mode of asset is active");
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "560.360 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "560.390 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "560.420 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        try {
            String sourceCodeYamlAsStr;
            try (InputStream is = file.getInputStream()) {
                sourceCodeYamlAsStr = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
            SourceCodeParamsYaml ppy;
            try {
                ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);
            } catch (WrongVersionOfParamsException e) {
                String es = "560.450 An error parsing yaml: " + e.getMessage();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(ppy);
            if (sourceCodeResult != null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, sourceCodeResult.getErrorMessagesAsList(), sourceCodeResult.getInfoMessagesAsList());
            }

            SourceCodeApiData.SourceCodeResult result = sourceCodeTxService.createSourceCode(sourceCodeYamlAsStr, ppy, context.getCompanyId());

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.getErrorMessagesAsList(), result.getInfoMessagesAsList());
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("560.480 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "560.510 can't load source codes, Error: " + e.getMessage());
        }
    }

    public CleanerInfo generateDirsForDev(Long sourceCodeId, String processCode, Long companyId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
            if (sourceCode == null) {
                log.info("560.540 sourceCode wasn't found, sourceCodeId: {}", sourceCodeId);
                return resource;
            }

            SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
            SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

            AtomicLong contextId = new AtomicLong();
            SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                    EnumsApi.SourceCodeLang.yaml, scspy.source, () -> "" + contextId.incrementAndGet());

            ExecContextParamsYaml.Process process = sourceCodeGraph.processes.stream().filter(o->o.processCode.equals(processCode)).findFirst().orElse(null);
            if (process == null) {
                log.warn("560.570 process wasn't found, processCode: {}", processCode);
                return resource;
            }

            Path tempDir = DirUtils.createMhTempPath("generate-dirs-for-dev-");
            if (tempDir==null) {
                throw new RuntimeException("(tempDir==null)");
            }
            resource.toClean.add(tempDir);

            final String processCodeDirName = ArtifactCommonUtils.normalizeCode(process.processCode);

            Path outputDir = tempDir.resolve(processCodeDirName);
            Files.createDirectory(outputDir);

            Map<String, Long> globalIds = new HashMap<>();
            AtomicLong globalId = new AtomicLong(1000);
            createGlobalVariables(process, outputDir, globalIds, globalId);

            Map<String, Long> localIds = new HashMap<>();
            AtomicLong localId = new AtomicLong(2000);
            createLocalVariables(process, outputDir, localIds, localId);

            createSystemDir(outputDir);
            TaskParamsYaml tpy = asTaskParamsYaml(scpy, process, globalIds, localIds);

            if (createArtifactsDir(outputDir, tpy)) {
                log.error("560.600 error creating artifact dir, processCode: {}", processCode);
                return resource;
            }

            String filename = "process-"+processCodeDirName+".zip";
            Path zipFile = tempDir.resolve(filename);

            ZipUtils.createZip(outputDir, zipFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpUtils.setContentDisposition(httpHeaders, filename);
            resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), RestUtils.getHeader(httpHeaders, Files.size(zipFile)), HttpStatus.OK);
            return resource;
        } catch (VariableDataNotFoundException e) {
            log.error("560.630 Variable #{}, context: {}, {}", e.variableId, e.context, e.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        } catch (Throwable th) {
            log.error("560.660 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }

    private static TaskParamsYaml asTaskParamsYaml(SourceCodeParamsYaml scpy, ExecContextParamsYaml.Process process, Map<String, Long> globalIds, Map<String, Long> localIds) {
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = 42L;
        tpy.task.inline = scpy.source.variables==null ? null : scpy.source.variables.inline;
        // tpy.task.workingPath will be inited later
        for (ExecContextParamsYaml.Variable o : process.inputs) {
            Long id;
            if (o.context == EnumsApi.VariableContext.global) {
                id = globalIds.get(o.name);
            }
            else {
                id = localIds.get(o.name);
            }
            if (id==null) {
                throw new IllegalStateException("(id==null)");
            }
            TaskParamsYaml.InputVariable input = new TaskParamsYaml.InputVariable();
            input.id = id;
            input.name = o.name;
            input.context = o.context;
            input.type = o.type;
            input.sourcing = o.sourcing;
            input.empty = false;
            input.setNullable(false);
            input.disk = o.disk;
            input.git = o.git;
            input.filename = "file-name-for-this-variable.txt";
            tpy.task.inputs.add(input);
        }
        AtomicLong outputId = new AtomicLong(3000);
        for (ExecContextParamsYaml.Variable o : process.outputs) {
            TaskParamsYaml.OutputVariable output = new TaskParamsYaml.OutputVariable();
            output.id = outputId.getAndIncrement();
            output.name = o.name;
            output.sourcing = o.sourcing;
            output.context = o.context;
            output.type = o.type;
            output.ext = o.ext;
            output.disk = o.disk;
            output.git = o.git;
            output.empty = false;
            output.setNullable(false);
            tpy.task.outputs.add(output);
        }

        return tpy;
    }

    /**
     * @return boolean - true if error
     */
    private static boolean createArtifactsDir(Path outputDir, TaskParamsYaml tpy) throws IOException {
        Path artifactsDir = outputDir.resolve(ConstsApi.ARTIFACTS_DIR);
        Files.createDirectory(artifactsDir);
        if (createEnvParamsFile(artifactsDir)) {
            return true;
        }

        return !ArtifactUtils.prepareParamsFileForTask(artifactsDir, outputDir.getFileName().toString(), tpy,
                Set.of(TaskFileParamsYamlUtils.DEFAULT_UTILS.getVersion()));
    }

    private static boolean createEnvParamsFile(Path artefactsDir) {
        EnvParamsYaml epy = new EnvParamsYaml();

        epy.envs.addAll(List.of(
                new EnvParamsYaml.Env("python-3", "/path-to-python/python", null),
                new EnvParamsYaml.Env("java-11", "/path-to-java/java -Dfile.encoding=UTF-8 -jar", null)));
        epy.disk.addAll(List.of(new EnvParamsYaml.DiskStorage("path-1", "/full/path/1"), new EnvParamsYaml.DiskStorage("path-2", "/full/path/2")));

        String status = EnvServiceUtils.prepareEnvironment(artefactsDir, new EnvServiceUtils.EnvYamlShort(epy));
        if (status!=null) {
            log.error(status);
            return true;
        }
        return false;
    }

    private static void createSystemDir(Path outputDir) throws IOException {
        Path systemDir = outputDir.resolve(Consts.SYSTEM_DIR);
        Files.createDirectory(systemDir);
        Files.writeString(systemDir.resolve(Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME), "<a stub value for log file "+Consts.MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME+">");
    }

    private static void createLocalVariables(ExecContextParamsYaml.Process process, Path outputDir, Map<String, Long> localIds, AtomicLong localId) throws IOException {
        boolean isLocalVars = process.inputs.stream().anyMatch(o->o.context!=EnumsApi.VariableContext.global);
        if (isLocalVars) {
            Path localVarDir = outputDir.resolve(EnumsApi.DataType.variable.toString());
            Files.createDirectory(localVarDir);
            for (ExecContextParamsYaml.Variable o : process.inputs) {
                if (o.context == EnumsApi.VariableContext.local) {
                    Long id = localIds.computeIfAbsent(o.name, k -> localId.getAndIncrement());
                    Files.writeString(localVarDir.resolve(id.toString()), "<a stub value for variable "+o.name+">");
                }
                else if (o.context == EnumsApi.VariableContext.array) {
                    final String nameForVariableInArray = VariableUtils.getNameForVariableInArray();

                    Long id = localIds.computeIfAbsent(nameForVariableInArray, k -> localId.getAndIncrement());
                    Files.writeString(localVarDir.resolve(id.toString()), "<a stub value for variable "+o.name+">");

                    VariableArrayParamsYaml vapy = new VariableArrayParamsYaml();
                    vapy.array.add(new VariableArrayParamsYaml.Variable(id.toString(), nameForVariableInArray, EnumsApi.DataSourcing.dispatcher, EnumsApi.DataType.variable));
                    String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);

                    id = localIds.computeIfAbsent(o.name, k -> localId.getAndIncrement());
                    Files.writeString(localVarDir.resolve(id.toString()), yaml);
                }
                else {
                    throw new IllegalStateException("Wrong variable context" + o.context);
                }
            }
        }
    }

    private static void createGlobalVariables(ExecContextParamsYaml.Process process, Path outputDir, Map<String, Long> globalIds, AtomicLong globalsId) throws IOException {
        boolean isGlobalVars = process.inputs.stream().anyMatch(o->o.context== EnumsApi.VariableContext.global);
        if (isGlobalVars) {
            Path globalVarDir = outputDir.resolve(EnumsApi.DataType.global_variable.toString());
            Files.createDirectory(globalVarDir);
            for (ExecContextParamsYaml.Variable o : process.inputs) {
                if (o.context == EnumsApi.VariableContext.global) {
                    Long id = globalIds.computeIfAbsent(o.name, k -> globalsId.getAndIncrement());
                    Files.writeString(globalVarDir.resolve(id.toString()), "<a stub value for global variable "+o.name+">");
                }
            }
        }
    }
}
