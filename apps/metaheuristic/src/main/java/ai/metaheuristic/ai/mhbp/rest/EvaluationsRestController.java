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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.mhbp.data.EvaluationData;
import ai.metaheuristic.ai.mhbp.evaluation.EvaluationService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 7:29 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/evaluation")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EvaluationsRestController {

    private final EvaluationService evaluationService;
    private final UserContextService userContextService;

    @GetMapping("/evaluate/{apiId}")
    public OperationStatusRest evaluate(@PathVariable @Nullable Long apiId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return evaluationService.evaluate(apiId, context, 0);
    }

    @PostMapping("/run-evaluation")
    public OperationStatusRest runEvaluation(Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return evaluationService.evaluate(id, context, 0);
    }

    @PostMapping("/run-test-evaluation")
    public OperationStatusRest runTestEvaluation(Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return evaluationService.evaluate(id, context, 1);
    }

    @GetMapping("/evaluations")
    public EvaluationData.Evaluations evaluations(Pageable pageable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        final EvaluationData.Evaluations evaluations = evaluationService.getEvaluations(pageable, context);
        return evaluations;
    }

    @GetMapping(value = "/evaluation-add")
    public EvaluationData.EvaluationUidsForCompany evaluationAdd(Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        EvaluationData.EvaluationUidsForCompany result = evaluationService.getEvaluationUidsForCompany(context);
        return result;
    }

    @PostMapping("/evaluation-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addFormCommit(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "apiId") String apiId,
            @RequestParam(name = "chapterIds") String[] chapterIds, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return evaluationService.createEvaluation(code, apiId, chapterIds, context.getCompanyId(), context.getAccountId());
    }

/*
    @PostMapping("/evaluation-edit-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public SourceCodeApiData.SourceCodeResult editFormCommit(Long sourceCodeId, @RequestParam(name = "source") String sourceCodeYamlAsStr) {
        throw new IllegalStateException("Not supported any more");
    }
*/

    @PostMapping("/evaluation-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest deleteCommit(Long evaluationId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return evaluationService.deleteEvaluationById(evaluationId, context);
    }


}
