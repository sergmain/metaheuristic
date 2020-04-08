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

package ai.metaheuristic.ai.dispatcher.internal_functions.aggregate_internal_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 4/03/2020
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AggregateInternalContextFunction implements InternalFunction {

    private final VariableRepository variableRepository;
    private final VariableService variableService;

    @Override
    public String getCode() {
        return Consts.MH_AGGREGATE_INTERNAL_CONTEXT_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_AGGREGATE_INTERNAL_CONTEXT_FUNCTION;
    }

    @Override
    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, String taskContextId,
            SourceCodeParamsYaml.VariableDefinition variableDefinition, TaskParamsYaml taskParamsYaml) {

        if (taskParamsYaml.task.outputs.size()!=1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_outputs_is_incorrect,
                    "There must be only one output variable, current: "+ taskParamsYaml.task.outputs);
        }

        Variable bd;
        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        if (outputVariable.context==EnumsApi.VariableContext.local) {
            bd = variableRepository.findById(Long.valueOf(outputVariable.id)).orElse(null);
            if (bd == null) {
                throw new IllegalStateException("Variable not found for code " + outputVariable);
            }
        }
        else {
            throw new IllegalStateException("GlobalVariable not found for code " + outputVariable);
        }

        String[] names = StringUtils.split(MetaUtils.getValue(taskParamsYaml.task.metas, "variables"), ", ");
        if (names==null || names.length==0) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "Meta 'variables' wasn't found or empty, process: "+ taskParamsYaml.task.processCode);
        }
        List<SimpleVariableAndStorageUrl> list = variableRepository.findByExecContextIdAndNames(execContextId, names);

        File dir = DirUtils.createTempDir("mh-aggregate-internal-context");
        if (dir==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR);
        }
        File resultDir = DirUtils.createTempDir("mh-aggregate-internal-context-result");
        if (resultDir==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR);
        }

        list.stream().map(o->o.taskContextId).collect(Collectors.toSet())
                .forEach(contextId->{
                    File taskContextDir = new File(dir, contextId);
                    //noinspection ResultOfMethodCallIgnored
                    taskContextDir.mkdirs();
                    list.stream().filter(t->t.taskContextId.equals(contextId))
                            .forEach( v->{
                                File varFile = new File(taskContextDir, v.variable);
                                variableService.storeToFile(v.id, varFile);
                            });
                });

        File zipFile = new File(resultDir, "result.zip");
        ZipUtils.createZip(dir, zipFile);
        try (InputStream is = new FileInputStream(zipFile)) {
            variableService.update(is, zipFile.length(), bd);
        } catch (Throwable e) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "Error while storing result to variable '"+bd.name);
        }

        return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }
}
