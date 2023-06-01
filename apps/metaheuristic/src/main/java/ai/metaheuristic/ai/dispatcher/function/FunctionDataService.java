/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Blob;
import java.util.Optional;

/**
 * @author Serge
 * Date: 1/23/2020
 * Time: 9:34 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionDataService {

    private final FunctionDataRepository functionDataRepository;

    @Transactional(readOnly = true)
    public void storeToFile(String code, Path trgFile) {
        try {
            Blob blob = functionDataRepository.getDataAsStreamByCode(code);
            if (blob==null) {
                log.warn("#088.010 Binary data for code {} wasn't found", code);
                throw new FunctionDataNotFoundException(code, "#088.010 Function data wasn't found, code: " + code);
            }
            try (InputStream is = blob.getBinaryStream()) {
                DirUtils.copy(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            String es = "#088.020 Error while storing binary data, error: " + th.getMessage();
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
