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

package ai.metaheuristic.ai.dispatcher.storage.variable;

import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobService;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobTxService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.function.BiConsumer;

/**
 * @author Sergio Lissner
 * Date: 6/12/2023
 * Time: 10:07 PM
 */
@Service
@Slf4j
@Profile({"dispatcher"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableDatabaseSpecificCommonService {

    private final VariableRepository variableRepository;
    private final GeneralBlobService generalBlobService;
    private final GeneralBlobTxService generalBlobTxService;
    private final CacheVariableRepository cacheVariableRepository;

    public void copyData(VariableData.StoredVariable storedVariable, TaskParamsYaml.OutputVariable targetVariable, BiConsumer<Long, Long> copyDataFunc) {
        TxUtils.checkTxExists();

        CacheVariable src = cacheVariableRepository.findById(storedVariable.id).orElse(null);
        if (src==null) {
            return;
        }

        Variable trg = variableRepository.findById(targetVariable.id).orElse(null);
        if (trg==null) {
            log.warn("!!! trying to copy date to non-existed variable");
            return;
        }

        trg.variableBlobId = generalBlobService.createVariableIfNotExist(trg.variableBlobId);
/*
        if (trg.variableBlobId==null) {
            trg.variableBlobId = variableBlobTxService.createVariableIfNotExist(null);
            if (trg.variableBlobId==null) {
                throw new IllegalStateException("(trg.variableBlobId==null)");
            }

            // TODO p5 2023-08-17 delete after Oct 1, 2023 if Ok
//            ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
//            dispatcherBlobStorage.storeVariableData(trg.variableBlobId, bais, Consts.STUB_BYTES.length);
        }
*/

        // that's correct - targetVariable.filename
        trg.filename = targetVariable.filename;
        trg.uploadTs = new Timestamp(System.currentTimeMillis());
        trg.inited = true;
        trg.nullified = false;

        variableRepository.save(trg);

        // TODO 2021-10-14 right now, an array variable isn't supported
        copyDataFunc.accept(storedVariable.id, trg.variableBlobId);
    }
}
