package aiai.ai.launchpad.file_process;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.flow.ProcessValidator;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
public class FileProcessValidator implements ProcessValidator {

    private final SnippetCache snippetCache;

    public FileProcessValidator(SnippetCache snippetCache) {
        this.snippetCache = snippetCache;
    }

    @Override
    public Enums.FlowValidateStatus validate(Flow flow, Process process) {
        if (process.getSnippetCodes() == null || process.getSnippetCodes().isEmpty()) {
            return Enums.FlowValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        for (String snippetCode : process.snippetCodes) {
            SnippetVersion sv = SnippetVersion.from(snippetCode);
            Snippet snippet = snippetCache.findByNameAndSnippetVersion(sv.name, sv.version);
            if (snippet==null) {
                log.warn("Snippet wasn't found for code: {}, process: {}", snippetCode, process);
                return Enums.FlowValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }

        if (!process.parallelExec && process.snippetCodes.size()>1) {
            return Enums.FlowValidateStatus.TOO_MANY_SNIPPET_CODES_ERROR;
        }

        return null;
    }
}
