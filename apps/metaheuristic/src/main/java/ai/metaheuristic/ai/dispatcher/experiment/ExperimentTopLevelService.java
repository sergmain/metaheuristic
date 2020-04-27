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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentTopLevelService {

    private final Globals globals;
    private final ExecContextCache execContextCache;

    private final ExperimentCache experimentCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentService experimentService;
    private final ExecContextService execContextService;
    private final ExecContextCreatorService execContextCreatorService;
    private final SourceCodeRepository sourceCodeRepository;

    public static ExperimentApiData.SimpleExperiment asSimpleExperiment(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return new ExperimentApiData.SimpleExperiment(params.name, params.description, params.code, e.getId());
    }

    public ExperimentApiData.ExperimentsResult getExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        ExperimentApiData.ExperimentsResult result = new ExperimentApiData.ExperimentsResult();
        final Slice<Long> experimentIds = experimentRepository.findAllByOrderByIdDesc(pageable);

        List<ExperimentApiData.ExperimentResult> experimentResults =
                experimentIds.stream()
                        .map(experimentCache::findById)
                        .filter(Objects::nonNull).map(experimentService::asExperimentDataShort)
                        .filter(Objects::nonNull).map(ExperimentApiData.ExperimentResult::new)
                        .collect(Collectors.toList());

        result.items = new PageImpl<>(experimentResults, pageable, experimentResults.size() + (experimentIds.hasNext() ? 1 : 0) );
        return result;
    }

    public OperationStatusRest changeExecContextState(String state, Long experimentId, DispatcherContext context) {
        Experiment e = experimentCache.findById(experimentId);
        if (e==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.007 experiment wasn't found, experimentId: " + experimentId);
        }
        OperationStatusRest operationStatusRest = execContextService.changeExecContextState(state, e.execContextId, context);
        return operationStatusRest;
    }

    public ExperimentApiData.ExperimentResult getExperimentWithoutProcessing(Long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentResult("#285.010 experiment wasn't found, experimentId: " + experimentId );
        }
        ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experiment.getParams());

        // hide the current processing info
        epy.processing = new ExperimentParamsYaml.ExperimentProcessing();
/*
        String params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.toString(epy);
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment), params);
*/
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment));
    }

    public ExperimentApiData.ExperimentsEditResult editExperiment(@PathVariable Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentsEditResult("#285.100 experiment wasn't found, experimentId: " + id);
        }
/*
        Iterable<Function> functions = functionRepository.findAll();
        ExperimentApiData.FunctionResult functionResult = new ExperimentApiData.FunctionResult();

        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        final List<String> functionCodes = epy.getFunctionCodes();
        List<Function> experimentFunctions = functionService.getFunctionsForCodes(functionCodes);

        functionResult.functions = experimentFunctions.stream().map(es->
                new ExperimentApiData.ExperimentFunctionResult(
                        es.getId(), es.getVersion(), es.getCode(), es.type, experiment.id)).collect(Collectors.toList());

        functionResult.selectOptions = functionService.getSelectOptions(
                functions,
                functionResult.functions.stream().map(o -> new FunctionData.FunctionCode(o.getId(), o.getFunctionCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!experimentFunctionTypes.contains(s.type) ) {
                        return true;
                    }
                    if (CommonConsts.FIT_TYPE.equals(s.type) && functionService.hasType(experimentFunctions, CommonConsts.FIT_TYPE)) {
                        return true;
                    }
                    else if (CommonConsts.PREDICT_TYPE.equals(s.type) && functionService.hasType(experimentFunctions, CommonConsts.PREDICT_TYPE)) {
                        return true;
                    }
                    else if (CommonConsts.CHECK_FITTING_TYPE.equals(s.type)) {
                        if (functionService.hasType(experimentFunctions, CommonConsts.CHECK_FITTING_TYPE)) {
                            return true;
                        }
                        for (Function function : experimentFunctions) {
                            if (CommonConsts.PREDICT_TYPE.equals(function.getType())) {
                                final Meta meta = MetaUtils.getMeta(function.getFunctionConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
                                if (MetaUtils.isTrue(meta)) {
                                    // don't include this Function because 'predict' function doesn't support fitting detection
                                    return false;
                                }
                            }
                        }
                        return true;
                    } else {
                        return false;
                    }
                });

        functionResult.sortFunctionsByOrder();
*/

        ExperimentApiData.ExperimentsEditResult result = new ExperimentApiData.ExperimentsEditResult();

/*
        ExperimentApiData.HyperParamsResult r = new ExperimentApiData.HyperParamsResult();
        r.items = epy.experimentYaml.getHyperParams().stream().map(ExperimentTopLevelService::asHyperParamData).collect(Collectors.toList());
        result.hyperParams = r;
        result.functionResult = functionResult;
*/
        result.simpleExperiment = asSimpleExperiment(experiment);
        return result;
    }

    public OperationStatusRest updateParamsAndSave(Experiment e, ExperimentParamsYaml params, String name, String description) {
        params.name = StringUtils.strip(name);
        params.code = e.code;
        params.description = StringUtils.strip(description);
        params.createdOn = System.currentTimeMillis();

        e.updateParams(params);

        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest addExperimentCommit(String sourceCodeUid, String name, String code, String description, DispatcherContext context) {
        SourceCodeImpl sc = sourceCodeRepository.findByUid(sourceCodeUid);
        if (sc==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.110 SourceCode wasn't found, sourceCodeUid: " + sourceCodeUid);
        }
        ExecContextCreatorService.ExecContextCreationResult execContextResultRest = execContextCreatorService.createExecContext(sourceCodeUid, context);
        if (execContextResultRest.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, execContextResultRest.getErrorMessagesAsList());
        }

        Experiment e = new Experiment();
        e.code = StringUtils.strip(code);
        e.execContextId = execContextResultRest.execContext.id;

        ExperimentParamsYaml params = new ExperimentParamsYaml();
        return updateParamsAndSave(e, params, name, description);
    }

    public OperationStatusRest editExperimentCommit(ExperimentApiData.SimpleExperiment simpleExperiment) {
        OperationStatusRest op = validate(simpleExperiment);
        if (op.status!= EnumsApi.OperationStatus.OK) {
            return op;
        }

        Experiment e = experimentRepository.findByIdForUpdate(simpleExperiment.id);
        if (e == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.110 experiment wasn't found, experimentId: " + simpleExperiment.id);
        }
        e.code = StringUtils.strip(simpleExperiment.getCode());

        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return updateParamsAndSave(e, params, simpleExperiment.getName(), simpleExperiment.getDescription());
    }

    private OperationStatusRest validate(ExperimentApiData.SimpleExperiment se) {
        if (StringUtils.isBlank(se.getName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.120 Name of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getCode())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.130 Code of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getDescription())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.140 Description of experiment is blank.");
        }
        return new OperationStatusRest(EnumsApi.OperationStatus.OK);
    }

    public OperationStatusRest toExperimentResult(Long id) {

        Experiment experiment = experimentCache.findById(id);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.410 can't find experiment for id: " + id);
        }

        if (experiment.execContextId ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.420 This experiment isn't bound to ExecContext");
        }
        return  new OperationStatusRest(EnumsApi.OperationStatus.OK, "Exporting of experiment was successfully started", "");
    }

    public OperationStatusRest produceTasks(String experimentCode, Long companyUniqueId) {
        return changeExecStateTo(experimentCode, EnumsApi.ExecContextState.PRODUCING, companyUniqueId);
    }

    public EnumsApi.ExecContextState getExperimentProcessingStatus(String experimentCode) {
        if (experimentCode==null || experimentCode.isBlank()) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null || experiment.execContextId ==null) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        ExecContext ec = execContextCache.findById(experiment.execContextId);
        if (ec==null) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        return EnumsApi.ExecContextState.toState(ec.getState());
    }

    public OperationStatusRest startProcessingOfTasks(String experimentCode, Long companyUniqueId) {
        return changeExecStateTo(experimentCode, EnumsApi.ExecContextState.STARTED, companyUniqueId);
    }

    private OperationStatusRest changeExecStateTo(String experimentCode, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        if (experimentCode==null || experimentCode.isBlank()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.550 experiment code is blank");
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.560 can't find an experiment for code: " + experimentCode);
        }
        OperationStatusRest status = execContextService.execContextTargetState(experiment.execContextId, execState, companyUniqueId);
        if (status.isErrorMessages()) {
            return status;
        }
        return  new OperationStatusRest(EnumsApi.OperationStatus.OK,
                "State of experiment '"+experimentCode+"' was successfully changed to " + execState, "");
    }


    public OperationStatusRest experimentDeleteCommit(Long id) {
        experimentCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentCloneCommit(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.270 An experiment wasn't found, experimentId: " + id);
        }
        // do not use experiment.getExperimentParamsYaml() because it's  caching ExperimentParamsYaml
        ExperimentParamsYaml epy = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(experiment.getParams());
        epy.processing = new ExperimentParamsYaml.ExperimentProcessing();
        epy.createdOn = System.currentTimeMillis();

        final Experiment e = new Experiment();
        String newCode = StrUtils.incCopyNumber(experiment.getCode());
        int i = 0;
        while ( experimentRepository.findIdByCode(newCode)!=null  ) {
            newCode = StrUtils.incCopyNumber(newCode);
            if (i++>100) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.273 Can't find a new code for experiment with the code: " + experiment.getCode());
            }
        }
        epy.code = newCode;
        e.code = newCode;
        e.updateParams(epy);
        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
