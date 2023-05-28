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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Scenario;
import ai.metaheuristic.ai.mhbp.beans.ScenarioGroup;
import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ai.metaheuristic.ai.mhbp.data.SimpleScenario;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioGroupRepository;
import ai.metaheuristic.ai.mhbp.repositories.ScenarioRepository;
import ai.metaheuristic.ai.mhbp.yaml.scenario.ScenarioParams;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.PageUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.utils.CollectionUtils.TreeUtils;

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

    private final Globals globals;
    private final ApiService apiService;
    private final ApiRepository apiRepository;
    private final ScenarioGroupRepository scenarioGroupRepository;
    private final ScenarioRepository scenarioRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final SourceCodeService sourceCodeService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;

    public ScenarioData.ScenarioGroupsResult getScenarioGroups(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(10, pageable);

        Page<ScenarioGroup> scenarioGroups = scenarioGroupRepository.findAllByAccountId(pageable, context.getAccountId());
        List<ScenarioData.SimpleScenarioGroup> list = scenarioGroups.stream().map(ScenarioData.SimpleScenarioGroup::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.scenarioGroupId, o1.scenarioGroupId)).collect(Collectors.toList());
        return new ScenarioData.ScenarioGroupsResult(new PageImpl<>(sorted, pageable, list.size()));
    }

    public ScenarioData.ScenariosResult getScenarios(Pageable pageable, @Nullable Long scenarioGroupId, DispatcherContext context) {
        if (scenarioGroupId==null) {
            return new ScenarioData.ScenariosResult(Page.empty(pageable));
        }
        pageable = PageUtils.fixPageSize(globals.dispatcher.rowsLimit.defaultLimit, pageable);
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
        r.aggregateTypes = Stream.of(AggregateFunction.AggregateType.values()).filter(o->o.supported).map(Enum::toString).toList();
        return r;
    }

    public ScenarioData.SimpleScenarioSteps getScenarioSteps(long scenarioGroupId, long scenarioId, DispatcherContext context) {
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s==null || s.scenarioGroupId!=scenarioGroupId || s.accountId!=context.getAccountId()) {
            return new ScenarioData.SimpleScenarioSteps(null, List.of());
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
                        if (o.function!=null) {
                            functionCode = o.function.code;
                        }

                    return new ScenarioData.SimpleScenarioStep(s.id, apiUid, o, functionCode);
                })
                .toList();

        TreeUtils<String> treeUtils = new CollectionUtils.TreeUtils<>();
        List<ScenarioData.SimpleScenarioStep> stepTree = treeUtils.rebuildTree((List)steps);

        return new ScenarioData.SimpleScenarioSteps(new ScenarioData.SimpleScenarioInfo(s.name, s.description), stepTree);
    }

    public OperationStatusRest runScenario(long scenarioGroupId, long scenarioId, DispatcherContext context) {
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s==null || s.scenarioGroupId!=scenarioGroupId || s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#373.120 scenario wasn't found, " + scenarioGroupId+", " + scenarioId);
        }

        String uid = ScenarioUtils.getUid(s);
        SourceCodeImpl sc = sourceCodeRepository.findByUid(uid);
        if (sc==null) {
            SourceCodeParamsYaml scpy = ScenarioUtils.to(uid, s.getScenarioParams());
            String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(scpy);
            SourceCodeApiData.SourceCodeResult result = sourceCodeService.createSourceCode(yaml, scpy, context.getCompanyId());
            if (!result.isValid()) {
                final String es = S.f("#373.160 validation: %s, %s", result.validationResult.status, result.validationResult.error);
                log.error(es);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            sc = sourceCodeRepository.findById(result.id).orElse(null);
            if (sc==null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, S.f("#373.180 SourceCode not found: %d", result.id));
            }
        }
        ExecContextCreatorService.ExecContextCreationResult execContextResult = execContextCreatorTopLevelService.createExecContextAndStart(sc.id, context.getCompanyId(), true);
        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(execContextResult.sourceCode, execContextResult.execContext);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest copyScenario(String scenarioGroupId, String scenarioId, DispatcherContext context) {
        Scenario s = scenarioRepository.findById(Long.parseLong(scenarioId)).orElse(null);
        if (s==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.240 Scenario # " + scenarioId+" wasn't found");
        }
        if (s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.280 accountId");
        }
        if (s.scenarioGroupId!=Long.parseLong(scenarioGroupId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.320 scenarioGroupId");
        }
        //noinspection DataFlowIssue
        s.id = null;
        //noinspection DataFlowIssue
        s.version = null;
        s.name = StrUtils.incCopyNumber(s.name);
        s.createdOn = System.currentTimeMillis();
        scenarioRepository.save(s);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest updateScenarioInfo(long scenarioGroupId, long scenarioId, String name, String description, DispatcherContext context) {
        Scenario s = scenarioRepository.findById(scenarioId).orElse(null);
        if (s==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.240 Scenario # " + scenarioId+" wasn't found");
        }
        if (s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.280 accountId");
        }
        if (s.scenarioGroupId!=scenarioGroupId) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.320 scenarioGroupId");
        }
        s.name = name;
        s.description = description;
        scenarioRepository.save(s);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest createOrChangeScenarioStep(
            String scenarioGroupId, String scenarioId, String uuid, String parentUuid, String name, String prompt,
            String apiId, String resultCode, String expected, String functionCode, String aggregateType,
            DispatcherContext context) {

        if (S.b(scenarioGroupId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.120 scenarioGroupId is null");
        }
        if (S.b(scenarioId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.160 scenarioId is null");
        }
        if (S.b(apiId) && S.b(functionCode)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.200 apiId is null");
        }

        Scenario s = scenarioRepository.findById(Long.parseLong(scenarioId)).orElse(null);
        if (s==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.240 Scenario # " + scenarioId+" wasn't found");
        }
        if (s.accountId!=context.getAccountId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.280 accountId");
        }
        if (s.scenarioGroupId!=Long.parseLong(scenarioGroupId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.320 scenarioGroupId");
        }
        ScenarioParams sp = s.getScenarioParams();

        ScenarioParams.Step step;
        if (S.b(uuid)) {
            step = new ScenarioParams.Step(UUID.randomUUID().toString(), parentUuid, name, prompt, null, resultCode, expected, null, null, null);
            sp.steps.add(step);
        }
        else {
            step = sp.steps.stream().filter(o->o.uuid.equals(uuid)).findFirst().orElse(null);
            if (step==null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"229.360 broken uuid");
            }
            step.name = name;
            step.p = prompt;
            step.resultCode = resultCode;
            step.expected = expected;
        }

        if (S.b(functionCode)) {
            Api api = apiRepository.findById(Long.parseLong(apiId)).orElse(null);
            if (api==null || api.companyId!=context.getCompanyId()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.420 apiId");
            }
            step.api = new ScenarioParams.Api(api.id, api.code);
        }
        else {
            if (S.b(prompt)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.440 prompt");
            }
            step.api = null;
            step.function = new ScenarioParams.Function(functionCode, EnumsApi.FunctionExecContext.internal);
            if (Consts.MH_ACCEPTANCE_TEST_FUNCTION.equals(functionCode)) {
                Api api = apiRepository.findById(Long.parseLong(apiId)).orElse(null);
                if (api==null || api.companyId!=context.getCompanyId()) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.480 apiId is null");
                }
                step.api = new ScenarioParams.Api(api.id, api.code);
            }
            else if (Consts.MH_AGGREGATE_FUNCTION.equals(functionCode)) {
                if (S.b(aggregateType)) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.520 aggregateType");
                }
                step.aggregateType = AggregateFunction.AggregateType.valueOf(aggregateType);
            }
        }

        s.updateParams(sp);

        scenarioRepository.save(s);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
