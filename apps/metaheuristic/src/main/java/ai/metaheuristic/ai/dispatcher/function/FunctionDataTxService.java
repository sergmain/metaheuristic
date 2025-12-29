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

import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
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
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionDataTxService {

    private final FunctionDataRepository functionDataRepository;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @Transactional(readOnly = true)
    public void storeToFile(String code, Path trgFile) {
        try {
            dispatcherBlobStorage.accessFunctionData(code, (is)-> {
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

    @Transactional
    public void deleteById(Long id) {
        functionDataRepository.deleteById(id);
    }

    @Transactional
    public void deleteByFunctionCode(String functionCode) {
        functionDataRepository.deleteByFunctionCode(functionCode);
    }

    @Transactional(readOnly = true)
    public Optional<FunctionData> findById(Long id) {
        return functionDataRepository.findById(id);
    }

}
