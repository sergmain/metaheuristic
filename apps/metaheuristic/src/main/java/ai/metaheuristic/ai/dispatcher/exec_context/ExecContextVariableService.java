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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.ExecContextCommonException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:13 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableService {

    private final ExecContextService execContextService;
    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void initInputVariableWithNull(Long execContextId, ExecContextParamsYaml execContextParamsYaml, int varIndex) {
        if (execContextParamsYaml.variables.inputs.size()<varIndex+1) {
            throw new ExecContextCommonException(
                    S.f("#697.020 varIndex is bigger than number of input variables. varIndex: %s, number: %s",
                            varIndex, execContextParamsYaml.variables.inputs.size()));
        }
        final ExecContextParamsYaml.Variable variable = execContextParamsYaml.variables.inputs.get(varIndex);
        if (!variable.getNullable()) {
            throw new ExecContextCommonException(S.f("#697.025 sourceCode %s, input variable %s must be declared as nullable to be set as null",
                    execContextParamsYaml.sourceCodeUid, variable.name));
        }
        String inputVariable = variable.name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("##697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.warn("#697.060 ExecContext #{} wasn't found", execContextId);
        }
        variableService.createInitializedWithNull(inputVariable, execContextId, Consts.TOP_LEVEL_CONTEXT_ID );
    }

    /**
     * this method is expecting only one input variable in execContext
     * for initializing more that one input variable the method initInputVariables has to be used
     *
     * varIndex - index of variable, start with 0
     */
    @Transactional
    public void initInputVariable(InputStream is, long size, String originFilename, Long execContextId, ExecContextParamsYaml execContextParamsYaml, int varIndex) {
        if (execContextParamsYaml.variables.inputs.size()<varIndex+1) {
            throw new ExecContextCommonException(
                    S.f("#697.020 varIndex is bigger than number of input variables. varIndex: %s, number: %s",
                            varIndex, execContextParamsYaml.variables.inputs.size()));
        }
        String inputVariable = execContextParamsYaml.variables.inputs.get(varIndex).name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("##697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.warn("#697.060 ExecContext #{} wasn't found", execContextId);
        }
        variableService.createInitialized(is, size, inputVariable, originFilename, execContextId, Consts.TOP_LEVEL_CONTEXT_ID );
    }

    @Transactional
    public void initInputVariables(ExecContextData.VariableInitializeList list) {
        if (list.execContextParamsYaml.variables.inputs.size()!=list.vars.size()) {
            throw new ExecContextCommonException(
                    S.f("#697.080 the number of input streams and variables for initing are different. is count: %d, vars count: %d",
                            list.vars.size(), list.execContextParamsYaml.variables.inputs.size()));
        }
        ExecContextImpl execContext = execContextService.findById(list.execContextId);
        if (execContext==null) {
            throw new ExecContextCommonException(
                    S.f("#697.100 ExecContext #{} wasn't found", list.execContextId));
        }
        for (int i = 0; i < list.vars.size(); i++) {
            ExecContextData.VariableInitialize var = list.vars.get(i);
            String inputVariable = list.execContextParamsYaml.variables.inputs.get(i).name;
            if (S.b(inputVariable)) {
                throw new ExecContextCommonException("#697.120 Wrong format of sourceCode, input variable for source code isn't specified");
            }
            variableService.createInitialized(var.is, var.size, inputVariable, var.originFilename, execContext.id, Consts.TOP_LEVEL_CONTEXT_ID );
        }
    }

    @Transactional
    public void storeDataInVariable(TaskParamsYaml.OutputVariable outputVariable, File file) {
        Variable variable = getVariable(outputVariable);

        final ResourceCloseTxEvent resourceCloseTxEvent = new ResourceCloseTxEvent();
        eventPublisher.publishEvent(resourceCloseTxEvent);
        try {
            InputStream is = new FileInputStream(file);
            resourceCloseTxEvent.add(is);
            variableService.update(is, file.length(), variable);
        } catch (FileNotFoundException e) {
            throw new InternalFunctionException(system_error, "#697.180 Can't open file   "+ file.getAbsolutePath());
        }
    }

    @Transactional
    public void storeStringInVariable(TaskParamsYaml.OutputVariable outputVariable, String value) {
        Variable variable = getVariable(outputVariable);

        byte[] bytes = value.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        variableService.update(is, bytes.length, variable);
    }

    private Variable getVariable(TaskParamsYaml.OutputVariable outputVariable) {
        Variable variable;
        if (outputVariable.context == EnumsApi.VariableContext.local) {
            variable = variableRepository.findById(outputVariable.id).orElse(null);
            if (variable == null) {
                throw new InternalFunctionException(variable_not_found, "#697.140 Variable not found for code " + outputVariable);
            }
        }
        else {
            throw new InternalFunctionException(global_variable_is_immutable, "#697.160 Can't store data in a global variable " + outputVariable.name);
        }
        return variable;
    }

    @Transactional
    public Void initOutputVariable(Long execContextId, ExecContextParamsYaml.Variable output) {
        TxUtils.checkTxExists();

        SimpleVariable sv = variableService.findVariableInAllInternalContexts(output.name, Consts.TOP_LEVEL_CONTEXT_ID, execContextId);
        if (sv == null) {
            Variable v = variableService.createUninitialized(output.name, execContextId, Consts.TOP_LEVEL_CONTEXT_ID);
        }
        return null;
    }


}
