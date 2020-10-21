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

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 10/19/2020
 * Time: 4:20 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ExecContextCreatorTopLevelService {

    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextCreatorService execContextCreatorService;

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(Long sourceCodeId, Long companyUniqueId) {
/*
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, companyUniqueId);
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreatorService.ExecContextCreationResult("#560.072 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + companyUniqueId);
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreatorService.ExecContextCreationResult("#560.072 Error creating execContext: " +
                    "sourceCode wasn't found for Id: " + sourceCodeId+", companyId: " + companyUniqueId);
        }
        return execContextCreatorService.createExecContextAndStart(sourceCode.id, companyUniqueId);
*/
        return execContextCreatorService.createExecContextAndStart(sourceCodeId, companyUniqueId);
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(String sourceCodeUid, Long companyUniqueId) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeByUid(sourceCodeUid, companyUniqueId);
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreatorService.ExecContextCreationResult("#563.020 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + companyUniqueId);
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreatorService.ExecContextCreationResult("#563.040 Error creating execContext: " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + companyUniqueId);
        }
        try {
            return execContextCreatorService.createExecContextAndStart(sourceCode.id, companyUniqueId);
        } catch (Throwable th) {
            final String es = "#563.060 General error of creating execContext. " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid + ", companyId: " + companyUniqueId + ", error: " + th.getMessage();
            log.error(es, th);
            return new ExecContextCreatorService.ExecContextCreationResult(es);
        }
    }


}
