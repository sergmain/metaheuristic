/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/19/2020
 * Time: 4:20 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextCreatorTopLevelService {

    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextCreatorService execContextCreatorService;

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(String sourceCodeUid, ExecContextData.UserExecContext context) {
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeByUid(sourceCodeUid, context.companyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new ExecContextCreatorService.ExecContextCreationResult("563.020 Error creating execContext: "+sourceCodesForCompany.getErrorMessagesAsStr()+ ", " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.companyId());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new ExecContextCreatorService.ExecContextCreationResult("563.040 Error creating execContext: " +
                    "sourceCode wasn't found for UID: " + sourceCodeUid+", companyId: " + context.companyId());
        }
        return createExecContextAndStart(sourceCode.id, context, true, null);
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(
            Long sourceCodeId, ExecContextData.UserExecContext context, boolean isProduceTasks, @Nullable ExecContextData.RootAndParent rootAndParent) {
        final ExecContextCreatorService.ExecContextCreationResult withSyncForCreation = SourceCodeSyncService.getWithSyncForCreation(sourceCodeId,
            () -> {
                try {
                    ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContextAndStart(
                        sourceCodeId, context, isProduceTasks, rootAndParent);
                    return result;
                } catch (CommonRollbackException e) {
                    return new ExecContextCreatorService.ExecContextCreationResult(e.messages);
                } catch (ExecContextTooManyInstancesException e) {
                    String es = S.f("563.105 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
                    log.warn(es);
                    ExecContextCreatorService.ExecContextCreationResult result = new ExecContextCreatorService.ExecContextCreationResult(es);
                    result.addInfoMessage(es);
                    return result;
                } catch (Throwable th) {
                    String es = "563.110 Error adding new execContext: " + th.getMessage();
                    log.error(es, th);
                    final ExecContextCreatorService.ExecContextCreationResult r = new ExecContextCreatorService.ExecContextCreationResult(es);
                    return r;
                }
            });
        return withSyncForCreation;
    }


}
