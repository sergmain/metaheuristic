/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.snippet;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.utils.SimpleSelectOption;
import aiai.ai.utils.SnippetUtils;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SnippetService {

    private final Globals globals;
    private final PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver;
    private final SnippetRepository snippetRepository;

    public SnippetService(Globals globals, SnippetRepository snippetRepository) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void init() throws IOException {
        File customSnippets = new File(globals.launchpadDir, "snippet-deploy");
        if (customSnippets.exists()) {
            loadSnippetsRecursevly(customSnippets);
        }
        else {
            log.info("Directory with custom snippets doesn't exist, {}", customSnippets.getPath());
        }

        Resource[] resources  = pathMatchingResourcePatternResolver.getResources("classpath:snippets/*");
        for (Resource resource : resources) {
            log.info("Load snippets as resource from {} ", resource.getFile().getPath());
            loadSnippetsFromDir(resource.getFile());
        }
        persistSnippets();
    }

    private void persistSnippets() throws IOException {
        File snippetDir = new File(globals.launchpadDir, "snippets");
        if (!snippetDir.exists()) {
            snippetDir.mkdirs();
        }

        Iterable<Snippet> snippets = snippetRepository.findAll();
        for (Snippet snippet : snippets) {
            SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, snippet.getSnippetCode(), snippet.filename);
            if (snippetFile.file==null) {
                log.error("Error while persisting snippet {}", snippet.getSnippetCode());
                continue;
            }
            if (!snippetFile.file.exists() || snippetFile.file.length()!=snippet.code.length) {
                log.warn("Snippet {} has different length. On disk - {}, in db - {}. Snippet will be re-created.",snippet.getSnippetCode(), snippetFile.file.length(), snippet.code.length);
                FileUtils.writeByteArrayToFile(snippetFile.file, snippet.code, false);
            }
        }
        //noinspection unused
        int i=0;
    }

    public interface SnippetFilter {
        boolean filter(Snippet snippet);
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<Snippet> snippets, List<ExperimentSnippet> experimentSnippets,
                                                     SnippetFilter snippetFilter) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (Snippet snippet : snippets) {
            boolean isExist=false;
            for (ExperimentSnippet experimentSnippet : experimentSnippets) {
                if (snippet.getSnippetCode().equals(experimentSnippet.getSnippetCode()) ) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (snippetFilter.filter(snippet)) {
                    continue;
                }
                selectOptions.add( new SimpleSelectOption(snippet.getSnippetCode(), String.format("Type: %s; Code: %s:%s", snippet.getType(), snippet.getName(), snippet.getSnippetVersion())));
            }
        }
        return selectOptions;
    }

    public List<ExperimentSnippet> getExperimentSnippets(Iterable<Snippet> snippets, Experiment experiment) {
        List<ExperimentSnippet> experimentSnippets = new ArrayList<>();
        for (Snippet snippet : snippets) {
            for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                if (snippet.getSnippetCode().equals(experimentSnippet.getSnippetCode()) ) {
                    experimentSnippet.type = snippet.type;
                    experimentSnippets.add(experimentSnippet);
                    break;
                }
            }
        }
        return experimentSnippets;
    }

    void loadSnippetsRecursevly(File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);
        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("Load snippets from {}", dir.getPath());
                loadSnippetsFromDir(dir);
                loadSnippetsRecursevly(dir);
            }
        }
    }

    /**
     * load snippets from directory
     *
     * @param srcDir
     * @throws IOException
     */
    private void loadSnippetsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "snippets.yaml");
        if (!yamlConfigFile.exists()) {
            log.warn("File 'snippets.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return;
        }

        try (InputStream is = new FileInputStream(yamlConfigFile)) {
            SnippetsConfig snippetsConfig = SnippetsConfigUtils.to(is);
            for (SnippetsConfig.SnippetConfig snippetConfig : snippetsConfig.snippets) {
                SnippetsConfig.SnippetConfigStatus status = snippetConfig.verify();
                if (!status.isOk) {
                    log.error(status.error);
                    continue;
                }
                File file = new File(srcDir, snippetConfig.file);
                if (!file.exists()) {
                    throw new IllegalStateException("File " + snippetConfig.file+" wasn't found in "+ srcDir.getAbsolutePath());
                }
                byte[] code;
                try( InputStream inputStream = new FileInputStream(file)) {
                    code = IOUtils.toByteArray(inputStream);;
                }
                String sum = Checksum.Type.SHA256.getChecksum(code);

                Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetConfig.name, snippetConfig.version);
                if (snippet!=null) {
                    final String checksum = Checksum.fromJson(snippet.checksum).checksums.get(Checksum.Type.SHA256);
                    if (!sum.equals(checksum)) {
                        if (globals.isReplaceSnapshot && snippetConfig.version.endsWith(Consts.SNAPSHOT_SUFFIX)) {
                            setChecksum(snippetConfig, sum, snippet);
                            snippet.name = snippetConfig.name;
                            snippet.snippetVersion = snippetConfig.version;
                            snippet.type = snippetConfig.type.toString();
                            snippet.filename = snippetConfig.file;
                            snippet.code = code;
                            snippet.env = snippetConfig.env;
                            snippetRepository.save(snippet);
                        }
                        else {
                            log.warn("Checksum mismatch for snippet '{}:{}'", snippet.name, snippet.snippetVersion);
                        }
                    }
                }
                else {
                    snippet = new Snippet();
                    setChecksum(snippetConfig, sum, snippet);
                    snippet.name = snippetConfig.name;
                    snippet.snippetVersion = snippetConfig.version;
                    snippet.type = snippetConfig.type.toString();
                    snippet.filename = snippetConfig.file;
                    try( InputStream inputStream = new FileInputStream(file)) {
                        snippet.code = IOUtils.toByteArray(inputStream);;
                    }
                    snippet.env = snippetConfig.env;
                    snippetRepository.save(snippet);
                }
            }
        }
    }

    private void setChecksum(SnippetsConfig.SnippetConfig snippetConfig, String sum, Snippet snippet) {
        if (snippetConfig.checksums != null) {
            // already defined checksum in snippets.yaml
            Checksum checksum = new Checksum();
            checksum.checksums.putAll(snippetConfig.checksums);
            snippet.checksum = checksum.toJson();
            boolean isSigned = false;
            for (Map.Entry<Checksum.Type, String> entry : snippetConfig.checksums.entrySet()) {
                if (entry.getKey().isSign) {
                    isSigned = true;
                    break;
                }
            }
            snippet.setSigned(isSigned);
        } else {
            // calc the new checksum
            snippet.checksum = new Checksum(Checksum.Type.SHA256, sum).toJson();
            snippet.setSigned(false);
        }
    }

}
