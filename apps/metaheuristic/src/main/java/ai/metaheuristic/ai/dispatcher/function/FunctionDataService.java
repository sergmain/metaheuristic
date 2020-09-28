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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * @author Serge
 * Date: 1/23/2020
 * Time: 9:34 PM
 */
@Service
@Transactional
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionDataService {
    private final EntityManager em;
    private final FunctionDataRepository functionDataRepository;

    public void storeToFile(String code, File trgFile) {
        try {
            Blob blob = functionDataRepository.getDataAsStreamByCode(code);
            if (blob==null) {
                log.warn("#088.010 Binary data for code {} wasn't found", code);
                throw new FunctionDataNotFoundException(code, "#088.010 Function data wasn't found, code: " + code);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            String es = "#088.020 Error while storing binary data, error: " + th.getMessage();
            log.error(es, th);
            throw new FunctionDataErrorException(code, es);
        }
    }

    public void deleteById(Long id) {
        functionDataRepository.deleteById(id);
    }

    public void deleteByFunctionCode(String functionCode) {
        functionDataRepository.deleteByFunctionCode(functionCode);
    }

    public FunctionData save(InputStream is, long size, String functionCode) {
        try {
            FunctionData data = functionDataRepository.findByCodeForUpdate(functionCode);
            if (data == null) {
                data = new FunctionData();
                data.setFunctionCode(functionCode);
                data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, functionCode)));
            } else {
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.dispatcher) {
                    // this is an exception for the case when two resources have the same names but different pool codes
                    throw new FunctionDataErrorException(functionCode, "#088.060 Sourcing must be dispatcher, value in db: " + data.getParams());
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            functionDataRepository.save(data);

            return data;
        }
        catch(FunctionDataErrorException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            String es = "#088.070 error storing data to db - " + th.getMessage();
            log.error(es, th);
            throw new FunctionDataErrorException(functionCode, es);
        }
    }

    public void update(InputStream is, long size, FunctionData data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        functionDataRepository.save(data);
    }

    @Transactional(readOnly = true)
    public Optional<FunctionData> findById(Long id) {
        return functionDataRepository.findById(id);
    }

}
