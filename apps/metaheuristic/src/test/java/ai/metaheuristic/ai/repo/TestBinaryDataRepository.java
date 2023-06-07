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
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
public class TestBinaryDataRepository {

    @Autowired
    private VariableTxService variableTxService;

    @Autowired
    private TxSupportForTestingService txSupportForTestingService;

    @Autowired
    private VariableRepository variableRepository;

    private Variable var1 = null;
    @AfterEach
    public void after() {
        if (var1!=null) {
            variableRepository.deleteById(var1.id);
        }
    }

    @Test
    public void test() throws InterruptedException {
        final byte[] bytes = "this is very short data".getBytes();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        var1 = ExecContextSyncService.getWithSync(10L,
                ()-> variableTxService.createInitializedWithTx(
                        inputStream, bytes.length, "test-01","test-file.bin", 10L, Consts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.binary));

        Timestamp ts = var1.getUploadTs();

        final SimpleVariable var2 = variableTxService.getVariableAsSimple(var1.getId());
        assertNotNull(var2);
        final byte[] bytesVar2 = variableTxService.getVariableAsBytes(var1.getId());
        assertArrayEquals(bytes, bytesVar2);

        // to check timestamp
        Thread.sleep(1100);

        final byte[] bytes2 = "another one very short data".getBytes();
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(bytes2);
        ExecContextSyncService.getWithSyncVoid(10L,
                ()-> VariableSyncService.getWithSyncVoidForCreation(var2.id,
                        ()-> variableTxService.updateWithTx(inputStream2, bytes2.length, var2.id)));

        final Variable var3 = variableRepository.findById(var2.getId()).orElse(null);
        assertNotNull(var3);
        assertNotEquals(ts, var3.getUploadTs());

        final byte[] bytesVar3 = variableTxService.getVariableAsBytes(var1.getId());
        assertArrayEquals(bytes2, bytesVar3);
    }
}
