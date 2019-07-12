/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.binary_data;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.BinaryDataSaveException;
import ai.metaheuristic.api.launchpad.BinaryData;
import ai.metaheuristic.ai.launchpad.beans.BinaryDataImpl;
import ai.metaheuristic.ai.launchpad.repositories.BinaryDataRepository;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleResource;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.*;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static ai.metaheuristic.api.EnumsApi.*;

@Service
@Transactional
@Slf4j
@Profile("launchpad")
public class BinaryDataService {

    private final EntityManager em;
    private final BinaryDataRepository binaryDataRepository;
    private final Globals globals;

    public BinaryDataService(EntityManager em, BinaryDataRepository binaryDataRepository, Globals globals) {
        this.em = em;
        this.binaryDataRepository = binaryDataRepository;
        this.globals = globals;
    }

    @Transactional(readOnly = true)
    public BinaryDataImpl getBinaryData(long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    private BinaryDataImpl getBinaryData(long id, @SuppressWarnings("SameParameterValue") boolean isInitBytes) {
        try {
            BinaryDataImpl data = binaryDataRepository.findById(id).orElse(null);
            if (data==null) {
                return null;
            }
            if (isInitBytes) {
                data.bytes = data.getData().getBytes(1, (int) data.getData().length());
            }
            return data;
        } catch (SQLException e) {
            throw new IllegalStateException("SQL error", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] getDataAsBytes(long id) {
        try {
            BinaryData data = binaryDataRepository.findById(id).orElse(null);
            if (data==null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(data.getData().getBinaryStream(), baos, 20000);
            return baos.toByteArray();
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    public void storeToFile(String code, File trgFile) {
        try {
/*
            // TODO 2019-06-08 need to confirm that new version is working with postgres.
            // after that this part can be deleted
            BinaryData data = binaryDataRepository.findByCode(code);
            if (data==null) {
                log.warn("#087.14.01 Binary data for code {} wasn't found", code);
                throw new BinaryDataNotFoundException("#087.14 Binary data wasn't found, code: " + code);
            }
            FileUtils.copyInputStreamToFile(data.getData().getBinaryStream(), trgFile);
*/
            Blob blob = binaryDataRepository.getDataAsStreamByCode(code);
            if (blob==null) {
                log.warn("#087.010 Binary data for code {} wasn't found", code);
                throw new BinaryDataNotFoundException("#087.010 Binary data wasn't found, code: " + code);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "#087.020 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public void deleteByRefId(long workbookId, BinaryDataRefType refType) {
        binaryDataRepository.deleteByRefIdAndRefType(workbookId, refType.toString());
    }

    public void deleteAllByType(BinaryDataType binaryDataType) {
        binaryDataRepository.deleteAllByDataType(binaryDataType.value);
    }

    @Transactional(readOnly = true)
    public List<SimpleCodeAndStorageUrl> getResourceCodesInPool(List<String> inputResourcePoolCode, Long workbookId) {
        return binaryDataRepository.getCodeAndStorageUrlInPool(inputResourcePoolCode, workbookId);
    }

    @Transactional(readOnly = true)
    public List<SimpleCodeAndStorageUrl> getResourceCodesInPool(List<String> inputResourcePoolCode) {
        return binaryDataRepository.getCodeAndStorageUrlInPool(inputResourcePoolCode);
    }

/*
    public List<SimpleCodeAndStorageUrl> getResourceCodes(List<String> inputResourceCode) {
        return binaryDataRepository.getCodeAndStorageUrl(inputResourceCode);
    }
*/

    public void deleteByCodeAndDataType(String code, BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByCodeAndDataType(code, binaryDataType.value);
    }

    public void deleteByPoolCodeAndDataType(String poolCode, BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByPoolCodeAndDataType(poolCode, binaryDataType.value);
    }

    public BinaryData save(InputStream is, long size,
                           BinaryDataType binaryDataType, String code, String poolCode,
                           boolean isManual, String filename, Long refId, BinaryDataRefType refType) {
        if (binaryDataType== BinaryDataType.SNIPPET && refId!=null) {
            String es = "#087.030 Snippet can't be bound to workbook";
            log.error(es);
            throw new BinaryDataSaveException(es);
        }
        if ((refId==null && refType!=null) || (refId!=null && refType==null)) {
            String es = "#087.040 refId and refType both must be null or both must not be null, " +
                    "refId: #"+refId+", refType: "+refType;
            log.error(es);
            throw new BinaryDataSaveException(es);
        }
        try {
            BinaryDataImpl data = binaryDataRepository.findByCodeForUpdate(code);
            if (data == null) {
                data = new BinaryDataImpl();
                data.setType(binaryDataType);
                data.setValid(true);
                data.setCode(code);
                data.setPoolCode(poolCode);
                data.setManual(isManual);
                data.setFilename(filename);
                data.setRefId(refId);
                data.setRefType(refType!=null ? refType.toString() : null);
                data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.launchpad)));
            } else {
                if (!poolCode.equals(data.getPoolCode())) {
                    // this is exception for the case when two resources have the same names but different pool codes
                    String es = "#087.050 Pool code is different, old: " + data.getPoolCode() + ", new: " + poolCode;
                    log.error(es);
                    throw new BinaryDataSaveException(es);
                }
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!=DataSourcing.launchpad) {
                    // this is an exception for the case when two resources have the same names but different pool codes
                    String es = "#087.060 Sourcing must be launchpad, value in db: " + data.getParams();
                    log.error(es);
                    throw new BinaryDataSaveException(es);
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            binaryDataRepository.saveAndFlush(data);

            return data;
        }
        catch(BinaryDataSaveException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            log.error("#087.070 error storing data to db", th);
            throw new BinaryDataSaveException("Error", th);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public BinaryData saveWithSpecificStorageUrl(String resourceCode, String poolCode, String params) {

        try {
            List<BinaryDataImpl> datas = binaryDataRepository.findAllByPoolCode(poolCode);
            BinaryDataImpl data;
            if (datas.isEmpty()) {
                data = new BinaryDataImpl();
            } else {
                if (datas.size()>1) {
                    String es = "#087.080 Can't create resource with storage url, too many resources are associated with this pool code: " + poolCode;
                    log.error(es);
                    throw new IllegalStateException(es);
                }
                data = datas.get(0);
                if (data.getDataType()!= BinaryDataType.DATA.value) {
                    String es = "#087.090 Can't create resource with storage url because record has different types: " + BinaryDataType.from(data.getDataType());
                    log.error(es);
                    throw new IllegalStateException(es);
                }
            }
            data.setType(BinaryDataType.DATA);
            data.setValid(true);
            data.setCode(resourceCode);
            data.setPoolCode(poolCode);
            data.setManual(true);
            data.setFilename(null);
            data.setRefId(null);
            data.setRefType(null);
            data.setParams(params);
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setData(null);

            binaryDataRepository.saveAndFlush(data);

            return data;
        }
        catch(IllegalStateException e) {
            throw e;
        }
        catch(Throwable th) {
            log.error("#087.100 error storing data to db", th);
            throw new RuntimeException("Error", th);
        }
    }

    public void update(InputStream is, long size, BinaryDataImpl data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        binaryDataRepository.saveAndFlush(data);
    }

    public Page<BinaryDataImpl> findAll(Pageable pageable) {
        return binaryDataRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        binaryDataRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<BinaryDataImpl> findById(Long id) {
        return binaryDataRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable) {
        return binaryDataRepository.getAllAsSimpleResources(pageable);
    }

    @Transactional(readOnly = true)
    public List<Long> getByPoolCodeAndType(String poolCode, BinaryDataType type) {
        return binaryDataRepository.findIdsByPoolCodeAndDataType(poolCode, type.value);
    }

    @Transactional(readOnly = true)
    public String getFilenameByPool1CodeAndType(String poolCode, BinaryDataType type) {
        return binaryDataRepository.findFilenameByPoolCodeAndDataType(poolCode, type.value);
    }
}
