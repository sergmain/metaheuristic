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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 9:12 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeStateService {

    private final SourceCodeCache sourceCodeCache;

    public void setValidTo(SourceCodeImpl sc, @Nullable Long companyUniqueId, boolean valid) {
        TxUtils.checkTxExists();

        if (companyUniqueId!=null && !companyUniqueId.equals(sc.companyId)) {
            log.warn("#373.020 SourceCode.companyId!=companyUniqueId, sc.id: {}, sc.companyId: {}, companyUniqueId: {}", sc.id, sc.companyId, companyUniqueId);
//            return;
        }

        if (sc.isValid()!=valid) {
            sc.setValid(valid);
            saveInternal(sc);
        }
    }

    @Transactional
    public void setLockedTo(Long sourceCodeId, @Nullable Long companyUniqueId, boolean locked) {
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc==null) {
            return;
        }
        if (companyUniqueId!=null && !companyUniqueId.equals(sc.companyId)) {
            log.warn("#373.040 SourceCode.companyId!=companyUniqueId, sc.id: {}, sc.companyId: {}, companyUniqueId: {}", sc.id, sc.companyId, companyUniqueId);
//            return;
        }
        if (sc.isLocked()!=locked) {
            sc.setLocked(locked);
            saveInternal(sc);
        }
    }

    private void saveInternal(SourceCodeImpl sourceCode) {
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCodeCache.save(sourceCode);
    }

}
