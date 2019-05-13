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

package aiai.ai.repo;

import aiai.ai.launchpad.beans.BinaryDataImpl;
import metaheuristic.api.v1.launchpad.BinaryData;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import metaheuristic.api.v1.EnumsApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestBinaryDataRepository {

    @Autowired
    private BinaryDataService binaryDataService;

    @Test
    public void testFeatureCompletionWithAllError() throws SQLException, InterruptedException {
        byte[] bytes = "this is very short data".getBytes();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        BinaryData d1 = binaryDataService.save(inputStream, bytes.length, EnumsApi.BinaryDataType.TEST, "test-01", "test-01",
                true, "test-file.bin", null);

        Timestamp ts = d1.getUploadTs();

        BinaryDataImpl d2 = binaryDataService.getBinaryData(d1.getId());
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
        assertNotEquals(ts, d2.getUploadTs());
        assertArrayEquals(bytes, d2.bytes);

        binaryDataService.deleteAllByType(EnumsApi.BinaryDataType.TEST);

    }

}
