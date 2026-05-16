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

package ai.metaheuristic.ai.lucene;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.lucene.LuceneBucketLockedException;
import ai.metaheuristic.ai.dispatcher.lucene.LuceneDocument;
import ai.metaheuristic.ai.dispatcher.lucene.LuceneFieldValue;
import ai.metaheuristic.ai.dispatcher.lucene.LuceneHit;
import ai.metaheuristic.ai.dispatcher.lucene.LuceneIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LuceneIndexService}. Each test gets its own temp dir to keep
 * isolation; tests are marked CONCURRENT because the service is fully stateless w.r.t.
 * the filesystem outside its bucket layout.
 *
 * @author Serge
 * Date: 5/15/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class LuceneIndexServiceTest {

    private Path tempStorage;
    private LuceneIndexService svc;

    @BeforeEach
    public void setup() throws Exception {
        tempStorage = Files.createTempDirectory("mh-lucene-test-");
        Globals globals = new Globals();
        globals.dispatcherStoragePath = tempStorage;
        svc = new LuceneIndexService(globals);
    }

    @AfterEach
    public void tearDown() throws Exception {
        svc.shutdown();
        if (Files.exists(tempStorage)) {
            try (var walk = Files.walk(tempStorage)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignore) {}
                });
            }
        }
    }

    // ==================== add / search ====================

    @Test
    public void test_addOrUpdate_and_search_findsTheDocument() {
        String bucket = "test-bucket-1";
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("dispatcher must support priority queues"),
                "projectCode", LuceneFieldValue.keyword("DRONE"),
                "reqId", LuceneFieldValue.stored("DRONE-1")
        )));

        List<LuceneHit> hits = svc.search(bucket, "priority", "text", Set.of(), 10);
        assertEquals(1, hits.size());
        assertEquals("DRONE-1", hits.get(0).docId());
        assertEquals("DRONE-1", hits.get(0).storedFields().get("reqId"));
    }

    @Test
    public void test_addOrUpdate_overwritesExistingDoc() {
        String bucket = "test-bucket-2";
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("original text foobar")
        )));
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("updated text bazqux")
        )));

        assertEquals(0, svc.search(bucket, "foobar", "text", Set.of(), 10).size());
        assertEquals(1, svc.search(bucket, "bazqux", "text", Set.of(), 10).size());
    }

    @Test
    public void test_delete_removesTheDocument() {
        String bucket = "test-bucket-3";
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("alpha beta")
        )));
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-2", Map.of(
                "text", LuceneFieldValue.analyzed("alpha gamma")
        )));
        assertEquals(2, svc.search(bucket, "alpha", "text", Set.of(), 10).size());

        svc.delete(bucket, "DRONE-1");

        List<LuceneHit> hits = svc.search(bucket, "alpha", "text", Set.of(), 10);
        assertEquals(1, hits.size());
        assertEquals("DRONE-2", hits.get(0).docId());
    }

    @Test
    public void test_search_emptyBucket_returnsEmpty() {
        List<LuceneHit> hits = svc.search("does-not-exist", "anything", "text", Set.of(), 10);
        assertEquals(0, hits.size());
    }

    @Test
    public void test_search_keywordFieldFilter() {
        String bucket = "test-bucket-keyword";
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("shared term"),
                "projectCode", LuceneFieldValue.keyword("DRONE")
        )));
        svc.addOrUpdate(bucket, new LuceneDocument("ROBOT-1", Map.of(
                "text", LuceneFieldValue.analyzed("shared term"),
                "projectCode", LuceneFieldValue.keyword("ROBOT")
        )));

        List<LuceneHit> hits = svc.search(bucket, "projectCode:DRONE AND text:shared", "text", Set.of("projectCode"), 10);
        assertEquals(1, hits.size());
        assertEquals("DRONE-1", hits.get(0).docId());
    }

    @Test
    public void test_search_invalidQuerySyntax_throws() {
        String bucket = "test-bucket-invalid";
        svc.addOrUpdate(bucket, new LuceneDocument("DRONE-1", Map.of(
                "text", LuceneFieldValue.analyzed("anything")
        )));
        // Trailing operator -> ParseException -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> svc.search(bucket, "text:foo AND", "text", Set.of(), 10));
    }

    // ==================== rebuildAtomic ====================

    @Test
    public void test_rebuildAtomic_replacesAllDocuments() {
        String bucket = "test-rebuild-1";
        svc.addOrUpdate(bucket, new LuceneDocument("OLD-1", Map.of(
                "text", LuceneFieldValue.analyzed("old content")
        )));
        assertEquals(1, svc.search(bucket, "old", "text", Set.of(), 10).size());

        int written = svc.rebuildAtomic(bucket, Stream.of(
                new LuceneDocument("NEW-1", Map.of(
                        "text", LuceneFieldValue.analyzed("brand new content"))),
                new LuceneDocument("NEW-2", Map.of(
                        "text", LuceneFieldValue.analyzed("more brand new content")))
        ));

        assertEquals(2, written);
        assertEquals(0, svc.search(bucket, "old", "text", Set.of(), 10).size(), "old docs must be gone");
        assertEquals(2, svc.search(bucket, "brand", "text", Set.of(), 10).size(), "new docs must be searchable");
    }

    @Test
    public void test_rebuildAtomic_emptyStream_clearsBucket() {
        String bucket = "test-rebuild-empty";
        svc.addOrUpdate(bucket, new LuceneDocument("OLD-1", Map.of(
                "text", LuceneFieldValue.analyzed("old content")
        )));

        int written = svc.rebuildAtomic(bucket, Stream.empty());

        assertEquals(0, written);
        assertEquals(0, svc.search(bucket, "old", "text", Set.of(), 10).size());
    }

    @Test
    public void test_rebuildAtomic_rejectsConcurrentRebuildOnSameBucket() throws Exception {
        String bucket = "test-rebuild-concurrent";
        // First rebuild blocks on a latch; second rebuild must be rejected with the locked exception.
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        Stream<LuceneDocument> blockingStream = Stream.<LuceneDocument>of(
                new LuceneDocument("BLOCK-1", Map.of(
                        "text", LuceneFieldValue.analyzed("blocking"))))
                .peek(d -> {
                    firstStarted.countDown();
                    try { releaseFirst.await(); } catch (InterruptedException ignore) {}
                });

        Thread t = new Thread(() -> {
            try {
                svc.rebuildAtomic(bucket, blockingStream);
            } catch (Throwable ex) {
                firstError.set(ex);
            }
        });
        t.start();
        firstStarted.await();

        // Second concurrent rebuild on the same bucket -> rejected
        assertTrue(svc.isRebuildInProgress(bucket));
        LuceneBucketLockedException ex = assertThrows(LuceneBucketLockedException.class,
                () -> svc.rebuildAtomic(bucket, Stream.of(
                        new LuceneDocument("OTHER-1", Map.of(
                                "text", LuceneFieldValue.analyzed("other"))))));
        assertNotNull(ex.getMessage());

        releaseFirst.countDown();
        t.join(10_000);
        assertFalse(svc.isRebuildInProgress(bucket));
        assertNull(firstError.get());

        // After unlock, a new rebuild is allowed.
        int written = svc.rebuildAtomic(bucket, Stream.of(
                new LuceneDocument("FRESH-1", Map.of(
                        "text", LuceneFieldValue.analyzed("fresh content")))));
        assertEquals(1, written);
        assertEquals(1, svc.search(bucket, "fresh", "text", Set.of(), 10).size());
    }

    // ==================== addBatch ====================

    @Test
    public void test_addBatch_addsAllDocuments() {
        String bucket = "test-addbatch-1";
        int written = svc.addBatch(bucket, Stream.of(
                new LuceneDocument("S1:DRONE-1", Map.of(
                        "text", LuceneFieldValue.analyzed("alpha content"))),
                new LuceneDocument("S1:DRONE-2", Map.of(
                        "text", LuceneFieldValue.analyzed("alpha other")))
        ));
        assertEquals(2, written);
        assertEquals(2, svc.search(bucket, "alpha", "text", Set.of(), 10).size());
    }

    @Test
    public void test_addBatch_isAdditive_doesNotWipeExisting() {
        String bucket = "test-addbatch-additive";
        svc.addBatch(bucket, Stream.of(
                new LuceneDocument("S1:DRONE-1", Map.of(
                        "text", LuceneFieldValue.analyzed("first batch term")))));
        svc.addBatch(bucket, Stream.of(
                new LuceneDocument("S2:DRONE-1", Map.of(
                        "text", LuceneFieldValue.analyzed("second batch term")))));

        // Both batches must coexist — addBatch appends, never wipes.
        assertEquals(2, svc.search(bucket, "term", "text", Set.of(), 10).size());
    }

    @Test
    public void test_addBatch_isIdempotent_sameDocIdOverwrites() {
        String bucket = "test-addbatch-idempotent";
        svc.addBatch(bucket, Stream.of(
                new LuceneDocument("S1:DRONE-1", Map.of(
                        "text", LuceneFieldValue.analyzed("original wording")))));
        // Re-run the same docId with different text — must overwrite, not duplicate.
        svc.addBatch(bucket, Stream.of(
                new LuceneDocument("S1:DRONE-1", Map.of(
                        "text", LuceneFieldValue.analyzed("revised wording")))));

        assertEquals(0, svc.search(bucket, "original", "text", Set.of(), 10).size());
        assertEquals(1, svc.search(bucket, "revised", "text", Set.of(), 10).size());
        // Exactly one doc with that docId.
        assertEquals(1, svc.search(bucket, "wording", "text", Set.of(), 10).size());
    }

    @Test
    public void test_addBatch_emptyStream_isNoOp() {
        String bucket = "test-addbatch-empty";
        int written = svc.addBatch(bucket, Stream.empty());
        assertEquals(0, written);
    }

    // Helper because Assertions.assertNull is sometimes shadowed by an import
    private static void assertNull(Object o) {
        if (o != null) {
            throw new AssertionError("expected null but was: " + o);
        }
    }
}
