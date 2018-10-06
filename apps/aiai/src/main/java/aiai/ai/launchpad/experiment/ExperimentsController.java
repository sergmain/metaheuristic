/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.feature.FeatureExecStatus;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.launchpad.beans.*;
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.yaml.snippet.SnippetType;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:22
 */
@Controller
@RequestMapping("/launchpad")
@Slf4j
public class ExperimentsController {

    @Data
    public static class Result {
        public Slice<Experiment> items;
    }

    @Data
    public static class SequencesResult {
        public Slice<ExperimentSequence> items;
    }

    @Data
    public static class ConsoleResult {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleConsoleOuput {
            public int order;
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<SimpleConsoleOuput> items = new ArrayList<>();
    }

    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippet> snippets = new ArrayList<>();

        public void sortSnippetsByOrder() {
            snippets.sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
        }
    }

    @Data
    public static class ExperimentResult {
        public Dataset dataset;
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeature> features;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public int seed;
        public String epoch;
        public long id;

        public static SimpleExperiment to(Experiment e) {
            return new SimpleExperiment(e.getName(), e.getDescription(), e.getSeed(), e.getEpoch(), e.getId());
        }
    }

    private final Globals globals;

    private final DatasetRepository datasetRepository;
    private final DatasetGroupsRepository datasetGroupsRepository;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentService experimentService;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;
    private final ExperimentSequenceWithSpecRepository experimentSequenceWithSpecRepository;

    public ExperimentsController(Globals globals, DatasetRepository datasetRepository, DatasetGroupsRepository datasetGroupsRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetRepository snippetRepository, SnippetService snippetService, ExperimentService experimentService, ExperimentSnippetRepository experimentSnippetRepository, ExperimentFeatureRepository experimentFeatureRepository, ExperimentSequenceRepository experimentSequenceRepository, ExperimentSequenceWithSpecRepository experimentSequenceWithSpecRepository) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.datasetGroupsRepository = datasetGroupsRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
        this.experimentService = experimentService;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
        this.experimentSequenceWithSpecRepository = experimentSequenceWithSpecRepository;
    }

    @GetMapping("/experiments")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperiments(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments :: table";
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public String getSequncesPart(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @PageableDefault(size = 10) Pageable pageable) {
        Experiment experiment= experimentRepository.findById(experimentId).orElse(null);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        SequencesResult result = new SequencesResult();
        result.items = experimentService.findExperimentSequence(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        model.addAttribute("result", result);
        model.addAttribute("experiment", experiment);
        model.addAttribute("feature", feature);
        model.addAttribute("consoleResult", new ConsoleResult());

        return "launchpad/experiment-feature-progress :: fragment-table";
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody ExperimentService.PlotData getPlotData(Model model, @PathVariable Long experimentId, @PathVariable Long featureId,
                                                                @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        Experiment experiment= experimentRepository.findById(experimentId).orElse(null);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        //noinspection UnnecessaryLocalVariable
        ExperimentService.PlotData data = experimentService.findExperimentSequenceForPlot(experiment, feature, params, paramsAxis);
        return data;
    }

    @PostMapping("/experiment-feature-progress-console-part/{id}")
    public String getSequncesConsolePart(Model model, @PathVariable(name="id") Long sequenceId) {
        ConsoleResult result = new ConsoleResult();
        ExperimentSequence sequence = experimentSequenceRepository.findById(sequenceId).orElse(null);
        if (sequence!=null) {
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(sequence.getSnippetExecResults());
            for (Map.Entry<Integer, ProcessService.Result> entry : snippetExec.getExecs().entrySet()) {
                final ProcessService.Result value = entry.getValue();
                result.items.add( new ConsoleResult.SimpleConsoleOuput(entry.getKey(), value.exitCode, value.isOk, value.console));
            }
        }
        model.addAttribute("consoleResult", result);

        return "launchpad/experiment-feature-progress :: fragment-console-table";
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public String getSequences(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#80.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }

        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);
        if (feature == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#80.02 feature wasn't found, featureId: " + featureId);
            return "redirect:/launchpad/experiments";
        }

        Map<String, Object> map = experimentService.prepareExperimentFeatures(experiment, feature);
        model.addAllAttributes(map);

        return "launchpad/experiment-feature-progress";
    }


    @GetMapping(value = "/experiment-add")
    public String add(@ModelAttribute("experiment") Experiment experiment) {
        experiment.setSeed(1);
        return "launchpad/experiment-add-form";
    }

    @GetMapping(value = "/experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#82.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        for (ExperimentHyperParams hyperParams : experiment.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ?variants.count : 0 );
        }
        if (experiment.getDatasetId()==null) {
            model.addAttribute("infoMessages", Collections.singleton("Launch is disabled, dataset isn't assigned"));
        }

        ExperimentResult experimentResult = new ExperimentResult();
        Dataset dataset = getDatasetAndCheck(experiment);
        experimentResult.dataset = dataset;
        experimentResult.features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        experimentResult.features.sort( (ExperimentFeature o1, ExperimentFeature o2) -> (Boolean.compare(o2.isFinished, o1.isFinished)));


        model.addAttribute("experiment", experiment);
        model.addAttribute("experimentResult", experimentResult);
        return "launchpad/experiment-info";
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        final Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        SnippetResult snippetResult = new SnippetResult();
        experiment.sortSnippetsByOrder();

        snippetResult.snippets = snippetService.getExperimentSnippets(snippets, experiment);
        final List<SnippetType> types = List.of(SnippetType.fit, SnippetType.predict);
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets, snippetResult.snippets,
                (s) -> {
                    if (!types.contains(SnippetType.valueOf(s.type)) ) {
                        return true;
                    }
                    if (SnippetType.fit.equals(s.type) && experiment.hasFit()) {
                        return true;
                    }
                    if (SnippetType.predict.equals(s.type) && experiment.hasPredict()) {
                        return true;
                    }
                    return false;
                });

        ExperimentResult experimentResult = new ExperimentResult();
        Dataset dataset = getDatasetAndCheck(experiment);
        if (dataset==null) {
            for (Dataset ds : datasetRepository.findAll()) {
                experimentResult.allDatasetOptions.add(new SimpleSelectOption(ds.getId().toString(), String.format("Id: %d; %s", ds.getId(), ds.getName())));
            }
        }
        experimentResult.dataset = dataset;
        snippetResult.sortSnippetsByOrder();
        model.addAttribute("experiment", experiment);
        model.addAttribute("simpleExperiment", SimpleExperiment.to(experiment));
        model.addAttribute("experimentResult", experimentResult);
        model.addAttribute("snippetResult", snippetResult);
        return "launchpad/experiment-edit-form";
    }

    private Dataset getDatasetAndCheck(Experiment experiment) {
        Dataset dataset = null;
        if (experiment.getDatasetId()!=null) {
            dataset = datasetRepository.findById(experiment.getDatasetId()).orElse(null);
            if (dataset == null) {
                log.warn("dataset wasn't found for id {}", experiment.getDatasetId());
                experiment.setDatasetId(null);
                experimentRepository.save(experiment);
            }
        }
        return dataset;
    }

    @PostMapping("/experiment-add-form-commit")
    public String addFormCommit(Model model, Experiment experiment) {
        return processCommit(model, experiment,  "launchpad/experiment-add-form", "redirect:/launchpad/experiments");
    }

    @PostMapping("/experiment-edit-form-commit")
    public String editFormCommit(Model model, SimpleExperiment simpleExperiment, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(simpleExperiment.id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#81.01 experiment wasn't found, experimentId: " + simpleExperiment.id);
            return "redirect:/launchpad/experiments";
        }
        experiment.setName(simpleExperiment.getName());
        experiment.setDescription(simpleExperiment.getDescription());
        experiment.setSeed(simpleExperiment.getSeed());
        experiment.setEpoch(simpleExperiment.getEpoch());
        return processCommit(model, experiment,  "launchpad/experiment-edit-form", "redirect:/launchpad/experiment-edit/"+experiment.getId());
    }

    private String processCommit(Model model, Experiment experiment, String errorTarget, String normalTarget) {
        ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(experiment.getEpoch());
        if (!numberOfVariants.status) {
            model.addAttribute("errorMessage", numberOfVariants.getError());
            return errorTarget;
        }
        experiment.setEpochVariant(numberOfVariants.getCount());
        experimentRepository.save(experiment);
        return normalTarget;
    }

    public static void sortSnippetsByType(List<ExperimentSnippet> snippets) {
        snippets.sort(Comparator.comparing(ExperimentSnippet::getType));
    }

    @PostMapping("/experiment-dataset-assign-commit/{id}")
    public String datasetAddCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#87.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()!=null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#87.02 Dataset is already assigned to this experiment, experimentId: " + id);
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        Dataset dataset = datasetRepository.findById(Long.parseLong(code)).orElse(null);
        if (dataset==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#87.03 Wrong datasetId: "+code+", experimentId: " + id);
            return "redirect:/launchpad/experiment-edit/"+id;
        }

        experiment.setDatasetId(dataset.getId());
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @GetMapping("/experiment-dataset-unassign-commit/{id}")
    public String datasetAddCommit(@PathVariable Long id, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#88.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()==null) {
            // dataset was already unassigned
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        experiment.setDatasetId(null);
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public String metadataAddCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#89.51 hyper param's key and value must not be null, key: "+key+", value: " + value );
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        String keyFinal = key.trim();
        boolean isExist = experiment.getHyperParams().stream().map(ExperimentHyperParams::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            redirectAttributes.addFlashAttribute("errorMessage", "#89.52 hyper parameter "+key+" already exist");
            return "redirect:/launchpad/experiment-edit/"+id;
        }

        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(keyFinal);
        m.setValues(value.trim());
        experiment.getHyperParams().add(m);

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-edit-commit/{id}")
    public String metadataEditCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#89.01 experiment wasn't found, id: "+id );
            return "redirect:/launchpad/experiments";
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#89.02 hyper param's key and value must not be null, key: "+key+", value: " + value );
            return "redirect:/launchpad/experiment-edit/"+id;
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

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-snippet-add-commit/{id}")
    public String snippetAddCommit(@PathVariable Long id, String code) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getSnippets()==null) {
            experiment.setSnippets(new ArrayList<>());
        }
        ExperimentSnippet s = new ExperimentSnippet();
        s.setExperiment(experiment);
        s.setSnippetCode( code );

        SnippetVersion snippetVersion = SnippetVersion.from(code);

        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        s.setType(snippet.getType());
        experiment.getSnippets().add(s);

        sortSnippetsByType(experiment.getSnippets());
        int order = 1;
        for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
            experimentSnippet.setOrder(order++);
        }

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{id}")
    public String metadataDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentHyperParams hyperParams = experimentHyperParamsRepository.findById(id).orElse(null);
        if (hyperParams == null || experimentId != hyperParams.getExperiment().getId()) {
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentHyperParamsRepository.deleteById(id);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public String metadataDefaultAddCommit(@PathVariable long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }

        add(experiment, "RNN", "[LSTM, GRU, SimpleRNN]");
        add(experiment, "activation", "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]");
        add(experiment, "optimizer", "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]");
        add(experiment, "batch_size", "[20, 40, 60]");
        add(experiment, "time_steps", "[5, 40, 60]");

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
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

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{id}")
    public String snippetDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getExperiment().getId()) {
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentSnippetRepository.deleteById(id);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
    }

    @GetMapping("/experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        model.addAttribute("experiment", experiment);
        return "launchpad/experiment-delete";
    }

    @PostMapping("/experiment-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#83.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        experimentSnippetRepository.deleteByExperimentId(id);
        experimentSequenceRepository.deleteByExperimentId(id);
        experimentFeatureRepository.deleteByExperimentId(id);
        experimentRepository.deleteById(id);
        return "redirect:/launchpad/experiments";
    }

    @PostMapping("/experiment-clone-commit")
    public String cloneCommit(Long id, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#83.02 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        Experiment trg = new Experiment();
        trg.setDatasetId(experiment.getDatasetId());
        trg.setEpoch(experiment.getEpoch());
        trg.setEpochVariant(experiment.getEpochVariant());
        trg.setSeed(experiment.getSeed());
        trg.setName( StrUtils.incCopyNumber(experiment.getName()) );
        trg.setDescription( experiment.getDescription() );
        trg.setAllSequenceProduced(false);
        trg.setFeatureProduced(false);
        trg.setCreatedOn(System.currentTimeMillis());
        trg.setLaunchedOn(null);
        trg.setLaunched(false);
        trg.setSnippets(new ArrayList<>());
        trg.setHyperParams(new ArrayList<>());
        trg.setExecState(Enums.ExperimentExecState.NONE.code);
        experimentRepository.save(trg);

        for (ExperimentSnippet snippet : experiment.getSnippets()) {
            ExperimentSnippet trgSnippet = new ExperimentSnippet();
            BeanUtils.copyProperties(snippet, trgSnippet);
            trgSnippet.setId(null);
            trgSnippet.setVersion(null);
            trgSnippet.setExperiment(trg);
            trg.getSnippets().add(trgSnippet);
            experimentSnippetRepository.save(trgSnippet);
        }
        for (ExperimentHyperParams params1 : experiment.getHyperParams()) {
            ExperimentHyperParams trgParam = new ExperimentHyperParams();
            BeanUtils.copyProperties(params1, trgParam);
            trgParam.setId(null);
            trgParam.setVersion(null);
            trgParam.setExperiment(trg);
            trg.getHyperParams().add(trgParam);
            experimentHyperParamsRepository.save(trgParam);
        }

        return "redirect:/launchpad/experiments";
    }

    @PostMapping("/experiment-sequence-rerun/{id}")
    public @ResponseBody boolean rerunSequence(@PathVariable long id) {
        ExperimentSequence seq = experimentSequenceRepository.findById(id).orElse(null);
        if (seq == null) {
            return false;
        }
        ExperimentFeature feature = experimentFeatureRepository.findById(seq.featureId).orElse(null);
        if (feature == null) {
            return false;
        }
        Experiment experiment = experimentRepository.findById(seq.getExperimentId()).orElse(null);
        if (experiment == null) {
            return false;
        }

        seq.setCompletedOn(null);
        seq.setCompleted(false);
        seq.setMetrics(null);
        seq.setAllSnippetsOk(false);
        seq.setSnippetExecResults(null);
        seq.setStationId(null);
        seq.setAssignedOn(null);
        experimentSequenceRepository.save(seq);

        feature.setExecStatus(FeatureExecStatus.unknown.code);
        feature.setFinished(false);
        feature.setInProgress(true);
        experimentFeatureRepository.save(feature);

        experiment.setExecState(Enums.ExperimentExecState.STOPPED.code);
        experimentRepository.save(experiment);

        return true;
    }

    @GetMapping("/experiment-launch/{experimentId}")
    public String launch(@PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.isLaunched()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.02 experiment was already launched, experimentId: " + experimentId);
            return "redirect:/launchpad/experiment-info/"+experimentId;
        }

        if (experiment.getDatasetId()==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.03 dataset wasn't assigned, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        Dataset dataset = datasetRepository.findById(experiment.getDatasetId()).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.04 experiment has broken link to dataset. Need to reassign a dataset.");
            return "redirect:/launchpad/experiments";
        }
        dataset.setLocked(true);
        dataset.setEditable(false);
        datasetRepository.save(dataset);

        experiment.setLaunched(true);
        experiment.setLaunchedOn(System.currentTimeMillis());
        experiment.setExecState(Enums.ExperimentExecState.STARTED.code);
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiment-info/"+experimentId;
    }

    @GetMapping("/experiment-target-exec-state/{state}/{experimentId}")
    public String stop(@PathVariable String state, @PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#85.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#85.03 dataset wasn't assigned, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if(!experiment.isLaunched()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#85.04 Experiment wasn't started yet, experimentId: " + experimentId);
            return "redirect:/launchpad/experiment-info/"+experimentId;
        }
        Enums.ExperimentExecState execState = Enums.ExperimentExecState.valueOf(state.toUpperCase());

        if ((execState==Enums.ExperimentExecState.STARTED && experiment.getExecState()==Enums.ExperimentExecState.STARTED.code) ||
                (execState==Enums.ExperimentExecState.STOPPED && experiment.getExecState()==Enums.ExperimentExecState.STOPPED.code)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#85.05 Experiment is already in target state: " + execState.toString());
            return "redirect:/launchpad/experiment-info/"+experimentId;

        }
        experiment.setExecState(execState.code);
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiment-info/"+experimentId;
    }

}
