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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 10:48 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextCreatorService {

    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextService execContextService;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextSyncService execContextSyncService;
    private final SourceCodeSyncService sourceCodeSyncService;

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

    @Transactional
    public ExecContextCreationResult createExecContext(Long sourceCodeId, DispatcherContext context) {
        sourceCodeSyncService.checkWriteLockPresent(sourceCodeId);

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, context.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreationResult("#562.020 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreationResult("#562.040 Error creating execContext: " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        return createExecContext(sourceCode, context.getCompanyId());
    }

    @Transactional
    public ExecContextCreationResult createExecContextAndStart(Long sourceCodeId, Long companyId) {
        sourceCodeSyncService.checkWriteLockPresent(sourceCodeId);

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, companyId);
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreationResult("#562.060 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + companyId);
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreationResult("#562.080 Error creating execContext: " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + companyId);
        }
        final ExecContextCreationResult creationResult = createExecContext(sourceCode, companyId);
        if (creationResult.isErrorMessages()) {
            return creationResult;
        }

        execContextSyncService.getWithSyncNullableForCreation(creationResult.execContext.id, () -> {
            final ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(creationResult.execContext.params);
            SourceCodeApiData.TaskProducingResultComplex result = execContextTaskProducingService.produceAndStartAllTasks(sourceCode, creationResult.execContext, execContextParamsYaml);
            if (result.sourceCodeValidationResult.status != EnumsApi.SourceCodeValidateStatus.OK) {
                creationResult.addErrorMessage(result.sourceCodeValidationResult.error);
            }
            if (result.taskProducingStatus != EnumsApi.TaskProducingStatus.OK) {
                creationResult.addErrorMessage("#562.100 Error while producing new tasks " + result.taskProducingStatus);
            }
            return null;
        });
        return creationResult;
    }

    /**
     *
     * @param sourceCode SourceCodeImpl
     * @param companyId Long companyId can be different from sourceCode.companyId
     * @return ExecContextCreationResult
     */
    public ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, Long companyId) {
        TxUtils.checkTxExists();
        sourceCodeSyncService.checkWriteLockPresent(sourceCode.id);

        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status.status != EnumsApi.SourceCodeValidateStatus.OK) {
            return new ExecContextCreationResult(sourceCodeValidation.getErrorMessagesAsList());
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        if (scpy.source.instances!=null && scpy.source.instances>0) {
            long count = execContextRepository.countInProgress();
            if (count>=scpy.source.instances) {
                return new ExecContextCreationResult(
                        S.f("#562.115 Too many instances of SourceCode %s, max allowed: %d, current count: %d",
                                sourceCode.uid, scpy.source.instances, count));
            }
        }

        AtomicLong contextId = new AtomicLong();
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                EnumsApi.SourceCodeLang.yaml, scspy.source, () -> "" + contextId.incrementAndGet());

        if (ExecContextProcessGraphService.anyError(sourceCodeGraph)) {
            return new ExecContextCreationResult("#562.120 processGraph is broken");
        }

        ExecContextImpl execContext = createExecContext(sourceCode, companyId, sourceCodeGraph);
        ExecContextCreationResult ecr = new ExecContextCreationResult();
        ecr.execContext = execContext;
        return ecr;
    }

    private ExecContextImpl createExecContext(SourceCodeImpl sourceCode, Long companyId, SourceCodeData.SourceCodeGraph sourceCodeGraph) {

        ExecContextImpl ec = new ExecContextImpl();
        ec.companyId = companyId;
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml expy = to(sourceCodeGraph);
        expy.sourceCodeUid = sourceCode.uid;
        ec.params = ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(expy);
        ec.setValid(true);

        ExecContextImpl execContext = execContextService.save(ec);
        return execContext;
    }

    private ExecContextParamsYaml to(SourceCodeData.SourceCodeGraph sourceCodeGraph) {
        ExecContextParamsYaml params = new ExecContextParamsYaml();
        params.clean = sourceCodeGraph.clean;
        params.processes.addAll(sourceCodeGraph.processes);
        params.graph = ConstsApi.EMPTY_GRAPH;
        params.processesGraph = ExecContextProcessGraphService.asString(sourceCodeGraph.processGraph);
        initVariables(sourceCodeGraph.variables, params.variables);

        return params;
    }

    private void initVariables(ExecContextParamsYaml.VariableDeclaration v1, ExecContextParamsYaml.VariableDeclaration v) {
        v.inline.putAll(v1.inline);
        v.globals = v1.globals;
        v.startInputAs = v1.startInputAs;
    }

}
