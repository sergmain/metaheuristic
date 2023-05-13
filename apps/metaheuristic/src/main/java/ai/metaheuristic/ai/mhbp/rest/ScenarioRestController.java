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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.scenario.ScenarioService;
import ai.metaheuristic.ai.mhbp.scenario.ScenarioTxService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 7:06 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/scenario")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ScenarioRestController {

    private final ScenarioService scenarioService;
    private final ScenarioTxService scenarioTxService;
    private final UserContextService userContextService;

    @GetMapping("/scenario-groups")
    public ScenarioData.ScenarioGroupsResult scenarioGroups(Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ScenarioData.ScenarioGroupsResult result = scenarioService.getScenarioGroups(pageable, context);
        return result;
    }

    @GetMapping("/scenarios/{scenarioGroupId}")
    public ScenarioData.ScenariosResult scenarios(Pageable pageable, @PathVariable Long scenarioGroupId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ScenarioData.ScenariosResult result = scenarioService.getScenarios(pageable, scenarioGroupId, context);
        return result;
    }

    // /dispatcher/scenario/scenarios/3/scenario/1/steps:
    @GetMapping("/scenarios/{scenarioGroupId}/scenario/{scenarioId}/steps")
    public ScenarioData.SimpleScenarioSteps scenarios(@PathVariable Long scenarioGroupId, @PathVariable Long scenarioId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ScenarioData.SimpleScenarioSteps result = scenarioService.getScenarioSteps(scenarioGroupId, scenarioId, context);
        return result;
    }

    @GetMapping(value = "/scenario-add")
    public ScenarioData.ScenarioUidsForAccount scenarioAdd(Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ScenarioData.ScenarioUidsForAccount result = scenarioService.getScenarioUidsForAccount(context);
        return result;
    }

    @GetMapping(value = "/scenario-step-add")
    public ScenarioData.ScenarioUidsForAccount scenarioStepAdd(Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ScenarioData.ScenarioUidsForAccount result = scenarioService.getScenarioUidsForAccount(context);
        return result;
    }

    @PostMapping(value = "/scenario-step-rearrange")
    public OperationStatusRest scenarioStepRearrange(
            @RequestParam(name = "scenarioId") Long scenarioId,
            @RequestParam(name = "prev") String previousUuid,
            @RequestParam(name = "curr") String currentUuid,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        if (Objects.equals(previousUuid, currentUuid)) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        OperationStatusRest result = scenarioTxService.scenarioStepRearrange(scenarioId, previousUuid, currentUuid, context);
        return result;
    }


    @PostMapping("/scenario-group-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addScenarioGroupFormCommit(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "description") String description,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.createScenarioGroup(name, description, context);
    }

    @PostMapping("/scenario-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addScenarioFormCommit(
            @RequestParam(name = "scenarioGroupId") String scenarioGroupId,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "description") String description,
            @RequestParam(name = "apiId") String apiId,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.createScenario(scenarioGroupId, name, description, apiId, context);
    }

    @PostMapping("/scenario-step-add-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest addScenarioStepFormCommit(
            @RequestParam(name = "scenarioGroupId") String scenarioGroupId,
            @RequestParam(name = "scenarioId") String scenarioId,
            @RequestParam(name = "parentUuid", required = false) String parentUuid,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "prompt") String prompt,
            @RequestParam(name = "apiId") String apiId,
            @RequestParam(name = "resultCode") String resultCode,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.createScenarioStep(scenarioGroupId, scenarioId, parentUuid, name, prompt, apiId, resultCode, context);
    }

    @PostMapping("/scenario-group-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest scenarioGroupDeleteCommit(Long scenarioGroupId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.deleteScenarioGroupById(scenarioGroupId, context);
    }

    @PostMapping("/scenario-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest scenarioDeleteCommit(Long scenarioId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.deleteScenarioById(scenarioId, context);
    }

    // /dispatcher/scenario/scenario-step-delete-commit: 404 OK
    @PostMapping("/scenario-step-delete-commit")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest scenarioDeleteCommit(Long scenarioId, String uuid, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioTxService.deleteScenarioStep(scenarioId, uuid, context);
    }

    @PostMapping("/scenario-duplicate")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest duplicateScenario(
            @RequestParam(name = "scenarioGroupId") String scenarioGroupId,
            @RequestParam(name = "scenarioId") String scenarioId,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "prompt") String prompt,
            @RequestParam(name = "apiId") String apiId,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioService.duplicateScenario(scenarioGroupId, scenarioId, name, prompt, apiId, context);
    }

    @PostMapping("/scenario-run")
//    @PreAuthorize("hasAnyRole('MASTER_ASSET_MANAGER', 'ADMIN', 'DATA')")
    public OperationStatusRest runScenario(
            @RequestParam(name = "scenarioGroupId") String scenarioGroupId,
            @RequestParam(name = "scenarioId") String scenarioId,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "prompt") String prompt,
            @RequestParam(name = "apiId") String apiId,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return scenarioService.runScenario(scenarioGroupId, scenarioId, name, prompt, apiId, context);
    }

}
