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
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final ExecContextCache execContextCache;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final SourceCodeSelectorService sourceCodeSelectorService;

    public SourceCodeApiData.ExecContextResult createExecContext(Long sourceCodeId, DispatcherContext context) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, context.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + context.getCompanyId());
        }
        return createExecContext(sourceCode);
    }

    public SourceCodeApiData.ExecContextResult createExecContext(String sourceCodeUid, DispatcherContext context) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeByUid(context.getCompanyId(), sourceCodeUid);
        if (sourceCodesForCompany.isErrorMessages()) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.getCompanyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.getCompanyId());
        }
        return createExecContext(sourceCode);
    }

    private SourceCodeApiData.ExecContextResult createExecContext(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return new SourceCodeApiData.ExecContextResult("#560.006 source code wasn't found");
        }
        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status != EnumsApi.SourceCodeValidateStatus.OK) {
            return new SourceCodeApiData.ExecContextResult(sourceCodeValidation.errorMessages);
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        AtomicLong contextId = new AtomicLong();
        if (true) {
            throw new NotImplementedException("Not yet");
        }
        SourceCodeData.SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, scspy.source, () -> "" + contextId.incrementAndGet());

        ExecContextParamsYaml.ExecContextYaml execContextYaml = new ExecContextParamsYaml.ExecContextYaml();

        // TODO 2020-02-24 add this line
        // changeValidStatus(producingResult.execContext.getId(), true);

        SourceCodeApiData.TaskProducingResultComplex execContext = createExecContext(sourceCode, execContextYaml);
        if (execContext.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.ExecContextResult(sourceCodeValidation.errorMessages);
        }
        SourceCodeApiData.ExecContextResult ecr = new SourceCodeApiData.ExecContextResult();
        ecr.execContext = execContext.execContext;
        return ecr;
    }


    private SourceCodeApiData.TaskProducingResultComplex createExecContext(SourceCodeImpl sourceCode, ExecContextParamsYaml.ExecContextYaml execContextYaml) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();

        ExecContextImpl ec = new ExecContextImpl();
        ec.setSourceCodeId(sourceCode.id);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml params = new ExecContextParamsYaml();
        params.execContextYaml = execContextYaml;
        params.graph = Consts.EMPTY_GRAPH;
        ec.updateParams(params);
        ec.setValid(true);

        result.execContext = execContextCache.save(ec);
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

}
