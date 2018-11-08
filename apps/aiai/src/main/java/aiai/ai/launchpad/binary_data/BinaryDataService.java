package aiai.ai.launchpad.binary_data;

import aiai.ai.Enums;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.repositories.BinaryDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.File;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;

@Service
@Transactional
@Slf4j
@Profile("launchpad")
public class BinaryDataService {

    private final EntityManager em;
    private final BinaryDataRepository binaryDataRepository;

    public BinaryDataService(EntityManager em, BinaryDataRepository binaryDataRepository) {
        this.em = em;
        this.binaryDataRepository = binaryDataRepository;
    }

    public BinaryData getBinaryData(long id) {
        return getBinaryData(id, true);
    }

    public BinaryData getBinaryData(long id, boolean isInitBytes) {
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
                log.warn("Binary data for code {} wasn't found", code);
                throw new BinaryDataNotFoundException("Binary data wasn't found, code: " + code);
            }
            FileUtils.copyInputStreamToFile(data.getData().getBinaryStream(), trgFile);
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public void deleteAllByType(Enums.BinaryDataType binaryDataType) {
        binaryDataRepository.deleteAllByDataType(binaryDataType.value);
    }

    public BinaryData save(InputStream is, long size,
                           Enums.BinaryDataType binaryDataType, String code, String poolCode) {
        BinaryData data = binaryDataRepository.findByCode(code);
        if (data==null) {
            data = new BinaryData();
            data.setDataType(binaryDataType.value);
            data.setValid(true);
            data.setCode(code);
            data.setPoolCode(poolCode);
        }
        else {
            if (!poolCode.equals(data.getPoolCode())) {
                throw new IllegalStateException(
                        "Pool code is different, old: " + data.getPoolCode()+", new: "+ poolCode);
            }
        }
        data.setUpdateTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        binaryDataRepository.save(data);

        return data;
    }

    public void update(InputStream is, long size, BinaryData data) {
        data.setUpdateTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

        binaryDataRepository.save(data);
    }

/*
    public void cloneBinaryData(Long srcRefId, Long trgRefId, Enums.BinaryDataType binaryDataType) throws SQLException {
        BinaryData srcData = binaryDataRepository.findByDataTypeAndRefId(binaryDataType.value, srcRefId);
        if (srcData==null) {
            return;
        }

        BinaryData data = new BinaryData();
        data.setDataType(binaryDataType.value);
        data.setUpdateTs(new Timestamp(System.currentTimeMillis()));
        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class))
                .createBlob(srcData.getData().getBinaryStream(), srcData.getData().length());
        data.setData(blob);

        binaryDataRepository.save(data);
    }
*/
}
