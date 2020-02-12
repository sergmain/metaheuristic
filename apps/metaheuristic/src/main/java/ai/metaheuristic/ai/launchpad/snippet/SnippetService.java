/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ai.metaheuristic.ai.launchpad.snippet;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.data.SnippetData;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SimpleSelectOption;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
import ai.metaheuristic.commons.yaml.snippet_list.SnippetConfigListYaml;
import ai.metaheuristic.commons.yaml.snippet_list.SnippetConfigListYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class SnippetService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final SnippetDataService snippetDataService;

    public Snippet findByCode(String snippetCode) {
        Long id = snippetRepository.findIdByCode(snippetCode);
        if (id==null) {
            return null;
        }
        return snippetCache.findById(id);
    }

    public boolean isSnippetVersionOk(int requiredVersion, SourceCodeParamsYaml.SnippetDefForSourceCode snDef) {
        TaskParamsYaml.SnippetConfig sc = getSnippetConfig(snDef);
        return sc != null && (sc.skipParams || requiredVersion <= SnippetCoreUtils.getTaskParamsVersion(sc.metas));
    }

    public static void sortExperimentSnippets(List<Snippet> snippets) {
        snippets.sort(SnippetService::experimentSnippetComparator);
    }

    private static int experimentSnippetComparator(Snippet o1, Snippet o2) {
        if (o1.getType().equals(o2.getType())) {
            return 0;
        }
        switch (o1.getType().toLowerCase()) {
            case CommonConsts.FIT_TYPE:
                return -1;
            case CommonConsts.PREDICT_TYPE:
                return CommonConsts.FIT_TYPE.equals(o2.getType().toLowerCase()) ? 1 : -1;
            case CommonConsts.CHECK_FITTING_TYPE:
                return 1;
            default:
                return 0;
        }
    }

    public List<Snippet> getSnippetsForCodes(List<String> snippetCodes) {
        if (snippetCodes==null || snippetCodes.isEmpty()) {
            return List.of();
        }
        //noinspection UnnecessaryLocalVariable
        List<Snippet> list = snippetRepository.findIdsByCodes(snippetCodes).stream()
                .map(snippetCache::findById)
                .sorted(SnippetService::experimentSnippetComparator)
                .collect(Collectors.toList());
        return list;
    }

    public TaskParamsYaml.SnippetConfig getSnippetConfig(SourceCodeParamsYaml.SnippetDefForSourceCode snippetDef) {
        TaskParamsYaml.SnippetConfig snippetConfig = null;
        if(StringUtils.isNotBlank(snippetDef.code)) {
            Snippet snippet = findByCode(snippetDef.code);
            if (snippet != null) {
                snippetConfig = TaskParamsUtils.toSnippetConfig(snippet.getSnippetConfig(true));
                boolean paramsAsFile = MetaUtils.isTrue(snippetConfig.metas, ConstsApi.META_MH_SNIPPET_PARAMS_AS_FILE_META);
                if (paramsAsFile) {
                    throw new NotImplementedException("mh.snippet-params-as-file==true isn't supported right now");
                }
                if (!snippetConfig.skipParams) {
                    // TODO 2019-10-09 need to handle a case when field 'params'
                    //  contains actual code (mh.snippet-params-as-file==true)
                    if (snippetConfig.params!=null && snippetDef.params!=null) {
                        snippetConfig.params = snippetConfig.params + ' ' + snippetDef.params;
                    }
                    else if (snippetConfig.params == null) {
                        if (snippetDef.params != null) {
                            snippetConfig.params = snippetDef.params;
                        }
                    }
                }
            } else {
                log.warn("#295.010 Can't find snippet for code {}", snippetDef.code);
            }
        }
        return snippetConfig;
    }

    public boolean hasType(List<Snippet> experimentSnippets, String type) {
        if (experimentSnippets ==null || experimentSnippets.isEmpty()) {
            return false;
        }
        for (Snippet snippet : experimentSnippets) {
            if (type.equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<Snippet> snippets, List<SnippetData.SnippetCode> snippetCodes,
                                                     Function<Snippet, Boolean> skip) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (Snippet snippet : snippets) {
            boolean isExist=false;
            for (SnippetData.SnippetCode snippetCode : snippetCodes) {
                if (snippet.getCode().equals(snippetCode.getSnippetCode()) ) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (skip.apply(snippet)) {
                    continue;
                }
                selectOptions.add( new SimpleSelectOption(snippet.getCode(), String.format("Type: %s; Code: %s", snippet.getType(), snippet.getCode())));
            }
        }
        return selectOptions;
    }

    void loadSnippetsRecursively(List<SnippetApiData.SnippetConfigStatus> statuses, File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);

        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("Load snippets from {}", dir.getPath());
                statuses.addAll(loadSnippetsFromDir(dir));
                loadSnippetsRecursively(statuses, dir);
            }
        }
    }

    /**
     * load snippets from directory
     *
     * @param srcDir File
     */
    @SuppressWarnings("Duplicates")
    List<SnippetApiData.SnippetConfigStatus> loadSnippetsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "snippets.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.020 File 'snippets.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return Collections.emptyList();
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        SnippetConfigListYaml snippetConfigList = SnippetConfigListYamlUtils.BASE_YAML_UTILS.to(cfg);
        List<SnippetApiData.SnippetConfigStatus> statuses = new ArrayList<>();
        for (SnippetConfigListYaml.SnippetConfig snippetConfig : snippetConfigList.snippets) {
//            SnippetConfigYaml snippetConfig = SnippetCoreUtils.to(scTemp);
            SnippetApiData.SnippetConfigStatus status = null;
            try {
                status = SnippetCoreUtils.validate(snippetConfig);
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
                                final String es = "#295.030 Snippet has a sourcing as 'launchpad' but file " + snippetConfig.file + " wasn't found.";
                                status = new SnippetApiData.SnippetConfigStatus(false, es);
                                log.warn(es+" Temp dir: " + srcDir.getAbsolutePath());
                                continue;
                            }
                            try (InputStream inputStream = new FileInputStream(file)) {
                                sum = Checksum.getChecksum(EnumsApi.Type.SHA256, inputStream);
                            }
                            snippetConfig.info.length = file.length();
                            break;
                        case station:
                        case git:
                            String s = SnippetCoreUtils.getDataForChecksumWhenGitSourcing(snippetConfig);
                            sum = Checksum.getChecksum(EnumsApi.Type.SHA256, new ByteArrayInputStream(s.getBytes()));
                            break;
                    }
                }

                Snippet snippet = snippetRepository.findByCodeForUpdate(snippetConfig.code);
                // there is a snippet with the same code
                if (snippet!=null) {
                    status = new SnippetApiData.SnippetConfigStatus(false,
                            "#295.040 Updating of snippet isn't supported any more, need to upload a snippet as a new version. Snippet code: "+snippet.code);
                    //noinspection UnnecessaryContinue
                    continue;
                }
                else {
                    snippet = new Snippet();
                    storeSnippet(snippetConfig, sum, file, snippet);
                }
            }
            catch(VariableSavingException e) {
                status = new SnippetApiData.SnippetConfigStatus(false, e.getMessage());
            }
            catch(Throwable th) {
                final String es = "#295.050 Error " + th.getClass().getName() + " while processing snippet '" + snippetConfig.code + "': " + th.toString();
                log.error(es, th);
                status = new SnippetApiData.SnippetConfigStatus(false, es);
            }
            finally {
                statuses.add(status!=null
                        ? status
                        : new SnippetApiData.SnippetConfigStatus(false,
                        "#295.060 MetricsStatus of snippet "+snippetConfig.code+" is unknown, this status needs to be investigated"));
            }
        }
        return statuses;
    }

    private void storeSnippet(SnippetConfigListYaml.SnippetConfig snippetConfig, String sum, File file, Snippet snippet) throws IOException {
        setChecksum(snippetConfig, sum);
        snippet.code = snippetConfig.code;
        snippet.type = snippetConfig.type;
        SnippetConfigYaml scy = SnippetCoreUtils.to(snippetConfig);
        snippet.params = SnippetConfigYamlUtils.BASE_YAML_UTILS.toString(scy);
        snippetCache.save(snippet);
        if (file != null) {
            try (InputStream inputStream = new FileInputStream(file)) {
                String snippetCode = snippet.getCode();
                snippetDataService.save(inputStream, snippetConfig.info.length, snippetCode);
            }
        }
    }

    private void setChecksum(SnippetConfigListYaml.SnippetConfig snippetConfig, String sum) {
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
            for (Map.Entry<EnumsApi.Type, String> entry : snippetConfig.checksumMap.entrySet()) {
                if (entry.getKey().isSign) {
                    isSigned = true;
                    break;
                }
            }
            snippetConfig.info.setSigned(isSigned);
        } else {
            // set the new checksum
            snippetConfig.checksum = new Checksum(EnumsApi.Type.SHA256, sum).toJson();
            snippetConfig.info.setSigned(false);
        }
    }
}
