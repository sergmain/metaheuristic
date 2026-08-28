/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Optional;

/**
 * @author Serge
 * Date: 1/23/2020
 * Time: 9:34 PM
 *
 * <p>Error code prefix: {@code 01.087.} (unique to this class).
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionDataTxService {

    private final DispatcherBlobStorage dispatcherBlobStorage;
    private final FunctionRepository functionRepository;

    @Transactional(readOnly = true)
    public void storeToFile(String code, Path trgFile) {
        try {
            // translating a function code into a VariableBlob id belongs here, not in the storage
            // backends: a Function's payload is an ordinary VariableBlob, so each backend was
            // repeating the same lookup only to end up calling its own variable read. Resolve once,
            // then use the variable path directly.
            final Long variableBlobId = functionRepository.findVariableBlobIdByCode(code);
            if (variableBlobId==null) {
                throw new FunctionDataErrorException(code, "01.087.020 Function " + code + " has no stored payload");
            }
            dispatcherBlobStorage.accessVariableData(variableBlobId, (is)-> {
                DirUtils.copy(is, trgFile);
                //noinspection unused
                int k=0;
            });
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            String es = "087.040 Error while storing binary data, error: " + th.getMessage();
            log.error(es, th);
            throw new FunctionDataErrorException(code, es);
        }
    }

}
