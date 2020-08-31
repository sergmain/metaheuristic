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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

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
    private final ApplicationEventPublisher eventPublisher;

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
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment));
    }

    public ExperimentApiData.ExperimentsEditResult editExperiment(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentsEditResult("#285.100 experiment wasn't found, experimentId: " + id);
        }
        ExperimentApiData.ExperimentsEditResult result = new ExperimentApiData.ExperimentsEditResult();
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
            eventPublisher.publishEvent(new DispatcherCacheRemoveSourceCodeEvent(sourceCodeUid));
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.110 SourceCode wasn't found, sourceCodeUid: " + sourceCodeUid+". Try to refresh page");
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
        if (S.b(experimentCode)) {
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
        if (S.b(experimentCode)) {
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

    public OperationStatusRest experimentCloneCommit(Long id, DispatcherContext context) {
        final Experiment e = experimentCache.findById(id);
        if (e == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.270 An experiment wasn't found, experimentId: " + id);
        }
        ExecContextImpl ec = execContextCache.findById(e.execContextId);
        if (ec==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.290 An associated execContext for experimentId #" + id +" wasn't found");
        }
        String sourceCodeUid = ec.getExecContextParamsYaml().sourceCodeUid;

        String newCode = StrUtils.incCopyNumber(e.getCode());
        int i = 0;
        while ( experimentRepository.findIdByCode(newCode)!=null  ) {
            newCode = StrUtils.incCopyNumber(newCode);
            if (i++>100) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.273 Can't find a new code for experiment with the code: " + e.getCode());
            }
        }

        OperationStatusRest status = addExperimentCommit(sourceCodeUid, e.getExperimentParamsYaml().name, newCode, e.getExperimentParamsYaml().description, context);
        return status;
    }
}
