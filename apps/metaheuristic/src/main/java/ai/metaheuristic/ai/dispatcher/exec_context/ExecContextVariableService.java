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
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableEntityManagerService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.ExecContextCommonException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private final ExecContextCache execContextCache;
    private final VariableTxService variableService;
    private final VariableEntityManagerService variableEntityManagerService;
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
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#697.060 ExecContext #{} wasn't found", execContextId);
        }
        variableService.createInitializedWithNull(inputVariable, execContextId, Consts.TOP_LEVEL_CONTEXT_ID );
    }

    /**
     * varIndex - index of variable, start with 0
     */
    @Transactional
    public void initInputVariable(
            InputStream is, long size, String originFilename, Long execContextId, ExecContextParamsYaml execContextParamsYaml,
            int varIndex, EnumsApi.VariableType type) {
        if (execContextParamsYaml.variables.inputs.size()<varIndex+1) {
            throw new ExecContextCommonException(
                    S.f("#697.020 varIndex is bigger than number of input variables. varIndex: %s, number: %s",
                            varIndex, execContextParamsYaml.variables.inputs.size()));
        }
        String inputVariable = execContextParamsYaml.variables.inputs.get(varIndex).name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("##697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#697.060 ExecContext #{} wasn't found", execContextId);
        }
        variableEntityManagerService.createInitialized(is, size, inputVariable, originFilename, execContextId, Consts.TOP_LEVEL_CONTEXT_ID, type );
    }

    @SneakyThrows
    @Transactional
    public void storeDataInVariable(TaskParamsYaml.OutputVariable outputVariable, Path file) {
        Variable variable = getVariable(outputVariable);

        final ResourceCloseTxEvent resourceCloseTxEvent = new ResourceCloseTxEvent();
        eventPublisher.publishEvent(resourceCloseTxEvent);
        try {
            InputStream is = Files.newInputStream(file);
            resourceCloseTxEvent.add(is);
            variableEntityManagerService.update(is, Files.size(file), variable);
        } catch (FileNotFoundException e) {
            throw new InternalFunctionException(system_error, "#697.180 Can't open file   "+ file.normalize());
        }
    }

    @Transactional
    public void storeStringInVariable(TaskParamsYaml.OutputVariable outputVariable, String value) {
        Variable variable = getVariable(outputVariable);

        byte[] bytes = value.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        variableEntityManagerService.update(is, bytes.length, variable);
    }

    @Transactional
    public void storeBytesInVariable(TaskParamsYaml.OutputVariable outputVariable, byte[] bytes) {
        Variable variable = getVariable(outputVariable);
        InputStream is = new ByteArrayInputStream(bytes);
        variableEntityManagerService.update(is, bytes.length, variable);
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
