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

package ai.metaheuristic.ai.launchpad.function;

import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.launchpad.beans.FunctionData;
import ai.metaheuristic.ai.launchpad.repositories.FunctionDataRepository;
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
@Profile("launchpad")
@RequiredArgsConstructor
public class FunctionDataService {
    private final EntityManager em;
    private final FunctionDataRepository functionDataRepository;

    public void storeToFile(String code, File trgFile) {
        try {
            Blob blob = functionDataRepository.getDataAsStreamByCode(code);
            if (blob==null) {
                log.warn("#088.010 Binary data for code {} wasn't found", code);
                throw new BinaryDataNotFoundException("#088.010 Binary data wasn't found, code: " + code);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "#088.020 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
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
                data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.launchpad)));
            } else {
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.launchpad) {
                    // this is an exception for the case when two resources have the same names but different pool codes
                    throw new VariableSavingException("#088.060 Sourcing must be launchpad, value in db: " + data.getParams());
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            functionDataRepository.save(data);

            return data;
        }
        catch(VariableSavingException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#088.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public void update(InputStream is, long size, FunctionData data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        functionDataRepository.save(data);
    }

    public void deleteById(Long id) {
        functionDataRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<FunctionData> findById(Long id) {
        return functionDataRepository.findById(id);
    }

}
