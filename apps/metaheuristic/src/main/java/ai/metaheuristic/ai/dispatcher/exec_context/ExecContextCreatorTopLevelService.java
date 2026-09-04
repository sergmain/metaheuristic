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

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
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
    private final ExecContextGitSourceService execContextGitSourceService;

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(
        String sourceCodeUid, ExecContextApiData.UserExecContext context, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo) {

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
        return createExecContextAndStart(sourceCode.id, context, true, null, execContextCreationInfo);
    }

    public ExecContextCreatorService.ExecContextCreationResult createExecContextAndStart(
            Long sourceCodeId, ExecContextApiData.UserExecContext context, boolean isProduceTasks,
            ExecContextData.@Nullable RootAndParent rootAndParent, ExecContextData.@Nullable ExecContextCreationInfo  execContextCreationInfo) {
        final ExecContextCreatorService.ExecContextCreationResult withSyncForCreation = SourceCodeSyncService.getWithSyncForCreation(sourceCodeId,
            () -> {
                try {
                    // Resolved HERE, in the non-transactional orchestrator, and handed to the tx method as a
                    // parameter: `git ls-remote` is a network call, and SPRING-TX-RULES.md 1 requires context
                    // data to be read outside the transaction and passed in with the smallest possible scope.
                    // The SourceCode is parsed twice as a result - that duplication is the accepted cost of
                    // transactional purity (SPRING-TX-RULES.md 1, "purity > fewer lines").
                    final ExecContextParamsYaml.GitSources gitSources = resolveGitSources(sourceCodeId, context);

                    ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContextAndStart(
                        sourceCodeId, context, isProduceTasks, rootAndParent, execContextCreationInfo, gitSources);
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

    /**
     * Pins every git-sourced external Function in the DAG to a concrete revision, before any transaction
     * is opened.
     *
     * <p>Returns null when the SourceCode can't be read - the tx method re-reads it and produces the
     * proper "sourceCode wasn't found" error, so failing here would only replace a good message with a
     * worse one.
     */
    private ExecContextParamsYaml.@Nullable GitSources resolveGitSources(Long sourceCodeId, ExecContextApiData.UserExecContext context) {
        final SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, context.companyId());
        if (sourceCodesForCompany.isErrorMessages() || sourceCodesForCompany.items.isEmpty()) {
            return null;
        }
        final SourceCodeImpl sourceCode = (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        final SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        final SourceCodeGraph scg = SourceCodeGraphFactory.parse(scspy.lang, scspy.source);
        return execContextGitSourceService.resolveGitSources(scg.processes, scg.groups);
    }

}
