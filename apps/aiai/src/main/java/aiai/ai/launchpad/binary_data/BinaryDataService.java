/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.binary_data;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.exceptions.BinaryDataSaveException;
import ai.metaheuristic.api.v1.launchpad.BinaryData;
import aiai.ai.launchpad.beans.BinaryDataImpl;
import aiai.ai.launchpad.repositories.BinaryDataRepository;
import aiai.ai.launchpad.launchpad_resource.SimpleResource;
import aiai.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.*;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

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
            BinaryData data = binaryDataRepository.findByCode(code);
            if (data==null) {
                log.warn("#087.14.01 Binary data for code {} wasn't found", code);
                throw new BinaryDataNotFoundException("#087.14 Binary data wasn't found, code: " + code);
            }
            FileUtils.copyInputStreamToFile(data.getData().getBinaryStream(), trgFile);
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "#087.10 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public void deleteByWorkbookId(long workbookId) {
        binaryDataRepository.deleteByWorkbookId(workbookId);
    }

    public void deleteAllByType(EnumsApi.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteAllByDataType(binaryDataType.value);
    }

    public List<SimpleCodeAndStorageUrl> getResourceCodesInPool(List<String> inputResourcePoolCode, long workbookId) {
        return binaryDataRepository.getCodeAndStorageUrlInPool(inputResourcePoolCode, workbookId);
    }

    public List<SimpleCodeAndStorageUrl> getResourceCodesInPool(List<String> inputResourcePoolCode) {
        return binaryDataRepository.getCodeAndStorageUrlInPool(inputResourcePoolCode);
    }

/*
    public List<SimpleCodeAndStorageUrl> getResourceCodes(List<String> inputResourceCode) {
        return binaryDataRepository.getCodeAndStorageUrl(inputResourceCode);
    }
*/

    public void deleteByCodeAndDataType(String code, EnumsApi.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByCodeAndDataType(code, binaryDataType.value);
    }

    public void deleteByPoolCodeAndDataType(String poolCode, EnumsApi.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByPoolCodeAndDataType(poolCode, binaryDataType.value);
    }

    public BinaryData save(InputStream is, long size,
                           EnumsApi.BinaryDataType binaryDataType, String code, String poolCode,
                           boolean isManual, String filename, Long workbookId) {
        if (binaryDataType== EnumsApi.BinaryDataType.SNIPPET && workbookId!=null) {
            String es = "#087.01 Snippet can't be bound to workbook";
            log.error(es);
            throw new BinaryDataSaveException(es);
        }
        try {
            BinaryDataImpl data = binaryDataRepository.findByCode(code);
            if (data == null) {
                data = new BinaryDataImpl();
                data.setType(binaryDataType);
                data.setValid(true);
                data.setCode(code);
                data.setPoolCode(poolCode);
                data.setManual(isManual);
                data.setFilename(filename);
                data.setWorkbookId(workbookId);
                data.setParams(Consts.SOURCING_LAUNCHPAD_PARAMS_STR);
            } else {
                if (!poolCode.equals(data.getPoolCode())) {
                    // this is exception for the case when two resources have the same names but different pool codes
                    String es = "#087.04 Pool code is different, old: " + data.getPoolCode() + ", new: " + poolCode;
                    log.error(es);
                    throw new BinaryDataSaveException(es);
                }
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.launchpad) {
                    // this is exception for the case when two resources have the same names but different pool codes
                    String es = "#087.05 Sourcing must be launchpad, value in db: " + data.getParams();
                    log.error(es);
                    throw new BinaryDataSaveException(es);
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            binaryDataRepository.save(data);

            return data;
        }
        catch(BinaryDataSaveException e) {
            throw e;
        }
        catch(Throwable th) {
            log.error("#087.09 error storing data to db", th);
            throw new BinaryDataSaveException("Error", th);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public BinaryData saveWithSpecificStorageUrl(String resourceCode, String poolCode, String storageUrl) {

        try {
            List<BinaryDataImpl> datas = binaryDataRepository.findAllByPoolCode(poolCode);
            BinaryDataImpl data;
            if (datas.isEmpty()) {
                data = new BinaryDataImpl();
            } else {
                if (datas.size()>1) {
                    String es = "#087.17 Can't create resource with storage url, too many resources are associated with this pool code: " + poolCode;
                    log.error(es);
                    throw new IllegalStateException(es);
                }
                data = datas.get(0);
                if (data.getDataType()!= EnumsApi.BinaryDataType.DATA.value) {
                    String es = "#087.21 Can't create resource with storage url because record has different types: " + EnumsApi.BinaryDataType.from(data.getDataType());
                    log.error(es);
                    throw new IllegalStateException(es);
                }
/*
                if (!data.getCode().equals(resourceCode)) {
                    String es = "#087.24 Can't create resource with storage url because record has different resource codes, " +
                            "in db: " + data.getCode()+", new resource code: " + resourceCode;
                    log.error(es);
                    throw new IllegalStateException(es);
                }
*/
            }
            data.setType(EnumsApi.BinaryDataType.DATA);
            data.setValid(true);
            data.setCode(resourceCode);
            data.setPoolCode(poolCode);
            data.setManual(true);
            data.setFilename(null);
            data.setWorkbookId(null);
            data.setParams(storageUrl);
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setData(null);

            binaryDataRepository.save(data);

            return data;
        }
        catch(IllegalStateException e) {
            throw e;
        }
        catch(Throwable th) {
            log.error("#087.09 error storing data to db", th);
            throw new RuntimeException("Error", th);
        }
    }

    public void update(InputStream is, long size, BinaryDataImpl data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        binaryDataRepository.save(data);
    }

    public Slice<BinaryData> findAll(Pageable pageable) {
        return binaryDataRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        binaryDataRepository.deleteById(id);
    }

    public Optional<BinaryDataImpl> findById(Long id) {
        return binaryDataRepository.findById(id);
    }

    public Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable) {
        return binaryDataRepository.getAllAsSimpleResources(pageable);
    }

    public List<BinaryData> getByPoolCodeAndType(String poolCode, EnumsApi.BinaryDataType type) {
        return binaryDataRepository.findAllByPoolCodeAndDataType(poolCode, type.value);
    }
}
