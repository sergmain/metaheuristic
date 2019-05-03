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
package aiai.ai.launchpad.snippet;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.repositories.ExperimentSnippetRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.CommonConsts;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
public class SnippetService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final BinaryDataService binaryDataService;

    public SnippetService(Globals globals, SnippetRepository snippetRepository, SnippetCache snippetCache, ExperimentSnippetRepository experimentSnippetRepository, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.snippetCache = snippetCache;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.binaryDataService = binaryDataService;
    }

    @PostConstruct
    public void init() {
    }

    public List<ExperimentSnippet> getTaskSnippetsForExperiment(Long experimentId) {
        List<ExperimentSnippet> experimentSnippets = experimentSnippetRepository.findByExperimentId(experimentId);
        ExperimentUtils.sortExperimentSnippets(experimentSnippets);
        return experimentSnippets;
    }

    public List<Snippet> getSnippets(long experimentId) {
        List<ExperimentSnippet> experimentSnippets = experimentSnippetRepository.findByExperimentId(experimentId);
        List<Snippet> snippets = new ArrayList<>();
        for (ExperimentSnippet experimentSnippet : experimentSnippets) {
            Snippet snippet = snippetRepository.findByCode(experimentSnippet.getSnippetCode());
            if (snippet == null) {
                log.error("#295.03 Can't find snippet for code: {}", experimentSnippet.getSnippetCode());
                continue;
            }
            snippets.add(snippet);
        }
        return snippets;
    }

    public SnippetConfig getSnippetConfig(String snippetCode) {
        SnippetConfig snippetConfig = null;
        if(StringUtils.isNotBlank(snippetCode)) {
            Snippet postSnippet = snippetRepository.findByCode(snippetCode);
            if (postSnippet != null) {
                snippetConfig = SnippetConfigUtils.to(postSnippet.params);
            } else {
                log.warn("#295.07 Can't find snippet for code {}", snippetCode);
            }
        }
        return snippetConfig;
    }

    public static void sortSnippetsByType(List<ExperimentSnippet> snippets) {
        snippets.sort(Comparator.comparing(ExperimentSnippet::getType));
    }

    public boolean hasFit(List<ExperimentSnippet> experimentSnippets) {
        if (experimentSnippets ==null || experimentSnippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : experimentSnippets) {
            if (CommonConsts.FIT_TYPE.equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPredict(List<ExperimentSnippet> experimentSnippets) {
        if (experimentSnippets ==null || experimentSnippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : experimentSnippets) {
            if (CommonConsts.PREDICT_TYPE.equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public interface SnippetFilter {
        boolean filter(Snippet snippet);
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<Snippet> snippets, List<SnippetCode> snippetCodes,
                                                     SnippetFilter snippetFilter) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (Snippet snippet : snippets) {
            boolean isExist=false;
            for (SnippetCode snippetCode : snippetCodes) {
                if (snippet.getCode().equals(snippetCode.getSnippetCode()) ) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (snippetFilter.filter(snippet)) {
                    continue;
                }
                selectOptions.add( new SimpleSelectOption(snippet.getCode(), String.format("Type: %s; Code: %s", snippet.getType(), snippet.getCode())));
            }
        }
        return selectOptions;
    }

    public List<ExperimentSnippet> getTaskSnippets(Iterable<Snippet> snippets, Experiment experiment) {
        List<ExperimentSnippet> experimentSnippets = new ArrayList<>();
        List<ExperimentSnippet> tss = getTaskSnippetsForExperiment(experiment.getId());
        for (Snippet snippet : snippets) {
            for (ExperimentSnippet experimentSnippet : tss) {
                if (snippet.getCode().equals(experimentSnippet.getSnippetCode()) ) {
                    // it should be ok without this line but just for sure
                    experimentSnippet.type = snippet.type;
                    experimentSnippets.add(experimentSnippet);
                    break;
                }
            }
            //noinspection unused
            int i=0;
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
     * @param srcDir File
     */
    // TODO 2019.05.03 status of loading of snippet has to be showed on web page
    private void loadSnippetsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "snippets.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.11 File 'snippets.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return;
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        SnippetConfigList snippetConfigList = SnippetConfigListUtils.to(cfg);
        for (SnippetConfig snippetConfig : snippetConfigList.snippets) {
            SnippetConfigStatus status = snippetConfig.validate();
            if (!status.isOk) {
                log.error(status.error);
                continue;
            }
            String sum=null;
            File file = null;
            if (globals.isSnippetChecksumRequired) {
                switch(snippetConfig.sourcing) {
                    case launchpad:
                        file = new File(srcDir, snippetConfig.file);
                        if (!file.exists()) {
                            throw new IllegalStateException("File " + snippetConfig.file + " wasn't found in " + srcDir.getAbsolutePath());
                        }
                        try (InputStream inputStream = new FileInputStream(file)) {
                            sum = Checksum.Type.SHA256.getChecksum(inputStream);
                        }
                        snippetConfig.info.length = file.length();
                        break;
                    case station:
                    case git:
                        String s = "" + snippetConfig.env+", " + snippetConfig.file +" " + snippetConfig.params;
                        sum = Checksum.Type.SHA256.getChecksum(new ByteArrayInputStream(s.getBytes()));
                        break;
                }
            }

            Snippet snippet = snippetRepository.findByCode(snippetConfig.code);
            // there is snippet with the same name:version
            if (snippet!=null) {
                SnippetConfig sc = SnippetConfigUtils.to(snippet.params);

                // new snippet is to replace one which is already in db
                if (globals.isReplaceSnapshot && snippetConfig.code.endsWith(Consts.SNAPSHOT_SUFFIX)) {
                    // there isn't any checksum for current snippet in db
                    if (sc.checksum ==null) {
                        storeSnippet(snippetConfig, sum, file, snippet);
                    }
                    else {
                        final String checksum = Checksum.fromJson(sc.checksum).checksums.get(Checksum.Type.SHA256);
                        // there checksum for current snippet in db isn't equal to new checksum
                        if (!checksum.equals(sum)) {
                            storeSnippet(snippetConfig, sum, file, snippet);
                        }
                    }
                }
                else {
                    log.warn("#295.14 Updating of snippets is prohibited, not a snapshot version '{}'", snippet.code);
                }
            }
            else {
                snippet = new Snippet();
                storeSnippet(snippetConfig, sum, file, snippet);
            }
        }
    }

    private void storeSnippet(SnippetConfig snippetConfig, String sum, File file, Snippet snippet) throws IOException {
        setChecksum(snippetConfig, sum);
        snippet.code = snippetConfig.code;
        snippet.type = snippetConfig.type;
        snippet.params = SnippetConfigUtils.toString(snippetConfig);
        snippetCache.save(snippet);
        if (file != null) {
            try (InputStream inputStream = new FileInputStream(file)) {
                String snippetCode = snippet.getCode();
                binaryDataService.save(inputStream, snippetConfig.info.length, Enums.BinaryDataType.SNIPPET, snippetCode, snippetCode, false, null, null);
            }
        }
    }

    private void setChecksum(SnippetConfig snippetConfig, String sum) {
        if (sum==null) {
            snippetConfig.checksum = null;
            snippetConfig.info.setSigned(false);
            return;
        }

        if (snippetConfig.checksumMap != null) {
            // already defined checksum in snippets.yaml
            Checksum checksum = new Checksum();
            checksum.checksums.putAll(snippetConfig.checksumMap);
            snippetConfig.checksum = checksum.toJson();
            boolean isSigned = false;
            for (Map.Entry<Checksum.Type, String> entry : snippetConfig.checksumMap.entrySet()) {
                if (entry.getKey().isSign) {
                    isSigned = true;
                    break;
                }
            }
            snippetConfig.info.setSigned(isSigned);
        } else {
            // set the new checksum
            snippetConfig.checksum = new Checksum(Checksum.Type.SHA256, sum).toJson();
            snippetConfig.info.setSigned(false);
        }
    }
}
