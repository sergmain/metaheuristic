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

package ai.metaheuristic.ai.mhbp.scenario;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 5/4/2023
 * Time: 8:01 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ScenarioTxService {

    private final ScenarioGroupRepository scenarioGroupRepository;
    private final ScenarioRepository scenarioRepository;

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

    public record FindScenario(Scenario scenario, OperationStatusRest status) {}

    @SuppressWarnings({"ConstantValue", "DataFlowIssue"})
    private FindScenario findScenario(Long scenarioId, DispatcherContext context) {
        if (scenarioId==null) {
            return new FindScenario(null, new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.240 scenarioId is null"));
        }
        Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
        if (scenario == null) {
            return new FindScenario(null, new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.280 Scenario wasn't found, scenarioId: " + scenarioId));
        }
        if (scenario.accountId!=context.getAccountId()) {
            return new FindScenario(null, new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.320 scenarioId: " + scenarioId));
        }
        return new FindScenario(scenario, OperationStatusRest.OPERATION_STATUS_OK);
    }

    @Transactional
    public OperationStatusRest moveScenarioToNewGroup(long scenarioGroupId, long scenarioId, long newScenarioGroupId, DispatcherContext context) {
        FindScenario findScenario = findScenario(scenarioId, context);
        if (findScenario.status.status!=EnumsApi.OperationStatus.OK) {
            return findScenario.status;
        }
        if (findScenario.scenario.scenarioGroupId!=scenarioGroupId) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.340 Scenario wasn't found, scenarioId: " + scenarioId);
        }
        if (findScenario.scenario.scenarioGroupId==newScenarioGroupId) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }

        Long actualNewGroupId = scenarioRepository.findScenarioGroup(newScenarioGroupId, context.getAccountId());
        if (actualNewGroupId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "229.345 Target ScenarioGroup wasn't found, newScenarioGroupId: " + newScenarioGroupId);
        }
        findScenario.scenario.scenarioGroupId = actualNewGroupId;

        scenarioRepository.save(findScenario.scenario);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteScenarioById(Long scenarioId, DispatcherContext context) {
        FindScenario findScenario = findScenario(scenarioId, context);
        if (findScenario.status.status!=EnumsApi.OperationStatus.OK) {
            return findScenario.status;
        }

        scenarioRepository.delete(findScenario.scenario);
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
    public OperationStatusRest acceptNewPromptForStep(long scenarioId, String uuid, String newPrompt, DispatcherContext context) {
        FindScenario findScenario = findScenario(scenarioId, context);
        if (findScenario.status.status!=EnumsApi.OperationStatus.OK) {
            return findScenario.status;
        }

        ScenarioParams sp = findScenario.scenario.getScenarioParams();
        ScenarioParams.Step step = sp.steps.stream().filter(o->o.uuid.equals(uuid)).findFirst().orElse(null);
        if (step==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.570 UUID is broken");
        }
        step.p = newPrompt;
        findScenario.scenario.updateParams(sp);

        scenarioRepository.save(findScenario.scenario);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteScenarioStep(Long scenarioId, String uuid, DispatcherContext context) {
        FindScenario findScenario = findScenario(scenarioId, context);
        if (findScenario.status.status!=EnumsApi.OperationStatus.OK) {
            return findScenario.status;
        }

        ScenarioParams sp = findScenario.scenario.getScenarioParams();
        sp.steps = sp.steps.stream().filter(o->!o.uuid.equals(uuid)).toList();
        findScenario.scenario.updateParams(sp);

        scenarioRepository.save(findScenario.scenario);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest scenarioStepRearrange(Long scenarioId, String previousUuid, String currentUuid, DispatcherContext context) {
        FindScenario findScenario = findScenario(scenarioId, context);
        if (findScenario.status.status!=EnumsApi.OperationStatus.OK) {
            return findScenario.status;
        }

        if (S.b(previousUuid) || S.b(currentUuid)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "239.565 indexes");
        }

        ScenarioParams sp = findScenario.scenario.getScenarioParams();

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

        findScenario.scenario.updateParams(sp);

        scenarioRepository.save(findScenario.scenario);
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
