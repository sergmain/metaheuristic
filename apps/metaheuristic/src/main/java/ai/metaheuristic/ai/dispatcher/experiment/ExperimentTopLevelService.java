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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.event.events.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.utils.PageUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExperimentTopLevelService {

    private final Globals globals;
    private final ExecContextCache execContextCache;

    private final ExperimentCache experimentCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentService experimentService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final SourceCodeRepository sourceCodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void deleteExperimentByExecContextId(ProcessDeletedExecContextEvent event) {
        try {
            experimentService.deleteExperimentByExecContextId(event.execContextId);
        } catch (Throwable th) {
            log.error("#285.020 Error, need to investigate ", th);
        }
    }

    public static ExperimentApiData.SimpleExperiment asSimpleExperiment(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();
        return new ExperimentApiData.SimpleExperiment(params.name, params.description, params.code, e.getId());
    }

    public ExperimentApiData.ExperimentsResult getExperiments(Pageable pageable) {
        pageable = PageUtils.fixPageSize(globals.dispatcher.rowsLimit.experiment, pageable);
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

    public OperationStatusRest changeExecContextState(String state, Long experimentId, UserContext context) {
        Experiment e = experimentCache.findById(experimentId);
        if (e==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.040 experiment wasn't found, experimentId: " + experimentId);
        }
        OperationStatusRest operationStatusRest = execContextTopLevelService.changeExecContextState(state, e.execContextId, context);
        return operationStatusRest;
    }

    public ExperimentApiData.ExperimentResult getExperimentWithoutProcessing(Long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentResult("#285.060 experiment wasn't found, experimentId: " + experimentId );
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

    public OperationStatusRest addExperimentCommit(String sourceCodeUid, String name, String code, String description, ExecContextData.UserExecContext context) {
        SourceCodeImpl sc = sourceCodeRepository.findByUid(sourceCodeUid);
        if (sc==null) {
            eventPublisher.publishEvent(new DispatcherCacheRemoveSourceCodeEvent(sourceCodeUid));
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.110 SourceCode wasn't found, sourceCodeUid: " + sourceCodeUid+". Try to refresh page");
        }
        ExecContextCreatorService.ExecContextCreationResult execContextResultRest = execContextCreatorTopLevelService.createExecContextAndStart(sourceCodeUid, context);
        if (execContextResultRest.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, execContextResultRest.getErrorMessagesAsList());
        }

        return experimentService.addExperimentCommit(execContextResultRest.execContext.id, name, code, description);
    }

    /**
     * this method is for using in command-line
     *
     * @param experimentCode
     * @return
     */
    public EnumsApi.ExecContextState getExperimentProcessingStatus(String experimentCode) {
        if (S.b(experimentCode)) {
            return EnumsApi.ExecContextState.UNKNOWN;
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null) {
            return EnumsApi.ExecContextState.ERROR;
        }
        ExecContext ec = execContextCache.findById(experiment.execContextId, true);
        if (ec==null) {
            return EnumsApi.ExecContextState.DOESNT_EXIST;
        }
        return EnumsApi.ExecContextState.toState(ec.getState());
    }

    /**
     * this method is for using in command-line
     *
     * @param experimentCode
     * @param companyUniqueId
     * @return
     */
    public OperationStatusRest startProcessingOfTasks(String experimentCode, Long companyUniqueId) {
        return changeExecStateTo(experimentCode, EnumsApi.ExecContextState.STARTED, companyUniqueId);
    }

    private OperationStatusRest changeExecStateTo(String experimentCode, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        if (S.b(experimentCode)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.120 experiment code is blank");
        }
        Experiment experiment = experimentRepository.findByCode(experimentCode);
        if (experiment==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.140 can't find an experiment for code: " + experimentCode);
        }
        OperationStatusRest status = execContextTopLevelService.execContextTargetState(experiment.execContextId, execState, companyUniqueId);
        if (status.isErrorMessages()) {
            return status;
        }
        return  new OperationStatusRest(EnumsApi.OperationStatus.OK,
                "State of experiment '"+experimentCode+"' was successfully changed to " + execState, "");
    }


    public OperationStatusRest experimentDeleteCommit(Long id, UserContext context) {
        return experimentService.deleteExperiment(id, context);
    }

    public OperationStatusRest experimentCloneCommit(Long id, ExecContextData.UserExecContext context) {
        final Experiment e = experimentCache.findById(id);
        if (e == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.260 An experiment wasn't found, experimentId: " + id);
        }
        ExecContextImpl ec = execContextCache.findById(e.execContextId, true);
        if (ec==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.280 An associated execContext for experimentId #" + id +" wasn't found");
        }
        String sourceCodeUid = ec.getExecContextParamsYaml().sourceCodeUid;

        String newCode = StrUtils.incCopyNumber(e.getCode());
        int i = 0;
        while ( experimentRepository.findIdByCode(newCode)!=null  ) {
            newCode = StrUtils.incCopyNumber(newCode);
            if (i++>100) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#285.300 Can't find a new code for experiment with the code: " + e.getCode());
            }
        }

        OperationStatusRest status = addExperimentCommit(sourceCodeUid, e.getExperimentParamsYaml().name, newCode, e.getExperimentParamsYaml().description, context);
        return status;
    }

    public OperationStatusRest editExperimentCommit(ExperimentApiData.SimpleExperiment simpleExperiment) {
        try {
            return experimentService.editExperimentCommit(simpleExperiment);
        } catch (Throwable th) {
            String es = "#285.320 Error while updating an Experiment #"+simpleExperiment.id+", error: " + th.getMessage();
            log.error(es, th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
    }
}
