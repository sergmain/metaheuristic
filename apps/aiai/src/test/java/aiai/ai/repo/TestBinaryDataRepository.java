package aiai.ai.repo;

import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.BinaryDataRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestBinaryDataRepository {

    @Autowired
    private BinaryDataService binaryDataService;

    @Test
    public void testFeatureCompletionWithAllError() throws SQLException, InterruptedException {
        byte[] bytes = "this is very short data".getBytes();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        BinaryData d1 = binaryDataService.save(inputStream, bytes.length, 1L, BinaryData.Type.TEST);

        Timestamp ts = d1.getUpdateTs();

        BinaryData d2 = binaryDataService.getBinaryData(d1.getId());
        assertNotNull(d2);
        assertEquals(d1, d2);
        assertArrayEquals(bytes, d2.bytes);

        // to check timestamp
        Thread.sleep(1100);

        bytes = "another one very short data".getBytes();
        inputStream = new ByteArrayInputStream(bytes);
        binaryDataService.update(inputStream, bytes.length, d2);

        d2 = binaryDataService.getBinaryData(d2.getId());
        assertNotNull(d2);
        assertNotEquals(ts, d2.getUpdateTs());
        assertArrayEquals(bytes, d2.bytes);

        binaryDataService.deleteAllByType(BinaryData.Type.TEST);

    }

}
