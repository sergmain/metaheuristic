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
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.utils.checksum.Checksum;
import aiai.ai.yaml.snippet.SnippetsConfig;
import aiai.ai.yaml.snippet.SnippetsConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
@Slf4j
public class SnippetController {

    private final Globals globals;
    private final PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver;
    private final SnippetRepository snippetRepository;

    public SnippetController(Globals globals, SnippetRepository snippetRepository) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void init() throws IOException {
        File customSnippets = new File(globals.launchpadDir, "snippets");
        if (customSnippets.exists()) {
            final File[] dirs = customSnippets.listFiles(File::isDirectory);
            if (dirs!=null) {
                for (File dir : dirs) {
                    log.info("Load snippets from {}", dir.getPath());
                    loadSnippetsFromDir(dir);
                }
            }
        }
        else {
            log.info("Directory with custom snippets doesn't exist, {}", customSnippets.getPath());
        }

        Resource[] resources  = pathMatchingResourcePatternResolver.getResources("classpath:snippets/*");
        for (Resource resource : resources) {
            log.info("Load snippets as resource from {} ", resource.getFile().getPath());
            loadSnippetsFromDir(resource.getFile());
        }
    }

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
