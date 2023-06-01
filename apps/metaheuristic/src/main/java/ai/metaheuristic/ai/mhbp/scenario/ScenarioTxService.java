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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 8:01 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ScenarioTxService {

    public final Globals globals;
    public final ScenarioGroupRepository scenarioGroupRepository;
    public final ScenarioRepository scenarioRepository;
    public final ApiRepository apiRepository;

    @Transactional
    public OperationStatusRest createScenarioGroup(String name, String description, DispatcherContext context) {
        ScenarioGroup s = new ScenarioGroup();
        s.name = name;
        s.description = description;
        s.companyId = context.getCompanyId();
        s.accountId = context.getAccountId();
        s.createdOn = System.currentTimeMillis();

        scenarioGroupRepository.save(s);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest createScenario(String scenarioGroupId, String name, String description, DispatcherContext context) {
        if (S.b(scenarioGroupId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.040 scenarioGroupId is null");
        }
        Scenario s = new Scenario();
        s.scenarioGroupId = Long.parseLong(scenarioGroupId);
        s.name = name;
        s.description = description;
        s.accountId = context.getAccountId();
        s.createdOn = System.currentTimeMillis();
        s.updateParams( new ScenarioParams());

        scenarioRepository.save(s);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteScenarioById(Long scenarioId, DispatcherContext context) {
        if (scenarioId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.240 scenarioId is null");
        }
        Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
        if (scenario == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.280 Scenario wasn't found, scenarioId: " + scenarioId);
        }
        if (scenario.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.320 scenarioId: " + scenarioId);
        }

        scenarioRepository.delete(scenario);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteScenarioGroupById(Long scenarioGroupId, DispatcherContext context) {
        if (scenarioGroupId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.360 scenarioGroupId is null");
        }
        ScenarioGroup scenarioGroup = scenarioGroupRepository.findById(scenarioGroupId).orElse(null);
        if (scenarioGroup == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.400 Scenario wasn't scenarioGroupId, scenarioId: " + scenarioGroupId);
        }
        if (scenarioGroup.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.440 scenarioGroupId: " + scenarioGroupId);
        }

        scenarioGroupRepository.delete(scenarioGroup);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteScenarioStep(Long scenarioId, String uuid, DispatcherContext context) {
        if (scenarioId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.440 scenarioGroupId is null");
        }
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.480 Scenario wasn't scenarioGroupId, scenarioId: " + scenarioId);
        }
        if (s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.440 scenarioId: " + scenarioId);
        }

        ScenarioParams sp = s.getScenarioParams();
        sp.steps = sp.steps.stream().filter(o->!o.uuid.equals(uuid)).toList();
        s.updateParams(sp);

        scenarioRepository.save(s);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest scenarioStepRearrange(Long scenarioId, String previousUuid, String currentUuid, DispatcherContext context) {
        if (scenarioId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.480 scenarioGroupId is null");
        }
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.520 Scenario wasn't scenarioGroupId, scenarioId: " + scenarioId);
        }
        if (s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.560 scenarioId: " + scenarioId);
        }

        if (S.b(previousUuid) || S.b(currentUuid)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.565 indexes");
        }

        ScenarioParams sp = s.getScenarioParams();

        ScenarioParams.Step prevStep = sp.steps.stream().filter(o->o.uuid.equals(previousUuid)).findFirst().orElse(null);
        if (prevStep==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.570 prev UUID");
        }
        ScenarioParams.Step currStep = sp.steps.stream().filter(o->o.uuid.equals(currentUuid)).findFirst().orElse(null);
        if (currStep==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.575 curr UUID");
        }
        sp.steps.remove(prevStep);
        prevStep.parentUuid = currStep.parentUuid;
        final int index = findIndex(sp.steps, currStep);
        if (index==-1) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.590 curr index");
        }
        sp.steps.add(index, prevStep);

        s.updateParams(sp);

        scenarioRepository.save(s);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static int findIndex(List<ScenarioParams.Step> steps, ScenarioParams.Step currStep) {
        for (int i = 0; i <steps.size(); i++) {
            if (steps.get(i).uuid.equals(currStep.uuid)) {
                return i;
            }

        }
        return -1;
    }

}
