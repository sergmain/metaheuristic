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

import aiai.ai.utils.ControllerUtils;
import aiai.ai.beans.*;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.repositories.*;
import aiai.ai.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        public Slice<ExperimentSequence> sequences;
    }

//    @Value("${aiai.table.rows.limit:#{5}}")
    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.table.rows.limit'), 5, 30, 5) }")
    private int limit;

    private final DatasetRepository datasetRepository;
    private final DatasetGroupsRepository datasetGroupsRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final SnippetRepository snippetRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;

    public ExperimentsController(DatasetRepository datasetRepository, DatasetGroupsRepository datasetGroupsRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetRepository snippetRepository, ExperimentSnippetRepository experimentSnippetRepository, ExperimentFeatureRepository experimentFeatureRepository, ExperimentSequenceRepository experimentSequenceRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetGroupsRepository = datasetGroupsRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetRepository = snippetRepository;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
    }

    @Data
    @AllArgsConstructor
    public static class SimpleSelectOption {
        String value;
        String desc;
    }

    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippet> snippets = new ArrayList<>();
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

    @GetMapping("/experiments")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(limit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperiments(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(limit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments :: table";
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}")
    public String getSequncesPart(@ModelAttribute(name = "result") SequencesResult result,
                                  @PathVariable Long experimentId, @PathVariable Long featureId, @PageableDefault(size = 10) Pageable pageable) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            result.sequences = Page.empty();
        }
        else {
            pageable = ControllerUtils.fixPageSize(10, pageable);
            result.sequences = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(pageable, featureId);
        }
        return "launchpad/experiment-feature-progress :: table";
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

        SequencesResult result = new SequencesResult();
        result.sequences = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(PageRequest.of(0, 10), featureId);

        model.addAttribute("result", result);
        model.addAttribute("experiment", experiment);
        model.addAttribute("feature", feature);
        return "launchpad/experiment-feature-progress";
    }



    @GetMapping(value = "/experiment-add")
    public String add(@ModelAttribute("experiment") Experiment experiment) {
        experiment.setSeed(1);
        return "launchpad/experiment-add-form";
    }

    @GetMapping(value = "/experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#81.01 experiment wasn't found, experimentId: " + id);
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
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        SnippetResult snippetResult = new SnippetResult();
        experiment.sortSnippetsByOrder();
        for (Snippet snippet : snippets) {
            boolean isExist=false;
            for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                if (snippet.getSnippetCode().equals(experimentSnippet.getSnippetCode()) ) {
                    experimentSnippet.type = snippet.type;
                    snippetResult.snippets.add(experimentSnippet);
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (SnippetType.fit.equals(snippet.type) && experiment.hasFit()) {
                    continue;
                }
                if (SnippetType.predict.equals(snippet.type) && experiment.hasPredict()) {
                    continue;
                }
                snippetResult.selectOptions.add( new SimpleSelectOption(snippet.getSnippetCode(), String.format("Type: %s; Code: %s:%s", snippet.getType(), snippet.getName(), snippet.getSnippetVersion())));
            }
        }

        ExperimentResult experimentResult = new ExperimentResult();
        Dataset dataset = getDatasetAndCheck(experiment);
        if (dataset==null) {
            for (Dataset ds : datasetRepository.findAll()) {
                experimentResult.allDatasetOptions.add(new SimpleSelectOption(ds.getId().toString(), String.format("Id: %d; %s", ds.getId(), ds.getName())));
            }
        }
        experimentResult.dataset = dataset;
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
        return processCommit(model, experiment,  "launchpad/experiment-add-form");
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
        return processCommit(model, experiment,  "launchpad/experiment-edit-form");
    }

    private String processCommit(Model model, Experiment experiment, String target) {
        ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(experiment.getEpoch());
        if (!numberOfVariants.status) {
            model.addAttribute("errorMessage", numberOfVariants.getError());
            return target;
        }
        experiment.setEpochVariant(numberOfVariants.getCount());
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiments";
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
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        experiment.setDatasetId(null);
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public String metadataAddCommit(@PathVariable Long id, String key, String value) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(key);
        m.setValues(value);
        experiment.getHyperParams().add(m);

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
        add(experiment, "activation", "[elu, linear, softsign, relu, tanh, sigmoid, hard_sigmoid, softplus]");
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
        BeanUtils.copyProperties(experiment, trg);
        trg.setId(null);
        trg.setVersion(null);
        trg.setName( StrUtils.incCopyNumber(experiment.getName()) );
        trg.setDescription( StrUtils.incCopyNumber( experiment.getDescription()) );
        trg.setAllSequenceProduced(false);
        trg.setFeatureProduced(false);
        trg.setLaunchedOn(null);
        trg.setLaunched(false);
        List<ExperimentSnippet> snippets = experiment.getSnippets();
        trg.setSnippets(new ArrayList<>());
        List<ExperimentHyperParams> params = experiment.getHyperParams();
        trg.setHyperParams(new ArrayList<>());
        experimentRepository.save(trg);

        for (ExperimentSnippet snippet : snippets) {
            ExperimentSnippet trgSnippet = new ExperimentSnippet();
            BeanUtils.copyProperties(snippet, trgSnippet);
            trgSnippet.setId(null);
            trgSnippet.setVersion(null);
            trgSnippet.setExperiment(trg);
            trg.getSnippets().add(trgSnippet);
            experimentSnippetRepository.save(trgSnippet);
        }
        for (ExperimentHyperParams params1 : params) {
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

    @GetMapping("/experiment-launch/{experimentId}")
    public String launch(@PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84BeanUtils.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.isLaunched()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.02 experiment was already launched, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
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
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiments";
    }

}
