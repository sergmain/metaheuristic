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

package aiai.ai.launchpad;

import aiai.ai.Globals;
import aiai.ai.beans.Snippet;
import aiai.ai.launchpad.dataset.DatasetUtils;
import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.repositories.SnippetRepository;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;

@Controller
@RequestMapping("/payload")
public class PayloadController {

    private  final SnippetRepository snippetRepository;

    private final Globals globals;

    public PayloadController(SnippetRepository snippetRepository, Globals globals) {
        this.snippetRepository = snippetRepository;
        this.globals = globals;
    }

    @GetMapping("/dataset/{id}")
    public HttpEntity<PathResource> datasets(@PathVariable("id") long datasetId) {

        final File datasetFile = DatasetUtils.getDatasetFile(globals.launchpadDir, datasetId);

        HttpHeaders header = new HttpHeaders();
        header.setContentLength(datasetFile.length());

        return new HttpEntity<>(new PathResource(datasetFile.toPath()), header);
    }

    @GetMapping("/feature/{datasetId}/{featureId}")
    public HttpEntity<PathResource> feature(@PathVariable("datasetId") long datasetId, @PathVariable("featureId") long featureId) {

        final File featureFile = DatasetUtils.getFeatureFile(globals.launchpadDir, datasetId, featureId);

        HttpHeaders header = new HttpHeaders();
        header.setContentLength(featureFile.length());

        return new HttpEntity<>(new PathResource(featureFile.toPath()), header);
    }

    @GetMapping("/snippet/{name}")
    public HttpEntity<String> snippets(@PathVariable("name") String snippetName) {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetName);
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);

        HttpHeaders header = new HttpHeaders();
        header.setContentLength(snippet.code.length());

        return new HttpEntity<>(snippet.code, header);
    }

}
