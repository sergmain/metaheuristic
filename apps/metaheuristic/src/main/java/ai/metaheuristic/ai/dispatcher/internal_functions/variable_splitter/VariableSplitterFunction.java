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

package ai.metaheuristic.ai.dispatcher.internal_functions.variable_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
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
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class VariableSplitterFunction implements InternalFunction {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextCache execContextCache;
    private final IdsRepository idsRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final TaskProducingCoreService taskProducingCoreService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Override
    public String getCode() {
        return Consts.MH_VARIABLE_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_VARIABLE_SPLITTER_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, Long taskId, String taskContextId, ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {

        if (taskParamsYaml.task.inputs.isEmpty()) {
            throw new IllegalStateException("Input variable wasn't specified");
        }
        if (taskParamsYaml.task.inputs.size()>1) {
            throw new IllegalStateException("Too many input variables");
        }
        TaskParamsYaml.InputVariable inputVariable = taskParamsYaml.task.inputs.get(0);
        String originFilename;
        // check the presence of variable
        if (inputVariable.context== EnumsApi.VariableContext.local) {
            Variable v = variableRepository
                    .findById(inputVariable.id)
                    .orElseThrow(()-> new IllegalStateException("Variable not found for code " + inputVariable));
            originFilename = v.filename;
        }
        else {
            GlobalVariable gv = globalVariableRepository
                    .findById(inputVariable.id)
                    .orElseThrow(()->new IllegalStateException("GlobalVariable not found for code " + inputVariable));
            originFilename = gv.filename;
        }

        if (S.b(originFilename)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.input_variable_isnt_file, "variable.filename is blank");
        }

        String ext = StrUtils.getExtension(originFilename);

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir);
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(sourceCodeId, execContextId, taskContextId, tempDir, mapping, taskParamsYaml, taskId);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(sourceCodeId, execContextId, taskContextId, tempDir, Map.of(dataFile.getName(), originFilename), taskParamsYaml, taskId);
            }
        }
        catch(Throwable th) {
            final String es = "#995.110 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            ExceptionUtils.rethrow(th);
        }
        finally {
            try {
                if (tempDir!=null) {
                    FileUtils.deleteDirectory(tempDir);
                }
            } catch (IOException e) {
                log.warn("Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
            }
        }
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

    public void loadFilesFromDirAfterZip(
            Long sourceCodeId, Long execContextId, String taskContextId, File srcDir,
            final Map<String, String> mapping, TaskParamsYaml taskParamsYaml, Long taskId) throws IOException {

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            throw new IllegalStateException("#995.200 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            throw new IllegalStateException("#995.202 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml execContextParamsYaml = ec.getExecContextParamsYaml();

        final ExecContextParamsYaml.Process process = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            throw new BreakFromLambdaException("Process '"+taskParamsYaml.task.processCode+"'not found");
        }

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);
        List<ExecContextData.ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        final String variableName = MetaUtils.getValue(process.metas, "output-variable");
        if (S.b(variableName)) {
            throw new BreakFromLambdaException("Meta with key 'output-variable' wasn't found for process '"+process.processCode+"'");
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger permutationNumber = new AtomicInteger(0);
        Files.list(srcDir.toPath())
                .forEach( dataFilePath ->  {
                    File file = dataFilePath.toFile();
                    permutationNumber.incrementAndGet();
                    try {
                        if (file.isDirectory()) {
                            final Stream<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                                    });
                            createTasksForSubProcesses(files, execContextId, execContextParamsYaml, subProcesses, permutationNumber, taskId, variableName,
                                    execContextParamsYaml.variables.inline, lastIds
                            );
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createTasksForSubProcesses(
                                    Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)),
                                    execContextId, execContextParamsYaml, subProcesses, permutationNumber, taskId, variableName,
                                    execContextParamsYaml.variables.inline, lastIds
                            );
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.130 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });
    }

    /**
     *
     * @param files
     * @param execContextId
     * @param execContextParamsYaml
     * @param subProcesses
     * @param permutationNumber
     * @param parentTaskId
     * @param outputVariableName
     * @param inlines
     * @param lastIds
     */
    public void createTasksForSubProcesses(
            Stream<BatchTopLevelService.FileWithMapping> files, Long execContextId, ExecContextParamsYaml execContextParamsYaml,
            List<ExecContextData.ProcessVertex> subProcesses,
            AtomicInteger permutationNumber, Long parentTaskId, String outputVariableName,
            Map<String, Map<String, String>> inlines, List<Long> lastIds) {

        List<Long> parentTaskIds = List.of(parentTaskId);
        TaskImpl t = null;
        String subProcessContextId = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("Process '" + subProcess.process + "' wasn't found");
            }
            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(permutationNumber.get()));
            t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
            if (t==null) {
                throw new BreakFromLambdaException("Creation of task failed");
            }
            List<Long> currTaskIds = List.of(t.getId());
            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, currTaskIds);
            parentTaskIds = currTaskIds;
            // all subProcesses must have the same processContextId
            if (subProcessContextId!=null && !subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }
            subProcessContextId = subProcess.processContextId;
        }

        if (subProcessContextId!=null) {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(permutationNumber.get()));
            lastIds.add(t.id);

            List<VariableUtils.VariableHolder> variableHolders = files
                    .map(f-> {
                        String variableName = S.f("mh.array-element-%s", UUID.randomUUID().toString());

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
