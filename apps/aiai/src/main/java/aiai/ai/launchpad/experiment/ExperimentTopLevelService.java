/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.experiment;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.data.ExperimentData;
import aiai.api.v1.data.OperationStatusRest;
import aiai.api.v1.data.SnippetApiData;
import aiai.api.v1.data.TaskApiData;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.ControllerUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.launchpad.Workbook;
import aiai.apps.commons.utils.StrUtils;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.api.v1.launchpad.Task;
import aiai.apps.commons.CommonConsts;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static aiai.ai.launchpad.data.ExperimentData.*;

@SuppressWarnings("Duplicates")
@Service
@Slf4j
@Profile("launchpad")
public class ExperimentTopLevelService {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public String code;
        public int seed;
        public long id;

        public static ExperimentData.SimpleExperiment to(Experiment e) {
            return new ExperimentData.SimpleExperiment(e.getName(), e.getDescription(), e.getCode(), e.getSeed(), e.getId());
        }
    }

    private final Globals globals;

    private final SnippetRepository snippetRepository;

    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;

    private final ExperimentCache experimentCache;
    private final ExperimentService experimentService;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final TaskRepository taskRepository;
    private final WorkbookRepository workbookRepository;
    private final TaskPersistencer taskPersistencer;

    public ExperimentTopLevelService(Globals globals, SnippetRepository snippetRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetService snippetService, ExperimentCache experimentCache, ExperimentService experimentService, ExperimentSnippetRepository experimentSnippetRepository, ExperimentFeatureRepository experimentFeatureRepository, TaskRepository taskRepository, WorkbookRepository workbookRepository, TaskPersistencer taskPersistencer) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetService = snippetService;
        this.experimentCache = experimentCache;
        this.experimentService = experimentService;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.taskRepository = taskRepository;
        this.workbookRepository = workbookRepository;
        this.taskPersistencer = taskPersistencer;
    }

    public ExperimentsResult getExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        ExperimentsResult result = new ExperimentsResult();
        result.items = experimentRepository.findAllByOrderByIdDesc(pageable);
        return result;
    }

    public ExperimentResult getExperiment(long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            return new ExperimentResult("#285.01 experiment wasn't found, experimentId: " + experimentId );
        }
        return new ExperimentResult(experiment);
    }

    public PlotData getPlotData(Long experimentId, Long featureId,
                                String[] params, String[] paramsAxis) {
        return experimentService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    public ConsoleResult getTasksConsolePart(Long taskId) {
        ConsoleResult result = new ConsoleResult ();
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task!=null) {
            SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            if (snippetExec!=null) {
                final SnippetApiData.SnippetExecResult execSnippetExecResult = snippetExec.getExec();
                result.items.add(new ConsoleResult.SimpleConsoleOutput(execSnippetExecResult.exitCode, execSnippetExecResult.isOk, execSnippetExecResult.console));
            }
            else {
                log.info("#285.10 snippetExec is null");
            }
        }
        return result;
    }

    public ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        TaskApiData.TasksResult tasksResult = new TaskApiData.TasksResult();
        tasksResult.items = experimentService.findTasks(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        ExperimentFeatureExtendedResult result = new ExperimentFeatureExtendedResult();
        result.tasksResult = tasksResult;
        result.experiment = experiment;
        result.experimentFeature = feature;
        result.consoleResult = new ConsoleResult();
        return result;
    }

    public ExperimentFeatureExtendedResult getExperimentFeatureExtended(Long experimentId, Long featureId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentFeatureExtendedResult("#285.15 experiment wasn't found, experimentId: " + experimentId);
        }

        ExperimentFeature experimentFeature = experimentFeatureRepository.findById(featureId).orElse(null);
        if (experimentFeature == null) {
            return new ExperimentFeatureExtendedResult("#285.19 feature wasn't found, experimentFeatureId: " + featureId);
        }

        return experimentService.prepareExperimentFeatures(experiment, experimentFeature);
    }

    public ExperimentInfoExtendedResult getExperimentInfo(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentInfoExtendedResult("#285.22 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getWorkbookId() == null) {
            return new ExperimentInfoExtendedResult("#285.25 experiment wasn't startet yet, experimentId: " + id);
        }
        Workbook workbook = workbookRepository.findById(experiment.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new ExperimentInfoExtendedResult("#285.29 experiment has broken ref to workbook, experimentId: " + id);
        }

        for (ExperimentHyperParams hyperParams : experiment.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ?variants.count : 0 );
        }

        ExperimentInfoExtendedResult result = new ExperimentInfoExtendedResult();
        if (experiment.getWorkbookId()==null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }

        ExperimentInfoResult experimentInfoResult = new ExperimentInfoResult();
        experimentInfoResult.features = experimentFeatureRepository.findByExperimentIdOrderByMaxValueDesc(experiment.getId());
        experimentInfoResult.workbook = workbook;
        experimentInfoResult.workbookExecState = EnumsApi.WorkbookExecState.toState(workbook.getExecState());

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public ExperimentsEditResult editExperiment(@PathVariable Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentsEditResult("#285.33 experiment wasn't found, experimentId: " + id);
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        ExperimentData.SnippetResult snippetResult = new ExperimentData.SnippetResult();

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        snippetResult.snippets = experimentSnippets;
        final List<String> types = Arrays.asList(CommonConsts.FIT_TYPE, CommonConsts.PREDICT_TYPE);
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets,
                snippetResult.snippets.stream().map(o -> new SnippetCode(o.getId(), o.getSnippetCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!types.contains(s.type) ) {
                        return true;
                    }
                    if (CommonConsts.FIT_TYPE.equals(s.type) && snippetService.hasFit(experimentSnippets)) {
                        return true;
                    }
                    else if (CommonConsts.PREDICT_TYPE.equals(s.type) && snippetService.hasPredict(experimentSnippets)) {
                        return true;
                    }
                    else return false;
                });

        snippetResult.sortSnippetsByOrder();
        ExperimentsEditResult result = new ExperimentsEditResult();
        result.hyperParams = HyperParamsResult.getInstance(experiment);
        result.simpleExperiment = SimpleExperiment.to(experiment);
        result.snippetResult = snippetResult;
        return result;
    }

    public OperationStatusRest addExperimentCommit(Experiment experiment) {
        if (experiment.getSeed()==0) {
            experiment.setSeed(1);
        }
        experiment.setCreatedOn(System.currentTimeMillis());
        return processCommit(experiment);
    }

    public OperationStatusRest editExperimentCommit(ExperimentData.SimpleExperiment simpleExperiment) {
        Experiment experiment = experimentCache.findById(simpleExperiment.id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.37 experiment wasn't found, experimentId: " + simpleExperiment.id);
        }
        experiment.setName(simpleExperiment.getName());
        experiment.setDescription(simpleExperiment.getDescription());
        experiment.setSeed(simpleExperiment.getSeed());
        experiment.setCode(simpleExperiment.getCode());
        experiment.setCreatedOn(System.currentTimeMillis());

        return processCommit(experiment);
    }

    private OperationStatusRest processCommit(Experiment experiment) {
        if (StringUtils.isBlank(experiment.getName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.40 Name of experiment is blank.");
        }
        if (StringUtils.isBlank(experiment.getCode())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.41 Code of experiment is blank.");
        }
        if (StringUtils.isBlank(experiment.getDescription())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.42 Description of experiment is blank.");
        }
        experiment.strip();
        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataAddCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.45 experiment wasn't found, experimentId: " + experimentId);
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.48 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        String keyFinal = key.trim();
        boolean isExist = experiment.getHyperParams().stream().map(ExperimentHyperParams::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.51 hyper parameter "+key+" already exist");
        }

        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(keyFinal);
        m.setValues(value.trim());
        experiment.getHyperParams().add(m);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataEditCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.53 experiment wasn't found, id: "+experimentId );
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.55 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        ExperimentHyperParams m=null;
        String keyFinal = key.trim();
        for (ExperimentHyperParams hyperParam : experiment.getHyperParams()) {
            if (hyperParam.getKey().equals(keyFinal)) {
                m = hyperParam;
                break;
            }
        }
        if (m==null) {
            m = new ExperimentHyperParams();
            m.setExperiment(experiment);
            m.setKey(keyFinal);
            experiment.getHyperParams().add(m);
        }
        m.setValues(value.trim());

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest snippetAddCommit(Long id, String snippetCode) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.58 experiment wasn't found, id: "+id );
        }
        Long experimentId = experiment.getId();
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experimentId);

        Snippet snippet = snippetRepository.findByCode(snippetCode);

        ExperimentSnippet ts = new ExperimentSnippet();
        ts.setExperimentId(experimentId);
        ts.setSnippetCode( snippetCode );
        ts.setType( snippet.type );

        List<ExperimentSnippet> list = new ArrayList<>(experimentSnippets);
        list.add(ts);

        ExperimentService.sortSnippetsByType(list);

        experimentSnippetRepository.saveAll(list);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDeleteCommit(long experimentId, Long id) {
        ExperimentHyperParams hyperParams = experimentHyperParamsRepository.findById(id).orElse(null);
        if (hyperParams == null || experimentId != hyperParams.getExperiment().getId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.61 Hyper parameters misconfigured, try again.");
        }
        experimentHyperParamsRepository.deleteById(id);
        experimentCache.invalidate(experimentId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDefaultAddCommit(long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.63 experiment wasn't found, id: "+experimentId );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }

        add(experiment, "epoch", "[10]");
        add(experiment, "RNN", "[LSTM, GRU, SimpleRNN]");
        add(experiment, "activation", "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]");
        add(experiment, "optimizer", "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]");
        add(experiment, "batch_size", "[20, 40, 60]");
        add(experiment, "time_steps", "[5, 40, 60]");
        add(experiment, "metrics_functions", "['#in_top_draw_digit, accuracy', 'accuracy']");

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private void add(Experiment experiment, String key, String value) {
        ExperimentHyperParams param = getParams(experiment, key, value);
        List<ExperimentHyperParams> params = experiment.getHyperParams();
        for (ExperimentHyperParams p1 : params) {
            if (p1.getKey().equals(param.getKey())) {
                p1.setValues(param.getValues());
                return;
            }
        }
        params.add(param);
    }

    private ExperimentHyperParams getParams(Experiment experiment, String key, String value) {
        ExperimentHyperParams params = new ExperimentHyperParams();
        params.setExperiment(experiment);
        params.setKey(key);
        params.setValues(value);
        return params;
    }

    public OperationStatusRest snippetDeleteCommit(long experimentId, Long id) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getExperimentId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.66 Snippet is misconfigured. Try again" );
        }
        experimentSnippetRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.71 experiment wasn't found, experimentId: " + id);
        }
        experimentSnippetRepository.deleteByExperimentId(id);
        experimentFeatureRepository.deleteByExperimentId(id);
        experimentCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentCloneCommit(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.73 experiment wasn't found, experimentId: " + id);
        }
        final Experiment e = new Experiment();
        e.setCode(StrUtils.incCopyNumber(experiment.getCode()));
        e.setName(StrUtils.incCopyNumber(experiment.getName()));
        e.setDescription(experiment.getDescription());
        e.setSeed(experiment.getSeed());
        e.setCreatedOn(System.currentTimeMillis());
        e.setHyperParams(
                experiment.getHyperParams()
                        .stream()
                        .map( p -> new ExperimentHyperParams(p.getKey(), p.getValues(), e))
                        .collect(Collectors.toList()));
        experimentCache.save(e);

        List<ExperimentSnippet> snippets = experimentSnippetRepository.findByExperimentId(experiment.getId());

        experimentSnippetRepository.saveAll( snippets.stream().map(s -> new ExperimentSnippet(s.getSnippetCode(), s.getType(), e.getId())).collect(Collectors.toList()));

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest rerunTask(long taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.75 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        Workbook workbook = workbookRepository.findById(task.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.84 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(taskId);
        return t!=null
                ? OperationStatusRest.OPERATION_STATUS_OK
                : new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Can't re-run task #"+taskId+", see log for more information");
    }
}
