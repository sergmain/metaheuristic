/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestBinaryDataRepository {

    @Autowired
    private VariableService variableService;

    @Autowired
    private TxSupportForTestingService txSupportForTestingService;

    @Autowired
    private VariableRepository variableRepository;

    private Variable d1 = null;
    @AfterEach
    public void after() {
        if (d1!=null) {
            variableRepository.deleteById(d1.id);
        }
    }

    @Test
    public void test() throws InterruptedException {
        final byte[] bytes = "this is very short data".getBytes();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        d1 = ExecContextSyncService.getWithSync(10L,
                ()-> txSupportForTestingService.createInitializedWithTx(inputStream, bytes.length, "test-01","test-file.bin", 10L, Consts.TOP_LEVEL_CONTEXT_ID));

        Timestamp ts = d1.getUploadTs();

        final Variable d2 = txSupportForTestingService.getVariableWithData(d1.getId());
        assertNotNull(d2);
        assertEquals(d1, d2);
        assertArrayEquals(bytes, d2.bytes);

        // to check timestamp
        Thread.sleep(1100);

        final byte[] bytes2 = "another one very short data".getBytes();
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(bytes2);
        ExecContextSyncService.getWithSyncNullable(10L,
                ()-> variableService.updateWithTx(inputStream2, bytes2.length, d2.id));

        final Variable d3 = txSupportForTestingService.getVariableWithData(d2.getId());

        assertNotNull(d3);
        assertNotEquals(ts, d3.getUploadTs());
        assertArrayEquals(bytes2, d3.bytes);
    }
}
