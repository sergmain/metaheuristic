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
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.beans.SnippetBase;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.SnippetBaseRepository;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.snippet.SnippetUtils;
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SnippetService {

    private final Globals globals;
    private final SnippetBaseRepository snippetBaseRepository;
    private final BinaryDataService binaryDataService;

    public SnippetService(Globals globals, SnippetBaseRepository snippetBaseRepository, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.snippetBaseRepository = snippetBaseRepository;
        this.binaryDataService = binaryDataService;
    }

    @PostConstruct
    public void init() throws IOException {
        if (globals.isStoreDataToDisk()) {
            persistSnippets();
        }
    }

    private void persistSnippets() throws IOException {
        File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
        if (!snippetDir.exists()) {
            snippetDir.mkdirs();
        }

        Iterable<SnippetBase> snippets = snippetBaseRepository.findAll();
        for (SnippetBase snippet : snippets) {
            persistConcreteSnippet(snippetDir, snippet);
        }
        //noinspection unused
        int i=0;
    }

    public File persistSnippet(String snippetCode) throws IOException {
        File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
        if (!snippetDir.exists()) {
            snippetDir.mkdirs();
        }
        SnippetVersion sv = SnippetVersion.from(snippetCode);
        SnippetBase snippet = snippetBaseRepository.findByNameAndSnippetVersion(sv.name, sv.version);
        //noinspection UnnecessaryLocalVariable
        File file = persistConcreteSnippet(snippetDir, snippet);
        return file;
    }

    private File persistConcreteSnippet(File snippetDir, SnippetBase snippet) {
        SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, snippet.getSnippetCode(), snippet.filename);
        if (snippetFile.file==null) {
            log.error("Error while persisting a snippet {}", snippet.getSnippetCode());
            return null;
        }
        if (!snippetFile.file.exists() || snippetFile.file.length()!=snippet.length) {
            log.warn("Snippet {} has the different length. On disk - {}, in db - {}. Snippet will be re-created.",snippet.getSnippetCode(), snippetFile.file.length(), snippet.length);
            SnippetBase s = snippetBaseRepository.findById(snippet.getId()).orElse(null);
            if (s==null) {
                throw new IllegalStateException("Can't find a snippet for Id " + snippet.getId()+", but base snippet is there");
            }
            binaryDataService.storeToFile(snippet.getId(), BinaryData.Type.SNIPPET, snippetFile.file);
        }
        return snippetFile.file;
    }

    public interface SnippetFilter {
        boolean filter(SnippetBase snippet);
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<SnippetBase> snippets, List<SnippetCode> snippetCodes,
                                                     SnippetFilter snippetFilter) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (SnippetBase snippet : snippets) {
            boolean isExist=false;
            for (SnippetCode snippetCode : snippetCodes) {
                if (snippet.getSnippetCode().equals(snippetCode.getSnippetCode()) ) {
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

    public List<ExperimentSnippet> getExperimentSnippets(Iterable<SnippetBase> snippets, Experiment experiment) {
        List<ExperimentSnippet> experimentSnippets = new ArrayList<>();
        for (SnippetBase snippet : snippets) {
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

    void loadSnippetsRecursively(File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);
        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("Load snippets from {}", dir.getPath());
                loadSnippetsFromDir(dir);
                loadSnippetsRecursively(dir);
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

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        SnippetsConfig snippetsConfig = SnippetsConfigUtils.to(cfg);
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
            String sum;
            try( InputStream inputStream = new FileInputStream(file)) {
                sum = Checksum.Type.SHA256.getChecksum(inputStream);
            }

            SnippetBase snippet = snippetBaseRepository.findByNameAndSnippetVersion(snippetConfig.name, snippetConfig.version);
            if (snippet!=null) {
                final String checksum = Checksum.fromJson(snippet.checksum).checksums.get(Checksum.Type.SHA256);
                if (!sum.equals(checksum)) {
                    if (globals.isReplaceSnapshot && snippetConfig.version.endsWith(Consts.SNAPSHOT_SUFFIX)) {
                        setChecksum(snippetConfig, sum, snippet);
                        snippet.name = snippetConfig.name;
                        snippet.snippetVersion = snippetConfig.version;
                        snippet.type = snippetConfig.type.toString();
                        snippet.filename = snippetConfig.file;
                        snippet.length = file.length();
                        snippet.env = snippetConfig.env;
                        snippet.params = snippetConfig.params;
                        snippetBaseRepository.save(snippet);
                        try( InputStream inputStream = new FileInputStream(file)) {
                            binaryDataService.save(inputStream, snippet.length, snippet.getId(), BinaryData.Type.SNIPPET);
                        }
                    }
                    else {
                        log.warn("Updating of snippets is prohibited, not a snapshot version '{}:{}'", snippet.name, snippet.snippetVersion);
                    }
                }
            }
            else {
                snippet = new SnippetBase();
                setChecksum(snippetConfig, sum, snippet);
                snippet.name = snippetConfig.name;
                snippet.snippetVersion = snippetConfig.version;
                snippet.type = snippetConfig.type.toString();
                snippet.filename = snippetConfig.file;
                snippet.length = file.length();
                snippet.env = snippetConfig.env;
                snippet.params = snippetConfig.params;
                snippetBaseRepository.save(snippet);
                try( InputStream inputStream = new FileInputStream(file)) {
                    binaryDataService.save(inputStream, snippet.length, snippet.getId(), BinaryData.Type.SNIPPET);
                }
            }
        }
    }

    private void setChecksum(SnippetsConfig.SnippetConfig snippetConfig, String sum, SnippetBase snippet) {
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
