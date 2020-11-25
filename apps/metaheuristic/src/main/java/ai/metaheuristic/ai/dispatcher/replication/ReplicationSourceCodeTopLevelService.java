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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 11/24/2020
 * Time: 8:06 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationSourceCodeTopLevelService {
    public final ReplicationCoreService replicationCoreService;
    public final ReplicationSourceCodeService replicationSourceCodeService;
    public final SourceCodeRepository sourceCodeRepository;
    public final SourceCodeService sourceCodeService;
    public final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;

    @Data
    @AllArgsConstructor
    public static class SourceCodeLoopEntry {
        public ReplicationData.SourceCodeShortAsset sourceCodeShort;
        public SourceCodeImpl sourceCode;
    }

    // TODO 2020-09-21 Need to create a reconciliation algo for a case when
    //  there is a record in MH_SOURCE_CODE but this SourceCode wasn't registered in Dispatcher
    //  2020-11-24 need more info about that problem case

    public void syncSourceCodes(List<String> actualSourceCodeUids) {
        sourceCodeRepository.findAllSourceCodeUids().stream()
                .filter(s->!actualSourceCodeUids.contains(s))
                .map(sourceCodeRepository::findByUid)
                .filter(Objects::nonNull)
                .forEach(s-> {
                    sourceCodeService.deleteSourceCodeById(s.id, false);
                });

        List<String> currFunctions = sourceCodeRepository.findAllSourceCodeUids();
        actualSourceCodeUids.stream()
                .filter(s->!currFunctions.contains(s))
                .map(this::getSourceCodeAsset)
                .filter(Objects::nonNull)
                .forEach(sourceCodeAsset -> {
                    SourceCodeImpl sc = replicationSourceCodeService.createSourceCode(sourceCodeAsset);
                    if (sc!=null) {
                        dispatcherParamsService.registerSourceCode(sc);
                    }
                });
    }


/*
    public void syncSourceCodes(List<ReplicationData.SourceCodeShortAsset> actualSourceCodes) {
        List<SourceCodeLoopEntry> forUpdating = new ArrayList<>(actualSourceCodes.size());
        LinkedList<ReplicationData.SourceCodeShortAsset> forCreating = new LinkedList<>(actualSourceCodes);

        List<Long> ids = sourceCodeRepository.findAllAsIds();
        for (Long id : ids) {
            SourceCodeImpl p = sourceCodeCache.findById(id);
            if (p==null) {
                continue;
            }

            boolean isDeleted = true;
            for (ReplicationData.SourceCodeShortAsset actualSourceCode : actualSourceCodes) {
                if (actualSourceCode.uid.equals(p.uid)) {
                    isDeleted = false;
                    if (actualSourceCode.updateOn != p.getSourceCodeStoredParamsYaml().internalParams.updatedOn) {
                        SourceCodeLoopEntry sourceCodeLoopEntry = new SourceCodeLoopEntry(actualSourceCode, p);
                        forUpdating.add(sourceCodeLoopEntry);
                    }
                    break;
                }
            }

            if (isDeleted) {
                SourceCodeImpl sc = sourceCodeCache.findById(id);
                if (sc!=null) {
                    dispatcherParamsService.unregisterSourceCode(sc.uid);
                }
                sourceCodeCache.deleteById(id);
            }
            forCreating.removeIf(sourceCodeShortAsset -> sourceCodeShortAsset.uid.equals(p.uid));
        }

        forUpdating.forEach(this::updateSourceCode);
        forCreating.forEach(this::createSourceCode);

        for (SourceCodeLoopEntry sourceCodeLoopEntry : forUpdating) {
            ReplicationData.SourceCodeAsset sourceCodeAsset = getSourceCodeAsset(sourceCodeLoopEntry.sourceCodeShort.uid);
            if (sourceCodeAsset == null) {
                return;
            }

            replicationSourceCodeService.updateSourceCode(sourceCodeLoopEntry, sourceCodeAsset);
        }
        forCreating.stream()
                .map(o-> getCompanyAsset(o.uniqueId))
                .filter(Objects::nonNull)
                .forEach(replicationCompanyService::createCompany);

    }
*/

    @Nullable
    private ReplicationData.SourceCodeAsset getSourceCodeAsset(String sourceCodeUid) {
        ReplicationData.SourceCodeAsset sourceCodeAsset = requestSourceCodeAsset(sourceCodeUid);
        if (sourceCodeAsset.isErrorMessages()) {
            log.error("#308.020 Error while getting sourceCodeUid "+ sourceCodeUid +", error: " + sourceCodeAsset.getErrorMessagesAsStr());
            return null;
        }
        return sourceCodeAsset;
    }

    private ReplicationData.SourceCodeAsset requestSourceCodeAsset(String uid) {
        Object data = replicationCoreService.getData(
                "/rest/v1/replication/source-code", ReplicationData.SourceCodeAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("uid", uid).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.SourceCodeAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.SourceCodeAsset response = (ReplicationData.SourceCodeAsset) data;
        return response;
    }

}
