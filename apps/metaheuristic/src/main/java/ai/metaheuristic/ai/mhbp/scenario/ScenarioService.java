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

package ai.metaheuristic.ai.mhbp.scenario;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ai.metaheuristic.ai.mhbp.data.SimpleScenario;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.utils.CollectionUtils.*;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 7:07 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ScenarioService {

    private final ApiService apiService;
    private final ApiRepository apiRepository;
    private final ScenarioGroupRepository scenarioGroupRepository;
    private final ScenarioRepository scenarioRepository;

    public ScenarioData.ScenarioGroupsResult getScenarioGroups(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(10, pageable);

        Page<ScenarioGroup> scenarioGroups = scenarioGroupRepository.findAllByAccountId(pageable, context.getAccountId());
        List<ScenarioData.SimpleScenarioGroup> list = scenarioGroups.stream().map(ScenarioData.SimpleScenarioGroup::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.scenarioGroupId, o1.scenarioGroupId)).collect(Collectors.toList());
        return new ScenarioData.ScenarioGroupsResult(new PageImpl<>(sorted, pageable, list.size()));
    }

    public ScenarioData.ScenariosResult getScenarios(Pageable pageable, Long scenarioGroupId, DispatcherContext context) {
        if (scenarioGroupId==null) {
            return new ScenarioData.ScenariosResult(Page.empty(pageable));
        }
        pageable = PageUtils.fixPageSize(10, pageable);
        Page<SimpleScenario> scenarios = scenarioRepository.findAllByScenarioGroupId(pageable, scenarioGroupId, context.getAccountId());

        return new ScenarioData.ScenariosResult(scenarios);
    }

    public ScenarioData.ScenarioUidsForAccount getScenarioUidsForAccount(DispatcherContext context) {
        ScenarioData.ScenarioUidsForAccount r = new ScenarioData.ScenarioUidsForAccount();
        r.apis = apiService.getApisAllowedForCompany(context).stream()
                .map(o ->new ScenarioData.ApiUid(o.id, o.code))
                .toList();
        r.functions = InternalFunctionRegisterService.internalFunctionMap.entrySet().stream()
                .filter(e->e.getValue().isScenarioCompatible())
                .map(e->new ScenarioData.InternalFunction(e.getKey(), e.getValue().getClass().getSimpleName())).collect(Collectors.toList());
        return r;
    }

    public ScenarioData.SimpleScenarioSteps getScenarioSteps(Long scenarioGroupId, Long scenarioId, DispatcherContext context) {
        if (scenarioGroupId==null || scenarioId==null) {
            return new ScenarioData.SimpleScenarioSteps(List.of());
        }
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s==null || s.scenarioGroupId!=scenarioGroupId || s.accountId!=context.getAccountId()) {
            return new ScenarioData.SimpleScenarioSteps(List.of());
        }
        ScenarioParams scenarioParams = s.getScenarioParams();

        Map<Long, ScenarioData.ApiUid> apis = new HashMap<>();
        List<ScenarioData.SimpleScenarioStep> steps = scenarioParams.steps.stream()
                .map(o-> {
                    ScenarioData.ApiUid apiUid = new ScenarioData.ApiUid(0L, "<broken API Id>");
                    String functionCode = null;
                        if (o.api!=null) {
                            apiUid = apis.computeIfAbsent(o.api.apiId,
                                    (apiId) -> apiRepository.findById(apiId)
                                            .map(api -> new ScenarioData.ApiUid(api.id, api.code))
                                            .orElse(new ScenarioData.ApiUid(0L, "<broken API Id>")));
                        }
                        else if (o.function!=null) {
                            functionCode = o.function.code;
                        }

                    return new ScenarioData.SimpleScenarioStep(s.id, apiUid, o, functionCode);
                })
                .toList();

        TreeUtils<String> treeUtils = new CollectionUtils.TreeUtils<>();
        List<ScenarioData.SimpleScenarioStep> stepTree = treeUtils.rebuildTree((List)steps);

        return new ScenarioData.SimpleScenarioSteps(stepTree);
    }

    public OperationStatusRest runScenario(String scenarioGroupId, String scenarioId, DispatcherContext context) {

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest duplicateScenario(String scenarioGroupId, String scenarioId, String name, String prompt, String apiId, DispatcherContext context) {

        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
