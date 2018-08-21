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
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
public class ExperimentsController {

    @Data
    public static class Result {
        public Slice<Experiment> items;
    }

    @Value("${aiai.table.rows.limit:#{5}}")
    private int limit;

    private final DatasetsRepository datasetRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final SnippetRepository snippetRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;

    public ExperimentsController(DatasetsRepository datasetRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetRepository snippetRepository, ExperimentSnippetRepository experimentSnippetRepository) {
        this.datasetRepository = datasetRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetRepository = snippetRepository;
        this.experimentSnippetRepository = experimentSnippetRepository;
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
        public Experiment experiment;
        public Dataset dataset;
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
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
        Dataset dataset = null;
        if (experiment.getDatasetId()!=null) {
            dataset = datasetRepository.findById(experiment.getDatasetId()).orElse(null);
            if (dataset == null) {
                experiment.setDatasetId(null);
                experimentRepository.save(experiment);
            }
        }
        experimentResult.experiment = experiment;
        experimentResult.dataset = dataset;
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
        Dataset dataset = null;
        if (experiment.getDatasetId()!=null) {
            dataset = datasetRepository.findById(experiment.getDatasetId()).orElse(null);
            if (dataset == null) {
                experiment.setDatasetId(null);
                experimentRepository.save(experiment);
            }
        }
        if (dataset==null) {
            for (Dataset ds : datasetRepository.findAll()) {
                experimentResult.allDatasetOptions.add(new SimpleSelectOption(ds.getId().toString(), String.format("Id: %d; %s", ds.getId(), ds.getName())));
            }
        }
        experimentResult.experiment = experiment;
        experimentResult.dataset = dataset;
        model.addAttribute("experimentResult", experimentResult);
        model.addAttribute("snippetResult", snippetResult);
        return "launchpad/experiment-edit-form";
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

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{id}")
    public String snippetDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getExperiment().getId()) {
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentSnippetRepository.deleteById(id);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
    }

    @PostMapping("/experiment-add-form-commit")
    public String addFormCommit(Model model, Experiment experiment) {
        return processCommit(model, experiment,  "launchpad/experiment-add-form");
    }

    @PostMapping("/experiment-edit-form-commit")
    public String editFormCommit(Model model, Experiment experiment) {
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
    public String deleteCommit(Long id) {
        experimentRepository.deleteById(id);
        return "redirect:/launchpad/experiments";
    }

    @GetMapping("/experiment-launch/{experimentId}")
    public String launch(@PathVariable long experimentId,final RedirectAttributes redirectAttributes, Model model) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#84.01 experiment wasn't found, experimentId: " + experimentId);
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

        Map<String, String> map = ExperimentService.toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
        List<ExperimentUtils.HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

        experiment.setNumberOfSequence(allHyperParams.size());
        experiment.setLaunched(true);
        experiment.setLaunchedOn(System.currentTimeMillis());
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiments";
    }

}
