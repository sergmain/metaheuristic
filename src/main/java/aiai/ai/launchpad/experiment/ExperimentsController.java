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

import aiai.ai.ControllerUtils;
import aiai.ai.beans.*;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.repositories.ExperimentMetadataRepository;
import aiai.ai.repositories.ExperimentRepository;
import aiai.ai.repositories.ExperimentSnippetRepository;
import aiai.ai.repositories.SnippetRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private final ExperimentRepository experimentRepository;
    private final ExperimentMetadataRepository experimentMetadataRepository;
    private final SnippetRepository snippetRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;

    public ExperimentsController(ExperimentRepository experimentRepository, ExperimentMetadataRepository experimentMetadataRepository, SnippetRepository snippetRepository, ExperimentSnippetRepository experimentSnippetRepository) {
        this.experimentRepository = experimentRepository;
        this.experimentMetadataRepository = experimentMetadataRepository;
        this.snippetRepository = snippetRepository;
        this.experimentSnippetRepository = experimentSnippetRepository;
    }

    @Data
    @AllArgsConstructor
    public static class SnippetSelectOption {
        String value;
        String desc;
    }

    @Data
    public static class SnippetResult {
        public List<SnippetSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippet> snippets = new ArrayList<>();
    }

    @GetMapping("/experiments")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
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
        for (ExperimentMetadata metadata : experiment.getMetadata()) {
            if (StringUtils.isBlank(metadata.getValue())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getStringNumberOfVariants(metadata.getValue());
            metadata.setVariants( variants.status ?variants.count : 0 );
        }
        model.addAttribute("experiment", experiment);
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
                snippetResult.selectOptions.add( new SnippetSelectOption(snippet.getSnippetCode(), String.format("Type: %s; Code: %s:%s", snippet.getType(), snippet.getName(), snippet.getSnippetVersion())));
            }
        }
//        snippetResult.snippets.sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
        snippetResult.snippets.sort(Comparator.comparing(ExperimentSnippet::getType));

        model.addAttribute("experiment", experiment);
        model.addAttribute("snippetResult", snippetResult);
        return "launchpad/experiment-edit-form";
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public String metadataAddCommit(@PathVariable Long id, String key, String value) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getMetadata()==null) {
            experiment.setMetadata(new ArrayList<>());
        }
        ExperimentMetadata m = new ExperimentMetadata();
        m.setExperiment(experiment);
        m.setKey(key);
        m.setValue(value);
        experiment.getMetadata().add(m);

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

        //noinspection ConstantConditions
        List<ExperimentSnippet> snippets = experimentSnippetRepository.findByExperiment_Id(experiment.getId());
        int order = snippets.isEmpty() ? 1 : snippets.stream().mapToInt(ExperimentSnippet::getOrder).max().getAsInt() + 1;
        s.setOrder(order);

        s.setExperiment(experiment);
        s.setSnippetCode( code );

        String name = code.substring(0, code.indexOf(':'));
        String version = code.substring(code.indexOf(':')+1);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(name, version);
        s.setType(snippet.getType());
        experiment.getSnippets().add(s);

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{id}")
    public String metadataDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentMetadata metadata = experimentMetadataRepository.findById(id).orElse(null);
        if (metadata == null || experimentId != metadata.getExperiment().getId()) {
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentMetadataRepository.deleteById(id);
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
        ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getEpochVariants(experiment.getEpoch());
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

}
