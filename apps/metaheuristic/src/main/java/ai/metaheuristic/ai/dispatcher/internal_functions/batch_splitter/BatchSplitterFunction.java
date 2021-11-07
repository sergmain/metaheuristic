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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
@RequiredArgsConstructor
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

        File tempDir=DirUtils.createTempDir("batch-file-upload-");
        try {
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.080 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            internalFunctionVariableService.storeToFile(variableHolder, dataFile);
            if (dataFile.length()==0) {
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.100 Empty files aren't supported"));
            }

            final File workingDir;
            Map<String, String> mapping;
            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                File zipDir=new File(tempDir, "zip");

                log.debug("Start loading .zip file data to db");
                workingDir = zipDir;
                mapping = ZipUtils.unzipFolder(dataFile, zipDir, true, List.of());
            }
            else {
                log.debug("Start loading file data to db");
                workingDir = tempDir;
                mapping = Map.of(dataFile.getName(), originFilename);
            }
            ExecContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                            batchSplitterTxService.loadFilesFromDirAfterZip(simpleExecContext, workingDir, mapping, taskParamsYaml, taskId)));
        }
        catch(UnzipArchiveException e) {
            final String es = "#995.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(BatchProcessingException e) {
            final String es = "#995.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(Throwable th) {
            final String es = "#995.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        finally {
            try {
                if (tempDir!=null && tempDir.exists()) {
                    FileUtils.deleteDirectory(tempDir);
                }
            } catch (IOException e) {
                log.warn("#995.180 Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
            }
        }
    }

}
