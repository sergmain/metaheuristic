package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.flow.ProcessValidator;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExperimentProcessValidator implements ProcessValidator {

    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final ExperimentCache experimentCache;

    public ExperimentProcessValidator(SnippetCache snippetCache, SnippetService snippetService, ExperimentCache experimentCache) {
        this.snippetCache = snippetCache;
        this.snippetService = snippetService;
        this.experimentCache = experimentCache;
    }

    @Override
    public Enums.FlowValidateStatus validate(Process process) {
        if (process.snippetCodes!=null && process.snippetCodes.size() > 0) {
            return Enums.FlowValidateStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
        }
        if (StringUtils.isBlank(process.code)) {
            return Enums.FlowValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        Experiment e = experimentCache.findByCode(process.code);
        if (e==null) {
            return Enums.FlowValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        if (e.getFlowInstanceId()!=null) {
            return Enums.FlowValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR;
        }
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(e.getId());
        if (experimentSnippets==null || experimentSnippets.size()<2) {
            return Enums.FlowValidateStatus.EXPERIMENT_HASNT_ALL_SNIPPETS_ERROR;
        }

        if (process.metas==null || process.metas.isEmpty()) {
            return Enums.FlowValidateStatus.EXPERIMENT_META_NOT_FOUND_ERROR;
        }

        Process.Meta m1 = process.getMeta("dataset");
        if (m1 ==null || StringUtils.isBlank(m1.getValue())) {
            return Enums.FlowValidateStatus.EXPERIMENT_META_DATASET_NOT_FOUND_ERROR;
        }
        Process.Meta m2 = process.getMeta("assembled-raw");
        if (m2 ==null || StringUtils.isBlank(m2.getValue())) {
            return Enums.FlowValidateStatus.EXPERIMENT_META_ASSEMBLED_RAW_NOT_FOUND_ERROR;
        }
        Process.Meta m3 = process.getMeta("feature");
        if (m3 ==null || StringUtils.isBlank(m3.getValue())) {
            return Enums.FlowValidateStatus.EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR;
        }

        return null;
    }
}
