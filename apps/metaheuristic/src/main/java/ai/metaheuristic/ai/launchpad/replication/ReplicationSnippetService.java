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

package ai.metaheuristic.ai.launchpad.replication;

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("launchpad")
public class ReplicationSnippetService {

    public final ReplicationCoreService replicationCoreService;
    public final SnippetRepository snippetRepository;
    public final SnippetCache snippetCache;

    public void syncSnippets(List<String> actualSnippets) {
        snippetRepository.findAllSnippetCodes().parallelStream()
                .filter(s->!actualSnippets.contains(s))
                .map(snippetRepository::findByCode)
                .filter(Objects::nonNull)
                .forEach(s->snippetRepository.deleteById(s.id));

        List<String> currSnippets = snippetRepository.findAllSnippetCodes();
        actualSnippets.parallelStream()
                .filter(s->!currSnippets.contains(s))
                .forEach(this::createSnippet);
    }

    private void createSnippet(String snippetCode) {
        ReplicationData.SnippetAsset snippetAsset = requestSnippetAsset(snippetCode);
        if (snippetAsset.isErrorMessages()) {
            log.error("#308.010 Error while getting snippet "+ snippetCode +", error: " + snippetAsset.getErrorMessagesAsStr());
            return;
        }
        Snippet sn = snippetRepository.findByCode(snippetCode);
        if (sn!=null) {
            return;
        }
        snippetAsset.snippet.id=null;
        snippetCache.save(snippetAsset.snippet);
    }

    public ReplicationData.SnippetAsset requestSnippetAsset(String snippetCode) {
        ReplicationData.ReplicationAsset data = replicationCoreService.getData(
                "/rest/v1/replication/snippet", ReplicationData.SnippetAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("snippetCode", snippetCode).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.SnippetAsset(((ReplicationData.AssetAcquiringError) data).errorMessages);
        }
        ReplicationData.SnippetAsset response = (ReplicationData.SnippetAsset) data;
        return response;
    }

}