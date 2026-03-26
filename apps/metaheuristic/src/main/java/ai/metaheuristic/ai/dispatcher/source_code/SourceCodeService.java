/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.utils.ArtifactUtils;
import ai.metaheuristic.ai.utils.EnvServiceUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import ai.metaheuristic.commons.utils.ErrorUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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

    public SourceCodeApiData.SourceCodeResult createSourceCode(String sourceCodeAsStr, EnumsApi.SourceCodeLang lang, Long companyUniqueId) {
        try {
            if (globals.dispatcher.asset.mode== EnumsApi.DispatcherAssetMode.replicated) {
                return new SourceCodeApiData.SourceCodeResult("560.030 Can't add a new sourceCode while 'replicated' mode of asset is active");
            }
            if (StringUtils.isBlank(sourceCodeAsStr)) {
                return new SourceCodeApiData.SourceCodeResult("560.060 sourceCode yaml is empty");
            }

            SourceCodeGraph sourceCodeGraph;
            try {
                sourceCodeGraph = SourceCodeGraphFactory.parse(lang, sourceCodeAsStr);
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

            final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(sourceCodeGraph.uid);
            if (sourceCodeResult != null) {
                return sourceCodeResult;
            }

            try {
                return sourceCodeTxService.createSourceCode(sourceCodeAsStr, sourceCodeGraph.uid, lang, companyUniqueId, sourceCodeGraph.type);
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

    public SourceCodeApiData.SourceCodeResult getSourceCode(Long sourceCodeId, UserContext context) {
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

    private SourceCodeApiData.@Nullable SourceCodeResult checkSourceCodeExist(final String uid) {
        if (StringUtils.isBlank(uid)) {
            return new SourceCodeApiData.SourceCodeResult("560.270 the uid of sourceCode is empty");
        }
        SourceCode f = sourceCodeRepository.findByUid(uid);
        if (f!=null) {
            final SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult();
            result.addInfoMessage("560.300 the sourceCode with uid " + uid + " already exists");
            return result;
        }
        return null;
    }

    public OperationStatusRest uploadSourceCode(MultipartFile file, UserContext context) {
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
        EnumsApi.SourceCodeLang lang = EnumsApi.SourceCodeLang.getLangFromExt(ext);
        if (lang==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                "560.420 SourceCode with type "+ext+" isn't supported, filename: " + originFilename);
        }

        try {
            String sourceCodeAsStr;
            try (InputStream is = file.getInputStream()) {
                sourceCodeAsStr = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
            SourceCodeGraph sourceCodeGraph;
            try {
                sourceCodeGraph = SourceCodeGraphFactory.parse(lang, sourceCodeAsStr);
            }
            catch (WrongVersionOfParamsException e) {
                String es = "560.450 An error parsing yaml: " + e.getMessage();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(sourceCodeGraph.uid);
            if (sourceCodeResult != null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, sourceCodeResult.getErrorMessagesAsList(), sourceCodeResult.getInfoMessagesAsList());
            }

            SourceCodeApiData.SourceCodeResult result = sourceCodeTxService.createSourceCode(
                sourceCodeAsStr, sourceCodeGraph.uid, lang, context.getCompanyId(), sourceCodeGraph.type);

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
