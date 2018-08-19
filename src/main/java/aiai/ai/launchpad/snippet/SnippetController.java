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

import aiai.ai.beans.Snippet;
import aiai.ai.repositories.SnippetRepository;
import aiai.ai.utils.Checksum;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Controller
public class SnippetController {

/*
    @Value("${aiai.launchpad.dir}")
    private String launchpadDirAs1String;
    @SuppressWarnings("FieldCanBeLocal")
    private File launchpadDir;
*/
    @Value("#{ T(aiai.ai.utils.EnvProperty).toFile( environment.getProperty('aiai.launchpad.dir' )) }")
    private File launchpadDir;

    @Value("${aiai.launchpad.is-replace-snapshot:#{true}}")
    private boolean isReplaceSnapshot;

    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    private final PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver;
    private final SnippetRepository snippetRepository;

    public SnippetController(SnippetRepository snippetRepository) {
        this.snippetRepository = snippetRepository;
        this.pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void init() throws IOException {
//        this.launchpadDir = toFile(launchpadDirAsString);

        File customSnippets = new File(launchpadDir, "snippets");
        if (customSnippets.exists()) {
            loadSnippetsFromDir(customSnippets);
        }
        else {
            System.out.println("Directory with custom snippets wasn't found");
        }

        Resource[] resources  = pathMatchingResourcePatternResolver.getResources("classpath:snippets/*");
        for (Resource resource : resources) {
            System.out.println(resource);
            loadSnippetsFromDir(resource.getFile());
        }
    }

    private void loadSnippetsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "snippets.yaml");
        if (!yamlConfigFile.exists()) {
            System.out.println("File 'snippets.yaml' wasn't found in dir "+ srcDir.getAbsolutePath());
            return;
        }

        try (InputStream is = new FileInputStream(yamlConfigFile)) {
            SnippetsConfig snippetsConfig = SnippetsConfig.loadSnippetYaml(is);
            for (SnippetsConfig.SnippetConfig snippetConfig : snippetsConfig.snippets) {
                SnippetsConfig.SnippetConfigStatus status = snippetConfig.verify();
                if (!status.isOk) {
                    System.out.println(status.error);
                    continue;
                }
                File file = new File(srcDir, snippetConfig.file);
                if (!file.exists()) {
                    throw new IllegalStateException("File " + snippetConfig.file+" wasn't found in "+ srcDir.getAbsolutePath());
                }
                String code;
                try( InputStream inputStream = new FileInputStream(file)) {
                    code = IOUtils.toString(inputStream, Charsets.UTF_8);;
                }
                String sum = Checksum.Type.SHA256.getChecksum(code);

                Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetConfig.name, snippetConfig.version);
                if (snippet!=null) {
                    if (!Checksum.fromJson(snippet.checksum).checksums.get(Checksum.Type.SHA256).equals(sum)) {
                        if (isReplaceSnapshot && snippetConfig.version.endsWith(SNAPSHOT_SUFFIX)) {
                            snippet.checksum = new Checksum(Checksum.Type.SHA256, sum).toJson();
                            snippet.name = snippetConfig.name;
                            snippet.snippetVersion = snippetConfig.version;
                            snippet.type = snippetConfig.type.toString();
                            snippet.filename = snippetConfig.file;
                            snippet.code = code;
                            snippetRepository.save(snippet);
                        }
                        else {
                            System.out.println(String.format("Checksum mismatch for snippet '%s:%s'", snippet.name, snippet.snippetVersion));
                        }
                    }
                }
                else {
                    snippet = new Snippet();
                    snippet.checksum = new Checksum(Checksum.Type.SHA256, sum).toJson();
                    snippet.name = snippetConfig.name;
                    snippet.snippetVersion = snippetConfig.version;
                    snippet.type = snippetConfig.type.toString();
                    snippet.filename = snippetConfig.file;
                    try( InputStream inputStream = new FileInputStream(file)) {
                        snippet.code = IOUtils.toString(inputStream, Charsets.UTF_8);;
                    }
                    snippetRepository.save(snippet);
                }
            }
        }
    }
}
