/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.storage;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.dispatcher.storage.DatabaseBlobPersistService;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WORM (write-once) guard of the default DB blob backend.
 *
 * <p>Every case here uses the create-stub-then-store shape with NO {@code MH_VARIABLE} row
 * referencing the blob, because that is how the blob backend is actually driven in production:
 * a caller allocates a stub via {@code createEmptyVariable()} and then writes into it via
 * {@code storeVariableData(...)}, holding the resulting {@code variableBlobId} in its OWN table.
 * The blob is therefore reachable only through {@code variableBlobId}, and the guard cannot rely
 * on anything outside {@code MH_VARIABLE_BLOB} to decide whether that blob is already
 * materialized.
 *
 * <p>Characterization of the defect recorded on 2026-08-16: the guard compared the stored length
 * against the one-byte placeholder a pre-created row used to carry, so exactly one byte of real
 * content was indistinguishable from a freshly pre-created stub and could be silently over-written.
 * The placeholder is gone - a pre-created row now leaves DATA null - and IS_MATERIALIZED records
 * the fact the comparison used to infer.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class DatabaseBlobPersistServiceWormGuardTest extends MhSharedItTest {

    @Autowired private DatabaseBlobPersistService databaseBlobPersistService;
    @Autowired private GeneralBlobTxService generalBlobTxService;
    @Autowired private VariableBlobRepository variableBlobRepository;
    @Autowired private PlatformTransactionManager txManager;

    /**
     * The defect itself: one byte of real content, then a second store against the same blob.
     * The stored length equals the stub length, so the length comparison cannot tell them apart.
     */
    @Test
    public void test_storeVariable_secondStoreOfOneByteContent_isRejected() {
        final Long blobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);

        databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(new byte[]{'A'}), 1, DispatcherBlobStorage.KIND_MH);
        assertArrayEquals(new byte[]{'A'}, readBlob(blobId));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(new byte[]{'B'}), 1, DispatcherBlobStorage.KIND_MH));

        assertTrue(e.getMessage().startsWith("174.045"), e.getMessage());
        assertArrayEquals(new byte[]{'A'}, readBlob(blobId));
    }

    /**
     * Control: same scenario, content longer than the stub. Green throughout - this is the
     * protection that already works today and that must not be lost by any fix.
     */
    @Test
    public void test_storeVariable_secondStoreOfMultiByteContent_isRejected() {
        final Long blobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);
        final byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(content), content.length, DispatcherBlobStorage.KIND_MH);

        final byte[] second = "world".getBytes(StandardCharsets.UTF_8);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(second), second.length, DispatcherBlobStorage.KIND_MH));

        assertTrue(e.getMessage().startsWith("174.045"), e.getMessage());
        assertArrayEquals(content, readBlob(blobId));
    }

    /**
     * The guard must not over-fire: a fresh stub accepts its one and only real store.
     *
     * <p>A pre-created row carries no data at all - readBlob maps the null DATA to an empty array -
     * and is not materialized. Those two facts together are what "fresh stub" now means; there is no
     * placeholder content to recognise it by.
     */
    @Test
    public void test_storeVariable_firstStoreIntoFreshStub_isAllowed() {
        final Long blobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);
        assertArrayEquals(new byte[0], readBlob(blobId));
        final Boolean materialized = new TransactionTemplate(txManager).execute(
                status -> variableBlobRepository.findById(blobId).orElseThrow().isMaterialized());
        assertEquals(Boolean.FALSE, materialized);

        final byte[] content = "first-store".getBytes(StandardCharsets.UTF_8);
        databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(content), content.length, DispatcherBlobStorage.KIND_MH);

        assertArrayEquals(content, readBlob(blobId));
    }

    /**
     * The guard must not over-fire on size either: a legal 1-byte variable is still a legal FIRST
     * store into a fresh stub.
     */
    @Test
    public void test_storeVariable_firstStoreOfOneByteContent_isAllowed() {
        final Long blobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);

        databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(new byte[]{'Z'}), 1, DispatcherBlobStorage.KIND_MH);

        assertArrayEquals(new byte[]{'Z'}, readBlob(blobId));
    }

    /**
     * createVariableWithData writes the row together with its data, so the result is materialized
     * on arrival and a later store against it must be refused.
     */
    @Test
    public void test_createVariableWithData_thenStore_isRejected() {
        final byte[] content = "created-with-data".getBytes(StandardCharsets.UTF_8);
        final Long blobId = databaseBlobPersistService.createVariableWithData(
                new ByteArrayInputStream(content), content.length, DispatcherBlobStorage.KIND_MH);

        final byte[] second = "overwrite".getBytes(StandardCharsets.UTF_8);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> databaseBlobPersistService.storeVariable(blobId, new ByteArrayInputStream(second), second.length, DispatcherBlobStorage.KIND_MH));

        assertTrue(e.getMessage().startsWith("174.045"), e.getMessage());
        assertArrayEquals(content, readBlob(blobId));
    }

    private byte[] readBlob(Long blobId) {
        return new TransactionTemplate(txManager).execute(status -> {
            VariableBlob vb = variableBlobRepository.findById(blobId).orElseThrow();
            Blob b = vb.getData();
            if (b == null) {
                return new byte[0];
            }
            try {
                return b.getBytes(1, (int) b.length());
            }
            catch (SQLException e) {
                throw new IllegalStateException("can't read VariableBlob #" + blobId, e);
            }
        });
    }
}
