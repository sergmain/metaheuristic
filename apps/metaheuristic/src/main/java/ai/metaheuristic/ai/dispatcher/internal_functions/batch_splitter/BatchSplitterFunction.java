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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingCoreService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
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

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextCache execContextCache;
    private final IdsRepository idsRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final TaskProducingCoreService taskProducingCoreService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionVariableService internalFunctionVariableService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, Long taskId, String taskContextId, ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {

/*
        if (taskParamsYaml.task.inputs.isEmpty()) {
            throw new IllegalStateException("Input variable wasn't specified");
        }
        if (taskParamsYaml.task.inputs.size()>1) {
            throw new IllegalStateException("Too many input variables");
        }
        TaskParamsYaml.InputVariable inputVariable = taskParamsYaml.task.inputs.get(0);
*/
        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#995.020 Meta 'variable-for-splitting' wasn't found");
        }

        List<VariableUtils.VariableHolder> holders = new ArrayList<>();
        InternalFunctionProcessingResult result = internalFunctionVariableService.discoverVariables(execContextId, taskContextId, inputVariableName, holders);
        if (result != null) {
            return result;
        }
        if (holders.size()>1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.040 Too many variables");
        }

        VariableUtils.VariableHolder variableHolder = holders.get(0);
        String originFilename = variableHolder.getFilename();
        if (S.b(originFilename)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.input_variable_isnt_file, "#995.060 variable.filename is blank");
        }

        String ext = StrUtils.getExtension(originFilename);

        File tempDir=DirUtils.createTempDir("batch-file-upload-");;
        try {
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.080 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            internalFunctionVariableService.storeToFile(variableHolder, dataFile);
            if (dataFile.length()==0) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.100 Empty files aren't supported");
            }

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir);
                log.debug("Start loading file data to db");
                return loadFilesFromDirAfterZip(sourceCodeId, execContextId, taskContextId, tempDir, mapping, taskParamsYaml, taskId);
            }
            else {
                log.debug("Start loading file data to db");
                return loadFilesFromDirAfterZip(sourceCodeId, execContextId, taskContextId, tempDir, Map.of(dataFile.getName(), originFilename), taskParamsYaml, taskId);
            }
        }
        catch(UnzipArchiveException e) {
            final String es = "#995.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(BatchProcessingException e) {
            final String es = "#995.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(Throwable th) {
            final String es = "#995.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        finally {
            try {
                if (tempDir!=null && tempDir.exists()) {
//                    FileUtils.deleteDirectory(tempDir);
                    Files.deleteIfExists(tempDir.toPath());
                }
            } catch (IOException e) {
                log.warn("#995.180 Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
            }
        }
    }

    public InternalFunctionProcessingResult loadFilesFromDirAfterZip(
            Long sourceCodeId, Long execContextId, String taskContextId, File srcDir,
            final Map<String, String> mapping, TaskParamsYaml taskParamsYaml, Long taskId) throws IOException {

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#995.200 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            throw new IllegalStateException("#995.220 execContext wasn't found, execContextId: " + execContextId);
        }
        Set<ExecContextData.TaskVertex_140> descendants = execContextGraphTopLevelService.findDescendants(ec, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#995.240 Graph for ExecContext #"+execContextId+" is broken");
        }

        ExecContextParamsYaml execContextParamsYaml = ec.getExecContextParamsYaml();

        final ExecContextParamsYaml.Process process = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#995.260 Process '"+taskParamsYaml.task.processCode+"'not found");
        }

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);
        List<ExecContextData.ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        final String variableName = MetaUtils.getValue(process.metas, "output-variable");
        if (S.b(variableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#995.280 Meta with key 'output-variable' wasn't found for process '"+process.processCode+"'");
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        Files.list(srcDir.toPath())
                .forEach( dataFilePath ->  {
                    File file = dataFilePath.toFile();
                    currTaskNumber.incrementAndGet();
                    try {
                        if (file.isDirectory()) {
                            final Stream<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                                    });
                            createTasksForSubProcesses(files, execContextId, execContextParamsYaml, subProcesses, currTaskNumber, taskId, variableName,
                                    execContextParamsYaml.variables.inline, lastIds, process
                            );
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createTasksForSubProcesses(
                                    Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)),
                                    execContextId, execContextParamsYaml, subProcesses, currTaskNumber, taskId, variableName,
                                    execContextParamsYaml.variables.inline, lastIds, process
                            );
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.300 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });
        execContextGraphTopLevelService.createEdges(execContextId, lastIds, descendants);
        return INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

    /**
     * @param files
     * @param execContextId
     * @param execContextParamsYaml
     * @param subProcesses
     * @param currTaskNumber
     * @param parentTaskId
     * @param outputVariableName
     * @param inlines
     * @param lastIds
     * @param process
     */
    public void createTasksForSubProcesses(
            Stream<BatchTopLevelService.FileWithMapping> files, Long execContextId, ExecContextParamsYaml execContextParamsYaml,
            List<ExecContextData.ProcessVertex> subProcesses,
            AtomicInteger currTaskNumber, Long parentTaskId, String outputVariableName,
            Map<String, Map<String, String>> inlines, List<Long> lastIds, ExecContextParamsYaml.Process process) {

        List<Long> parentTaskIds = List.of(parentTaskId);
        TaskImpl t = null;
        String subProcessContextId = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("#995.320 Process '" + subProcess.process + "' wasn't found");
            }

            if (process.logic!=EnumsApi.SourceCodeSubProcessLogic.sequential) {
                throw new BreakFromLambdaException("#995.340 only the 'sequential' logic is supported");
            }
            // all subProcesses must have the same processContextId
            if (subProcessContextId!=null && !subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("#995.360 Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }

            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(currTaskNumber.get()));
            t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
            if (t==null) {
                throw new BreakFromLambdaException("#995.380 Creation of task failed");
            }
            List<Long> currTaskIds = List.of(t.getId());
            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, currTaskIds);
            parentTaskIds = currTaskIds;
            subProcessContextId = subProcess.processContextId;
        }

        if (subProcessContextId!=null) {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
            lastIds.add(t.id);

            List<VariableUtils.VariableHolder> variableHolders = files
                    .map(f-> {
                        String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());

                        Variable v;
                        try {
                            try( FileInputStream fis = new FileInputStream(f.file)) {
                                v = variableService.createInitialized(fis, f.file.length(), variableName, f.originName, execContextId, currTaskContextId);
                            }
                        } catch (IOException e) {
                            throw new BreakFromLambdaException(e);
                        }
                        SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.taskContextId);
                        return new VariableUtils.VariableHolder(sv);
                    })
                    .collect(Collectors.toList());

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            Variable v = variableService.createInitialized(bais, bytes.length, outputVariableName, null, execContextId, currTaskContextId);

            int i=0;
        }
    }
}
