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

package ai.metaheuristic.ai.launchpad.snippet;

import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.BinaryDataSaveException;
import ai.metaheuristic.ai.launchpad.beans.SnippetBinaryData;
import ai.metaheuristic.ai.launchpad.repositories.SnippetBinaryDataRepository;
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
public class SnippetBinaryDataService {
    private final EntityManager em;
    private final SnippetBinaryDataRepository snippetBinaryDataRepository;

    public void storeToFile(String code, File trgFile) {
        try {
            Blob blob = snippetBinaryDataRepository.getDataAsStreamByCode(code);
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

    public void deleteBySnippetCode(String snippetCode) {
        snippetBinaryDataRepository.deleteBySnippetCode(snippetCode);
    }

    public SnippetBinaryData save(InputStream is, long size, String snippetCode) {
        try {
            SnippetBinaryData data = snippetBinaryDataRepository.findByCodeForUpdate(snippetCode);
            if (data == null) {
                data = new SnippetBinaryData();
                data.setSnippetCode(snippetCode);
                data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.launchpad)));
            } else {
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.launchpad) {
                    // this is an exception for the case when two resources have the same names but different pool codes
                    throw new BinaryDataSaveException("#088.060 Sourcing must be launchpad, value in db: " + data.getParams());
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            snippetBinaryDataRepository.save(data);

            return data;
        }
        catch(BinaryDataSaveException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new BinaryDataSaveException("#088.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public void update(InputStream is, long size, SnippetBinaryData data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        snippetBinaryDataRepository.save(data);
    }

    public void deleteById(Long id) {
        snippetBinaryDataRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SnippetBinaryData> findById(Long id) {
        return snippetBinaryDataRepository.findById(id);
    }

}
