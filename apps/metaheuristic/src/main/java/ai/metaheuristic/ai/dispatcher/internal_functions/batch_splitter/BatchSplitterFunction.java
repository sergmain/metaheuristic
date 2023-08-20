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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.Consts.ZIP_EXT;
import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:36 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchSplitterFunction implements InternalFunction {

    private final BatchSplitterTxService batchSplitterTxService;
    private final InternalFunctionVariableService internalFunctionVariableService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#995.020 Meta 'variable-for-splitting' wasn't found"));
        }

        List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(simpleExecContext.execContextId, taskContextId, inputVariableName);
        if (holders.size()>1) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.040 Too many variables"));
        }

        VariableUtils.VariableHolder variableHolder = holders.get(0);
        String originFilename = variableHolder.getFilename();
        if (S.b(originFilename)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.input_variable_isnt_file, "#995.060 variable.filename is blank"));
        }

        String ext = StrUtils.getExtension(originFilename);

        Path tempDir=DirUtils.createMhTempPath("batch-file-upload-");
        try {
            if (tempDir==null || !Files.isDirectory(tempDir)) {
                String es = "#995.080 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
            }

            final Path dataFile = Files.createTempFile(tempDir, "uploaded-file-", ext);
            internalFunctionVariableService.storeToFile(variableHolder, dataFile);
            if (Files.size(dataFile)==0) {
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, "#995.100 Empty files aren't supported");
            }

            final Path workingDir;
            Map<String, String> mapping;
            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                Path zipDir=tempDir.resolve("zip");
                Files.createDirectory(zipDir);

                log.debug("Start loading .zip file data to db");
                workingDir = zipDir;
                mapping = ZipUtils.unzipFolder(dataFile, zipDir, true, List.of(), true);
            }
            else {
                log.debug("Start loading file data to db");
                workingDir = tempDir;
                mapping = Map.of(dataFile.getFileName().toString(), originFilename);
            }
            ExecContextGraphSyncService.getWithSyncVoid(simpleExecContext.execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSyncVoid(simpleExecContext.execContextTaskStateId,
                            () -> loadFilesFromDirAfterZip(simpleExecContext, taskId, taskParamsYaml, workingDir, mapping)));
        }
        catch(UnzipArchiveException e) {
            final String es = "#995.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(BatchProcessingException e) {
            final String es = "#995.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(Throwable th) {
            final String es = "#995.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        finally {
            DirUtils.deletePathAsync(tempDir);
        }
    }

    private void loadFilesFromDirAfterZip(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, TaskParamsYaml taskParamsYaml, Path workingDir, Map<String, String> mapping) {
        ArtifactCleanerAtDispatcher.setBusy();
        try {
            batchSplitterTxService.loadFilesFromDirAfterZip(simpleExecContext, workingDir, mapping, taskParamsYaml, taskId);
        }
        finally {
            ArtifactCleanerAtDispatcher.notBusy();
        }
    }

}
