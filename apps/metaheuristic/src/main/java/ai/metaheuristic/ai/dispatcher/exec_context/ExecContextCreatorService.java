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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 10:48 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextCreatorService {

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

    private final ExecContextCache execContextCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final SourceCodeSelectorService sourceCodeSelectorService;

    public ExecContextCreationResult createExecContext(Long sourceCodeId, DispatcherContext context) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, context.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreationResult("#560.072 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreationResult("#560.072 Error creating execContext: " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        return createExecContext(sourceCode);
    }

    public ExecContextCreationResult createExecContext(String sourceCodeUid, DispatcherContext context) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeByUid(context.getCompanyId(), sourceCodeUid);
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreationResult("#560.072 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.getCompanyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreationResult("#560.072 Error creating execContext: " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.getCompanyId());
        }
        return createExecContext(sourceCode);
    }

    public ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return new ExecContextCreationResult("#560.006 source code wasn't found");
        }
        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status != EnumsApi.SourceCodeValidateStatus.OK) {
            return new ExecContextCreationResult(sourceCodeValidation.errorMessages);
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        AtomicLong contextId = new AtomicLong();
        ExecContextCreationResult ecr = new ExecContextCreationResult();
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                EnumsApi.SourceCodeLang.yaml, scspy.source, () -> "" + contextId.incrementAndGet());

        // TODO 2020-02-24 add this line
        // changeValidStatus(producingResult.execContext.getId(), true);

        //noinspection UnnecessaryLocalVariable
        ExecContextImpl execContext = createExecContext(sourceCode, sourceCodeGraph);
        ecr.execContext = execContext;
        return ecr;
    }

    private ExecContextImpl createExecContext(SourceCodeImpl sourceCode, SourceCodeData.SourceCodeGraph sourceCodeGraph) {

        ExecContextImpl ec = new ExecContextImpl();
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ec.updateParams(to(sourceCodeGraph));
        ec.setValid(true);

        //noinspection UnnecessaryLocalVariable
        ExecContextImpl execContext = execContextCache.save(ec);
        return execContext;
    }

    private ExecContextParamsYaml to(SourceCodeData.SourceCodeGraph sourceCodeGraph) {
        ExecContextParamsYaml params = new ExecContextParamsYaml();
        params.clean = sourceCodeGraph.clean;
        params.preservePoolNames = sourceCodeGraph.preservePoolNames;
        params.processes = sourceCodeGraph.processes;
        params.variables = sourceCodeGraph.variables;

        if (true) {
            throw new NotImplementedException("add here Export of sourceCodeGraph.processGraph to String;");
        }
        params.processesGraph = "";
        params.graph = Consts.EMPTY_GRAPH;

        return params;
    }
}
