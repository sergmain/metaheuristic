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

package ai.metaheuristic.ai.repo;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestBinaryDataRepository {

    @Autowired
    private VariableService variableService;

    private Variable d1 = null;
    @AfterEach
    public void after() {
        if (d1!=null) {
            variableService.deleteById(d1.id);
        }
    }

    @Test
    public void test() throws InterruptedException {
        byte[] bytes = "this is very short data".getBytes();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        d1 = variableService.save(inputStream, bytes.length, "test-01","test-file.bin", 10L, "1");

        Timestamp ts = d1.getUploadTs();

        Variable d2 = variableService.getBinaryData(d1.getId());
        assertNotNull(d2);
        assertEquals(d1, d2);
        assertArrayEquals(bytes, d2.bytes);

        // to check timestamp
        Thread.sleep(1100);

        bytes = "another one very short data".getBytes();
        inputStream = new ByteArrayInputStream(bytes);
        variableService.update(inputStream, bytes.length, d2);

        d2 = variableService.getBinaryData(d2.getId());
        assertNotNull(d2);
        assertNotEquals(ts, d2.getUploadTs());
        assertArrayEquals(bytes, d2.bytes);
    }
}
