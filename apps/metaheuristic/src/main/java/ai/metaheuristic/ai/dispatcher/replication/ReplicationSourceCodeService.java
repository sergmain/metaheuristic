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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static ai.metaheuristic.ai.dispatcher.replication.ReplicationSourceCodeTopLevelService.SourceCodeLoopEntry;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationSourceCodeService {

    public final ReplicationCoreService replicationCoreService;
    public final SourceCodeRepository sourceCodeRepository;
    public final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;

    @Nullable
    @Transactional
    public SourceCodeImpl createSourceCode(ReplicationData.SourceCodeAsset sourceCodeAsset) {
        SourceCodeImpl p = sourceCodeRepository.findByUid(sourceCodeAsset.sourceCode.uid);
        if (p!=null) {
            return null;
        }

        //noinspection ConstantConditions
        sourceCodeAsset.sourceCode.id=null;
        //noinspection ConstantConditions
        sourceCodeAsset.sourceCode.version=null;

        SourceCodeImpl sc = sourceCodeCache.save(sourceCodeAsset.sourceCode);
        return sc;
    }
}