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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.events.NewWebsocketTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTxService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.commons.graph.ExecContextProcessGraphService;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.commons.CommonConsts;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.ERROR;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 10:48 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextCreatorService {

    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextCache execContextCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextVariableStateTxService execContextVariableStateCache;
    private final ApplicationEventPublisher eventPublisher;
    private final VariableTxService variableTxService;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExecContextCreationResult extends BaseDataClass {
        public ExecContextImpl execContext;
        public SourceCodeImpl sourceCode;

        @JsonCreator
        public ExecContextCreationResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public ExecContextCreationResult(List<String> errorMessages) {
            this.errorMessages = errorMessages;
        }
        public ExecContextCreationResult(String errorMessage) {
            this.addErrorMessage(errorMessage);
        }

        public ExecContextCreationResult(SourceCodeImpl sourceCode) {
            this.sourceCode = sourceCode;
        }

        public ExecContextCreationResult(SourceCodeImpl sourceCode, ExecContextImpl execContext) {
            this.sourceCode = sourceCode;
            this.execContext = execContext;
        }
    }

    @Transactional(rollbackFor = {CommonRollbackException.class, ExecContextTooManyInstancesException.class} )
    public ExecContextCreationResult createExecContextAndStart(
            Long sourceCodeId, ExecContextApiData.UserExecContext context, boolean isProduceTasks,
            ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo,
            ExecContextParamsYaml.@Nullable GitSources gitSources, @Nullable Map<String, String> inputVariables) {

        SourceCodeSyncService.checkWriteLockPresent(sourceCodeId);

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, context.companyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            throw new CommonRollbackException(
                "562.060 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.companyId(), ERROR);
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            throw new CommonRollbackException(
                "562.080 Error creating execContext: sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.companyId(), ERROR);
        }
        final ExecContextCreationResult creationResult = createExecContext(sourceCode, context, rootAndParent, execContextCreationInfo, gitSources);

        if (!isProduceTasks) {
            return creationResult;
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);
        if (!scg.variables.inputs.isEmpty()) {
            if (creationResult.execContext==null) {
                throw new IllegalStateException("562.118 ExecContext wasn't created, so its input variables can't be initialized");
            }
            initInputVariables(scg.variables.inputs, inputVariables, creationResult.execContext.id);
        }

        if (CollectionUtils.isNotEmpty(creationResult.getErrorMessages())) {
            throw new CommonRollbackException(creationResult.getErrorMessages(), ERROR);
        }

        produceTasksForExecContextInternal(sourceCode, creationResult);
        if (CollectionUtils.isNotEmpty(creationResult.getErrorMessages())) {
            throw new CommonRollbackException(creationResult.getErrorMessages(), ERROR);
        }
        return creationResult;
    }

    @Transactional
    public void produceTasksForExecContext(SourceCodeImpl sourceCode, Long execContextId) {
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return;
        }
        ExecContextCreationResult result = new ExecContextCreationResult();
        result.execContext = ec;
        produceTasksForExecContextInternal(sourceCode, result);
    }

    private void produceTasksForExecContextInternal(SourceCodeImpl sourceCode, ExecContextCreationResult creationResult) {
        TxUtils.checkTxExists();
        ExecContextSyncService.getWithSyncVoidForCreation(creationResult.execContext.id, () ->
                ExecContextGraphSyncService.getWithSyncVoidForCreation(creationResult.execContext.execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSyncVoidForCreation(creationResult.execContext.execContextTaskStateId,
                            () -> {
                                SourceCodeApiData.TaskProducingResultComplex result = execContextTaskProducingService.produceAndStartAllTasks(
                                    sourceCode, creationResult.execContext);
                                if (result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.OK) {
                                    creationResult.addErrorMessage(result.sourceCodeValidationResult.error);
                                }
                                if (result.taskProducingStatus != EnumsApi.TaskProducingStatus.OK) {
                                    creationResult.addErrorMessage("562.150 Error while producing new tasks " + result.taskProducingStatus);
                                }
                                if (result.anyExternalFunction) {
                                    eventPublisher.publishEvent(new NewWebsocketTxEvent(Enums.WebsocketEventType.task));
                                }
                            })));
    }

    /**
     *
     * @param sourceCode SourceCodeImpl
     * @param context user's context - accountId+companyId. companyId can be different from sourceCode.companyId
     * @return ExecContextCreationResult
     */
    public ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, ExecContextApiData.UserExecContext context,
                                                       ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo,
                                                       ExecContextParamsYaml.@Nullable GitSources gitSources) {
        TxUtils.checkTxExists();
        SourceCodeSyncService.checkWriteLockPresent(sourceCode.id);

        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status.status != EnumsApi.SourceCodeValidateStatus.OK) {
            throw new CommonRollbackException(sourceCodeValidation.getErrorMessagesAsList(), ERROR);
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);

        if (scg.instances>0) {
            int count = execContextRepository.countInProgress(scg.uid);
            if (count>=scg.instances) {
                throw new ExecContextTooManyInstancesException(sourceCode.uid, scg.instances, count);
            }
        }

        if (ExecContextProcessGraphService.anyError(scg)) {
            throw new CommonRollbackException("562.180 processGraph is broken", ERROR);
        }

        ExecContextImpl execContext = createExecContext(sourceCode, context, scg, rootAndParent, execContextCreationInfo, gitSources);
        ExecContextCreationResult ecr = new ExecContextCreationResult();
        ecr.execContext = execContext;
        return ecr;
    }

    private ExecContextImpl createExecContext(
            SourceCodeImpl sourceCode, ExecContextApiData.UserExecContext context, SourceCodeGraph sourceCodeGraph,
            ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo,
            ExecContextParamsYaml.@Nullable GitSources gitSources) {

        ExecContextImpl ec = new ExecContextImpl();
        ec.companyId = context.companyId();
        ec.accountId = context.accountId();
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml ecpy = to(sourceCodeGraph);
        ecpy.sourceCodeUid = sourceCode.uid;
        // already resolved by the orchestrator, OUTSIDE this transaction - `ls-remote` is a network call
        // and SPRING-TX-RULES.md 1 keeps context reads out of the tx
        ecpy.gitSources = gitSources;
        if (rootAndParent!=null) {
            ecpy.execContextGraph = new ExecContextParamsYaml.ExecContextGraph(rootAndParent.rootExecContextId, rootAndParent.parentExecContextId);
            ec.rootExecContextId = rootAndParent.rootExecContextId;
        }
        copyToParams(execContextCreationInfo, ecpy);

        ec.updateParams(ecpy);
        ec.setValid(true);

        ExecContextTaskState execContextTaskState = new ExecContextTaskState();
        execContextTaskState.updateParams(new ExecContextTaskStateParamsYaml());
        execContextTaskState.createdOn = System.currentTimeMillis();
        execContextTaskState = execContextTaskStateRepository.save(execContextTaskState);
        ec.execContextTaskStateId = execContextTaskState.id;

        ExecContextGraph execContextGraph = new ExecContextGraph();
        execContextGraph.updateParams(new ExecContextGraphParamsYaml());
        execContextGraph.createdOn = System.currentTimeMillis();
        execContextGraph = execContextGraphCache.save(execContextGraph);
        ec.execContextGraphId = execContextGraph.id;

        ExecContextVariableState bean = new ExecContextVariableState();
        bean.updateParams(new ExecContextApiData.ExecContextVariableStates());
        bean.createdOn = System.currentTimeMillis();
        bean = execContextVariableStateCache.save(bean);
        ec.execContextVariableStateId = bean.id;

        ec = execContextCache.save(ec);
        return ec;
    }

    /**
     * Initializes every source-level input the SourceCode declares, from the values the caller supplied.
     *
     * <p>This runs between creating the ExecContext and producing its Tasks, which is the only window it
     * can run in: a Task is produced against variables that already exist, and a variable needs an
     * ExecContext to belong to. There is exactly one moment in between, and this is it.
     *
     * <p>Both halves of the match are enforced. A declared input with no value fails - that is the old
     * refusal, unchanged for every caller that passes nothing. A value for a name the SourceCode does not
     * declare fails too, and that half is not pedantry: a typo in a variable name would otherwise leave
     * the real input uninitialized and surface far away as a null variable at whichever Task read it.
     */
    private void initInputVariables(
            List<ExecContextParamsYaml.Variable> inputs, @Nullable Map<String, String> inputVariables, Long execContextId) {

        final Map<String, String> values = inputVariables==null ? Map.of() : inputVariables;

        final List<String> missing = new ArrayList<>();
        for (ExecContextParamsYaml.Variable input : inputs) {
            if (!values.containsKey(input.name)) {
                missing.add(input.name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("562.120 Tasks can't be created with execContext because SourceCode has "
                + "input variable(s) which weren't initialized: " + String.join(", ", missing)
                + ". Supply a value for each of them, or initialize them before producing Tasks.");
        }
        final List<String> declared = inputs.stream().map(v -> v.name).toList();
        final List<String> unknown = values.keySet().stream().filter(name -> !declared.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("562.122 Value(s) supplied for name(s) which the SourceCode doesn't "
                + "declare as an input variable: " + String.join(", ", unknown) + ", declared: " + String.join(", ", declared));
        }
        for (ExecContextParamsYaml.Variable input : inputs) {
            final byte[] bytes = values.get(input.name).getBytes(StandardCharsets.UTF_8);
            variableTxService.createInitializedTx(new ByteArrayInputStream(bytes), bytes.length, input.name, null,
                execContextId, CommonConsts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.text);
        }
    }
    private static void copyToParams(ExecContextData.@Nullable ExecContextCreationInfo info, ExecContextParamsYaml ecpy) {
        if (info == null) {
            return;
        }
        ecpy.desc = info.desc();
    }

    private static ExecContextParamsYaml to(SourceCodeGraph sourceCodeGraph) {
        ExecContextParamsYaml params = new ExecContextParamsYaml();
        params.clean = sourceCodeGraph.clean;
        params.processes.addAll(sourceCodeGraph.processes);
        params.groups.addAll(sourceCodeGraph.groups);
        params.processesGraph = ExecContextProcessGraphService.asString(sourceCodeGraph.processGraph);
        initVariables(sourceCodeGraph.variables, params.variables);

        return params;
    }

    private static void initVariables(ExecContextParamsYaml.VariableDeclaration src, ExecContextParamsYaml.VariableDeclaration trg) {
        trg.inline.putAll(src.inline);
        trg.globals = src.globals;
        trg.inputs.addAll(src.inputs);
        trg.outputs.addAll(src.outputs);
    }

}
