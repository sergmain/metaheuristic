package aiai.ai.launchpad.binary_data;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.repositories.BinaryDataRepository;
import aiai.ai.launchpad.resource.SimpleResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.File;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public BinaryData getBinaryData(long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    private BinaryData getBinaryData(long id, boolean isInitBytes) {
        try {
            BinaryData data = binaryDataRepository.findById(id).orElse(null);
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

    public void storeToFile(String code, File trgFile) {
        try {
            BinaryData data = binaryDataRepository.findByCode(code);
            if (data==null) {
                log.warn("#087.14.01 Binary data for code {} wasn't found", code);
/*
                log.warn("#087.14.02 Current codes:");
                for (String allCode : binaryDataRepository.dumpAllCodes()) {
                    log.warn("\t#087.14.03 {}", allCode);
                }
*/
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

    public void deleteByFlowInstanceId(long flowInstanceId) {
        binaryDataRepository.deleteByFlowInstanceId(flowInstanceId);
    }

    public void deleteAllByType(Enums.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteAllByDataType(binaryDataType.value);
    }

    public List<String> getResourceCodesInPool(String inputResourcePoolCode) {
        return binaryDataRepository.findAllByPoolCode(inputResourcePoolCode)
                .stream().map(BinaryData::getCode).collect(Collectors.toList());
    }

    public void deleteByCodeAndDataType(String code, Enums.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByCodeAndDataType(code, binaryDataType.value);
    }

    public void deleteByPoolCodeAndDataType(String poolCode, Enums.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteByPoolCodeAndDataType(poolCode, binaryDataType.value);
    }

    public BinaryData save(InputStream is, long size,
                           Enums.BinaryDataType binaryDataType, String code, String poolCode,
                           boolean isManual, String filename, Long flowInstanceId) {
        if (binaryDataType== Enums.BinaryDataType.SNIPPET && flowInstanceId!=null) {
            String es = "#087.01 Snippet can't be bound to flow instance";
            log.error(es);
            throw new IllegalStateException(es);
        }
        try {
            BinaryData data = binaryDataRepository.findByCode(code);
            if (data == null) {
                data = new BinaryData();
                data.setType(binaryDataType);
                data.setValid(true);
                data.setCode(code);
                data.setPoolCode(poolCode);
                data.setManual(isManual);
                data.setFilename(filename);
                data.setFlowInstanceId(flowInstanceId);
            } else {
                if (!poolCode.equals(data.getPoolCode())) {
                    // this is exception for the case when two resources have the same names but different pool codes
                    String es = "#087.04 Pool code is different, old: " + data.getPoolCode() + ", new: " + poolCode;
                    log.error(es);
                    throw new IllegalStateException(es);
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

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

    public void update(InputStream is, long size, BinaryData data) {
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

    public Optional<BinaryData> findById(Long id) {
        return binaryDataRepository.findById(id);
    }

    public Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable) {
        return binaryDataRepository.getAllAsSimpleResources(pageable);
    }
}
