package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.flow.ProcessValidator;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("launchpad")
public class ExperimentProcessValidator implements ProcessValidator {

    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final ExperimentCache experimentCache;
    private final FlowInstanceRepository flowInstanceRepository;

    public ExperimentProcessValidator(SnippetCache snippetCache, SnippetService snippetService, ExperimentCache experimentCache, FlowInstanceRepository flowInstanceRepository) {
        this.snippetCache = snippetCache;
        this.snippetService = snippetService;
        this.experimentCache = experimentCache;
        this.flowInstanceRepository = flowInstanceRepository;
    }

    // TODO ! experiment has to be stateless and have its own instances

    @Override
    public Enums.FlowValidateStatus validate(Flow flow, Process process) {
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
            FlowInstance flowInstance = flowInstanceRepository.findById(e.getFlowInstanceId()).orElse(null);
            if (flowInstance != null) {
                if (!flow.getId().equals(flowInstance.getFlowId())) {
                    return Enums.FlowValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR;
                }
            }
            else {
                return Enums.FlowValidateStatus.FLOW_INSTANCE_DOESNT_EXIST_ERROR;
            }
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
