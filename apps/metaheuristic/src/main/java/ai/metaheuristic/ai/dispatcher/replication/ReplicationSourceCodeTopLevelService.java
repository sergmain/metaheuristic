/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTxService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    public final SourceCodeTxService sourceCodeTxService;
    public final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;

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
                    sourceCodeTxService.deleteSourceCodeById(s.id, false);
                    dispatcherParamsTopLevelService.unregisterSourceCode(s.uid);
                });

        List<String> currFunctions = sourceCodeRepository.findAllSourceCodeUids();
        List<SourceCodeImpl> newSourceCodes = new ArrayList<>();
        actualSourceCodeUids.stream()
                .filter(s->!currFunctions.contains(s))
                .map(this::getSourceCodeAsset)
                .filter(Objects::nonNull)
                .forEach(sourceCodeAsset -> {
                    SourceCodeImpl sc = replicationSourceCodeService.createSourceCode(sourceCodeAsset);
                    newSourceCodes.add(sc);
                });

        if (!newSourceCodes.isEmpty()) {
            dispatcherParamsTopLevelService.registerSourceCodes(newSourceCodes);
        }
    }

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
                "/rest/v1/replication/source-code", ReplicationData.SourceCodeAsset.class, List.of(new BasicNameValuePair("uid", uid)),
                (uri) -> Request.get(uri).connectTimeout(Timeout.ofSeconds(5))
//                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.SourceCodeAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.SourceCodeAsset response = (ReplicationData.SourceCodeAsset) data;
        return response;
    }

}
