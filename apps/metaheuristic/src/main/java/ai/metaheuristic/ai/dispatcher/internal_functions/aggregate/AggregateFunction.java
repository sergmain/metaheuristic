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

package ai.metaheuristic.ai.dispatcher.internal_functions.aggregate;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 11:19 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AggregateFunction implements InternalFunction {

    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String META_ERROR_CONTROL = "error-control-policy";
    public enum ErrorControlPolicy { fail, ignore }

    @Override
    public String getCode() {
        return Consts.MH_AGGREGATE_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_AGGREGATE_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        final ResourceCloseTxEvent resourceCloseTxEvent = new ResourceCloseTxEvent();
        eventPublisher.publishEvent(resourceCloseTxEvent);

        if (taskParamsYaml.task.outputs.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_outputs_is_incorrect,
                            "#992.020 There must be only one output variable, current: "+ taskParamsYaml.task.outputs));
        }

        Variable variable;
        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        if (outputVariable.context==EnumsApi.VariableContext.local) {
            variable = variableRepository.findById(outputVariable.id).orElse(null);
            if (variable == null) {
                throw new InternalFunctionException(
                        new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                        "#992.040 Variable not found for code " + outputVariable));
            }
        }
        else {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.global_variable_is_immutable,
                    "#992.060 Can't store data in a global variable " + outputVariable.name));
        }

        String[] names = StringUtils.split(MetaUtils.getValue(taskParamsYaml.task.metas, "variables"), ", ");
        if (names==null || names.length==0) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#992.080 Meta 'variables' wasn't found or empty, process: "+ taskParamsYaml.task.processCode));
        }

        String policyMeta = MetaUtils.getValue(taskParamsYaml.task.metas, META_ERROR_CONTROL);
        ErrorControlPolicy policy = S.b(policyMeta) ? ErrorControlPolicy.ignore : ErrorControlPolicy.valueOf(policyMeta);

        List<SimpleVariable> list = variableRepository.getIdAndStorageUrlInVarsForExecContext(simpleExecContext.execContextId, names);

        File tempDir = DirUtils.createTempDir("mh-aggregate-internal-context-");
        if (tempDir==null) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#992.100 Can't create temporary directory in dir "+ SystemUtils.JAVA_IO_TMPDIR));
        }
        resourceCloseTxEvent.add(tempDir);

        File outputDir = new File(tempDir, outputVariable.name);
        if (!outputDir.mkdirs()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#992.120 Can't create output directory  "+ outputDir.getAbsolutePath()));
        }

        list.stream().map(o->o.taskContextId).collect(Collectors.toSet())
                .forEach(contextId->{
                    File taskContextDir = new File(outputDir, contextId);
                    //noinspection ResultOfMethodCallIgnored
                    taskContextDir.mkdirs();
                    list.stream().filter(t-> contextId.equals(t.taskContextId))
                            .forEach( v->{
                                if (v.nullified) {
                                    return;
                                }
                                try {
                                    File varFile = new File(taskContextDir, v.variable);
                                    variableService.storeToFile(v.id, varFile);
                                } catch (VariableDataNotFoundException e) {
                                    log.error("#992.140 Variable #{}, name {},  wasn't found", v.id, v.variable);
                                    if (policy==ErrorControlPolicy.fail) {
                                        throw e;
                                    }
                                }
                            });
                });

        File zipFile = new File(tempDir, "result-for-"+outputVariable.name+".zip");

        ZipUtils.createZip(outputDir, zipFile);
        try {
            InputStream is = new FileInputStream(zipFile);
            resourceCloseTxEvent.add(is);
            variableService.update(is, zipFile.length(), variable);
        } catch (FileNotFoundException e) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "Can't open zipFile   "+ zipFile.getAbsolutePath()));
        }
    }
}
