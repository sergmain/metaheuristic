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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo) {

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
        final ExecContextCreationResult creationResult = createExecContext(sourceCode, context, rootAndParent, execContextCreationInfo);

        if (!isProduceTasks) {
            return creationResult;
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);
        if (!scg.variables.inputs.isEmpty()) {
            throw new IllegalStateException("562.120 Tasks can't be created with execContext because SourceCode has input variable(s). Task must be created after initializing SourceCode input variables.");
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
                                                       ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo) {
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

        ExecContextImpl execContext = createExecContext(sourceCode, context, scg, rootAndParent, execContextCreationInfo);
        ExecContextCreationResult ecr = new ExecContextCreationResult();
        ecr.execContext = execContext;
        return ecr;
    }

    private ExecContextImpl createExecContext(
            SourceCodeImpl sourceCode, ExecContextApiData.UserExecContext context, SourceCodeGraph sourceCodeGraph,
            ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo) {

        ExecContextImpl ec = new ExecContextImpl();
        ec.companyId = context.companyId();
        ec.accountId = context.accountId();
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml ecpy = to(sourceCodeGraph);
        ecpy.sourceCodeUid = sourceCode.uid;
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
