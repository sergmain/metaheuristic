/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
            Long sourceCodeId, ExecContextData.UserExecContext context, boolean isProduceTasks, @Nullable ExecContextData.RootAndParent rootAndParent) {

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
        final ExecContextCreationResult creationResult = createExecContext(sourceCode, context, rootAndParent);

        if (!isProduceTasks) {
            return creationResult;
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        if (scpy.source.variables!=null && !scpy.source.variables.inputs.isEmpty()) {
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
        eventPublisher.publishEvent(new NewWebsocketTxEvent(Enums.WebsocketEventType.task));
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
                            })));
    }

    /**
     *
     * @param sourceCode SourceCodeImpl
     * @param context user's context - accountId+companyId. companyId can be different from sourceCode.companyId
     * @return ExecContextCreationResult
     */
    public ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, ExecContextData.UserExecContext context, @Nullable ExecContextData.RootAndParent rootAndParent) {
        TxUtils.checkTxExists();
        SourceCodeSyncService.checkWriteLockPresent(sourceCode.id);

        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status.status != EnumsApi.SourceCodeValidateStatus.OK) {
            throw new CommonRollbackException(sourceCodeValidation.getErrorMessagesAsList(), ERROR);
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        if (scpy.source.instances!=null && scpy.source.instances>0) {
            int count = execContextRepository.countInProgress(scpy.source.uid);
            if (count>=scpy.source.instances) {
                throw new ExecContextTooManyInstancesException(sourceCode.uid, scpy.source.instances, count);
            }
        }

        AtomicLong contextId = new AtomicLong();
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                EnumsApi.SourceCodeLang.yaml, scspy.source, () -> String.valueOf(contextId.incrementAndGet()));

        if (ExecContextProcessGraphService.anyError(sourceCodeGraph)) {
            throw new CommonRollbackException("562.180 processGraph is broken", ERROR);
        }

        ExecContextImpl execContext = createExecContext(sourceCode, context, sourceCodeGraph, rootAndParent);
        ExecContextCreationResult ecr = new ExecContextCreationResult();
        ecr.execContext = execContext;
        return ecr;
    }

    private ExecContextImpl createExecContext(
            SourceCodeImpl sourceCode, ExecContextData.UserExecContext context, SourceCodeData.SourceCodeGraph sourceCodeGraph,
            @Nullable ExecContextData.RootAndParent rootAndParent) {

        ExecContextImpl ec = new ExecContextImpl();
        ec.companyId = context.companyId();
        ec.accountId = context.accountId();
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml expy = to(sourceCodeGraph);
        expy.sourceCodeUid = sourceCode.uid;
        if (rootAndParent!=null) {
            expy.execContextGraph = new ExecContextParamsYaml.ExecContextGraph(rootAndParent.rootExecContextId, rootAndParent.parentExecContextId);
            ec.rootExecContextId = rootAndParent.rootExecContextId;
        }
        ec.updateParams(expy);
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

    private static ExecContextParamsYaml to(SourceCodeData.SourceCodeGraph sourceCodeGraph) {
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
