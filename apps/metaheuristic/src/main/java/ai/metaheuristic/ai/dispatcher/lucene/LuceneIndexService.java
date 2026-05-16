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

package ai.metaheuristic.ai.dispatcher.lucene;

import ai.metaheuristic.ai.Globals;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generic, tenant-agnostic Lucene index manager.
 *
 * Design notes:
 *
 *   • An "index bucket" is identified by an opaque string and stored on disk under
 *     {@code <dispatcherStoragePath>/lucene-indexes/<bucket>}. The bucket name is
 *     restricted to [A-Za-z0-9._-]+ to keep it safe as a directory name; callers are
 *     responsible for composing per-tenant/per-project keys (e.g. "rg-7-DRONE").
 *
 *   • MH knows nothing about RG / requirements / governance. The schema of each
 *     indexed document is fully determined by the caller via {@link LuceneDocument}
 *     and {@link LuceneFieldValue}. The only reserved field name is {@code _docId},
 *     a non-analyzed StringField used as the per-bucket update/delete key.
 *
 *   • Searches use the standard Lucene query parser. Callers pass a query string
 *     and the default field name to use when the query contains bare terms.
 *
 *   • A {@link SearcherManager} per bucket caches open readers and is refreshed
 *     after every mutation (addOrUpdate / delete) and on rebuild swap.
 *
 *   • {@link #rebuildAtomic(String, Stream)} performs a zero-downtime rebuild:
 *     it writes a new index to {@code <bucket>.next}, then closes the live
 *     SearcherManager, performs an atomic directory swap, opens a fresh
 *     SearcherManager, and deletes the old directory. Reads against the bucket
 *     continue against the old SearcherManager until the swap moment; the
 *     read-side window where the manager is rebuilt is bounded by the time to
 *     open a single IndexReader on the new dir (milliseconds).
 *
 *   • Concurrent rebuild on the same bucket is rejected with
 *     {@link LuceneBucketLockedException}. Per-bucket rebuild lock state lives in
 *     {@link #activeRebuilds}.
 *
 * @author Serge
 * Date: 5/15/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class LuceneIndexService {

    /** Reserved field name used as the per-bucket update/delete term. */
    public static final String DOC_ID_FIELD = "_docId";

    /** Sub-directory under dispatcher storage path where all buckets live. */
    public static final String LUCENE_ROOT_DIR = "lucene-indexes";

    /** Suffix for the in-flight rebuild directory (sibling of the live bucket dir). */
    private static final String NEXT_SUFFIX = ".next";

    /** Suffix used during atomic swap; deleted asynchronously after a successful swap. */
    private static final String OLD_SUFFIX = ".old";

    /** Bucket-name validation pattern — keeps directory names safe. */
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final Globals globals;

    /** Live SearcherManager per bucket — opened lazily on first read. */
    private final Map<String, SearcherManager> searchers = new ConcurrentHashMap<>();

    /**
     * Per-bucket write-op flag — presence means a bucket-level write operation
     * (rebuildAtomic or addBatch) is in progress for that bucket. Lucene allows
     * only one IndexWriter per directory, so both write paths share this guard;
     * a second concurrent write on the same bucket is rejected with
     * {@link LuceneBucketLockedException}.
     */
    private final Map<String, Boolean> activeBucketOps = new ConcurrentHashMap<>();

    /** Cached Analyzer instance — StandardAnalyzer is thread-safe and immutable. */
    private final Analyzer analyzer = new StandardAnalyzer();

    // ==================== Public API ====================

    /**
     * Add or replace a single document in the bucket. If a document with the same
     * docId exists, it is overwritten; otherwise a new one is created.
     *
     * Implementation note: each call opens an IndexWriter, commits, and refreshes
     * the SearcherManager. For bulk inserts during a full reindex, prefer
     * {@link #rebuildAtomic(String, Stream)}.
     */
    @SneakyThrows
    public void addOrUpdate(String bucket, LuceneDocument doc) {
        validateBucket(bucket);
        if (doc.docId() == null || doc.docId().isEmpty()) {
            throw new IllegalArgumentException("docId must not be null or empty");
        }
        Path bucketDir = bucketPath(bucket);
        ensureBucketInitialized(bucketDir);
        try (Directory dir = openDir(bucketDir);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            Document luceneDoc = toLuceneDocument(doc);
            writer.updateDocument(new Term(DOC_ID_FIELD, doc.docId()), luceneDoc);
            writer.commit();
        }
        refreshSearcher(bucket);
    }

    /**
     * Delete a document from the bucket. No-op if the bucket doesn't exist or the
     * docId isn't present.
     */
    @SneakyThrows
    public void delete(String bucket, String docId) {
        validateBucket(bucket);
        Path bucketDir = bucketPath(bucket);
        if (!Files.isDirectory(bucketDir)) {
            return;
        }
        try (Directory dir = openDir(bucketDir);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            writer.deleteDocuments(new Term(DOC_ID_FIELD, docId));
            writer.commit();
        }
        refreshSearcher(bucket);
    }

    /**
     * Run a Lucene-syntax query against the bucket. Returns at most {@code maxResults}
     * hits ranked by relevance.
     *
     * @param bucket         the bucket key
     * @param luceneQuery    standard Lucene query syntax (boolean, fuzzy, proximity, fields)
     * @param defaultField   field name to use for bare terms in the query (e.g. "text")
     * @param keywordFields  field names that were indexed as KEYWORD; QueryParser will NOT
     *                       run the analyzer over terms targeted at these fields, so the
     *                       stored case is preserved. Pass an empty set if none.
     * @param maxResults     upper bound on results returned
     * @return list of hits; empty if the bucket doesn't exist yet
     */
    @SneakyThrows
    public List<LuceneHit> search(String bucket, String luceneQuery, String defaultField,
                                  Set<String> keywordFields, int maxResults) {
        validateBucket(bucket);
        if (maxResults <= 0) {
            return List.of();
        }
        Path bucketDir = bucketPath(bucket);
        if (!Files.isDirectory(bucketDir)) {
            return List.of();
        }
        SearcherManager mgr = getOrOpenSearcher(bucket);
        if (mgr == null) {
            return List.of();
        }
        Analyzer queryAnalyzer = keywordFields == null || keywordFields.isEmpty()
                ? analyzer
                : buildQueryAnalyzer(keywordFields);
        QueryParser parser = new QueryParser(defaultField, queryAnalyzer);
        Query query;
        try {
            query = parser.parse(luceneQuery);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid Lucene query: " + e.getMessage(), e);
        }
        IndexSearcher searcher = mgr.acquire();
        try {
            TopDocs topDocs = searcher.search(query, maxResults);
            List<LuceneHit> out = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                String docId = d.get(DOC_ID_FIELD);
                Map<String, String> storedFields = new HashMap<>();
                for (IndexableField f : d.getFields()) {
                    if (!DOC_ID_FIELD.equals(f.name()) && f.stringValue() != null) {
                        storedFields.put(f.name(), f.stringValue());
                    }
                }
                out.add(new LuceneHit(docId, sd.score, storedFields));
            }
            return out;
        } finally {
            mgr.release(searcher);
        }
    }

    /**
     * Returns true if the bucket has a directory on disk. Does not guarantee the
     * index is non-empty or healthy.
     */
    public boolean bucketExists(String bucket) {
        validateBucket(bucket);
        return Files.isDirectory(bucketPath(bucket));
    }

    /**
     * Returns true if a rebuild is currently running for this bucket.
     */
    public boolean isRebuildInProgress(String bucket) {
        validateBucket(bucket);
        return activeBucketOps.containsKey(bucket);
    }

    /**
     * Atomic, zero-downtime rebuild. The supplied stream is drained into a brand
     * new index at {@code <bucket>.next}; on completion the live bucket directory
     * is atomically swapped and the live SearcherManager is reopened. Reads
     * against the bucket continue uninterrupted against the old SearcherManager
     * until the swap moment.
     *
     * <p>If a rebuild is already running for the same bucket, throws
     * {@link LuceneBucketLockedException}. Caller must catch and translate
     * appropriately.
     *
     * <p>The stream is closed by this method.
     *
     * @return number of documents written to the new index
     */
    @SneakyThrows
    public int rebuildAtomic(String bucket, Stream<LuceneDocument> docs) {
        validateBucket(bucket);
        Boolean prev = activeBucketOps.putIfAbsent(bucket, Boolean.TRUE);
        if (prev != null) {
            throw new LuceneBucketLockedException("A write operation is already in progress for bucket: " + bucket);
        }
        Path nextDir = nextBucketPath(bucket);
        Path liveDir = bucketPath(bucket);
        Path oldDir = oldBucketPath(bucket);
        int written = 0;
        try {
            deleteDirectoryIfExists(nextDir);
            Files.createDirectories(nextDir);
            try (Directory dir = openDir(nextDir);
                 IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
                 Stream<LuceneDocument> s = docs) {
                List<LuceneDocument> batch = s.toList();
                for (LuceneDocument d : batch) {
                    writer.addDocument(toLuceneDocument(d));
                    written++;
                }
                writer.commit();
            }
            // Atomic swap. Close any live searcher first so the OS can release file handles
            // before the directory is moved (matters on Windows; harmless on Linux).
            closeSearcher(bucket);
            deleteDirectoryIfExists(oldDir);
            if (Files.isDirectory(liveDir)) {
                Files.move(liveDir, oldDir, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(nextDir, liveDir, StandardCopyOption.ATOMIC_MOVE);
            // Reopen searcher against the new live dir. Best-effort: failure here just
            // means the next read call will open it lazily.
            try {
                getOrOpenSearcher(bucket);
            } catch (Throwable t) {
                log.warn("Failed to pre-open searcher after rebuild for bucket {}: {}", bucket, t.getMessage());
            }
            deleteDirectoryIfExists(oldDir);
            log.info("Lucene bucket '{}' rebuilt with {} document(s)", bucket, written);
            return written;
        } finally {
            activeBucketOps.remove(bucket);
        }
    }

    /**
     * Append a batch of documents to a bucket in place, without wiping it.
     *
     * <p>This is the incremental write path — contrast with {@link #rebuildAtomic}
     * which replaces the whole bucket. Each document is written with
     * {@code updateDocument} keyed on its {@code _docId}, so re-adding a document
     * with a docId that already exists overwrites it rather than duplicating it;
     * callers can therefore safely re-run a batch (idempotent).
     *
     * <p>Zero-downtime: readers continue to see the pre-commit view via the
     * cached {@link SearcherManager} until {@code maybeRefresh()} runs after the
     * single commit. There is no directory swap and no read-side gap.
     *
     * <p>Shares the per-bucket write lock with {@link #rebuildAtomic}; a
     * concurrent write on the same bucket is rejected with
     * {@link LuceneBucketLockedException}.
     *
     * <p>The stream is closed by this method.
     *
     * @return number of documents written
     */
    @SneakyThrows
    public int addBatch(String bucket, Stream<LuceneDocument> docs) {
        validateBucket(bucket);
        Boolean prev = activeBucketOps.putIfAbsent(bucket, Boolean.TRUE);
        if (prev != null) {
            throw new LuceneBucketLockedException("A write operation is already in progress for bucket: " + bucket);
        }
        try {
            Path bucketDir = bucketPath(bucket);
            ensureBucketInitialized(bucketDir);
            int written = 0;
            try (Directory dir = openDir(bucketDir);
                 IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
                 Stream<LuceneDocument> s = docs) {
                List<LuceneDocument> batch = s.toList();
                for (LuceneDocument d : batch) {
                    if (d.docId() == null || d.docId().isEmpty()) {
                        throw new IllegalArgumentException("docId must not be null or empty");
                    }
                    writer.updateDocument(new Term(DOC_ID_FIELD, d.docId()), toLuceneDocument(d));
                    written++;
                }
                writer.commit();
            }
            refreshSearcher(bucket);
            log.info("Lucene bucket '{}' received a batch of {} document(s)", bucket, written);
            return written;
        } finally {
            activeBucketOps.remove(bucket);
        }
    }

    // ==================== Lifecycle ====================

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, SearcherManager> e : searchers.entrySet()) {
            try {
                e.getValue().close();
            } catch (Throwable t) {
                log.warn("Failed to close SearcherManager for bucket {}: {}", e.getKey(), t.getMessage());
            }
        }
        searchers.clear();
    }

    // ==================== Internals ====================

    private static void validateBucket(String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("bucket must not be null or empty");
        }
        if (!BUCKET_NAME_PATTERN.matcher(bucket).matches()) {
            throw new IllegalArgumentException("bucket name must match [A-Za-z0-9._-]+: " + bucket);
        }
    }

    private Path luceneRoot() {
        return globals.dispatcherStoragePath.resolve(LUCENE_ROOT_DIR);
    }

    private Path bucketPath(String bucket) {
        return luceneRoot().resolve(bucket);
    }

    private Path nextBucketPath(String bucket) {
        return luceneRoot().resolve(bucket + NEXT_SUFFIX);
    }

    private Path oldBucketPath(String bucket) {
        return luceneRoot().resolve(bucket + OLD_SUFFIX);
    }

    @SneakyThrows
    private void ensureBucketInitialized(Path bucketDir) {
        if (!Files.isDirectory(bucketDir)) {
            Files.createDirectories(bucketDir);
            // Write an empty initial commit so a SearcherManager can be opened later.
            try (Directory dir = openDir(bucketDir);
                 IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
                writer.commit();
            }
        }
    }

    @SneakyThrows
    private static Directory openDir(Path bucketDir) {
        return new NIOFSDirectory(bucketDir);
    }

    @SneakyThrows
    private Optional<SearcherManager> tryOpenSearcher(String bucket) {
        Path bucketDir = bucketPath(bucket);
        if (!Files.isDirectory(bucketDir)) {
            return Optional.empty();
        }
        try {
            Directory dir = openDir(bucketDir);
            if (!DirectoryReader.indexExists(dir)) {
                dir.close();
                return Optional.empty();
            }
            return Optional.of(new SearcherManager(dir, null));
        } catch (IOException e) {
            log.warn("Failed to open SearcherManager for bucket {}: {}", bucket, e.getMessage());
            return Optional.empty();
        }
    }

    private SearcherManager getOrOpenSearcher(String bucket) {
        SearcherManager existing = searchers.get(bucket);
        if (existing != null) {
            return existing;
        }
        synchronized (searchers) {
            SearcherManager again = searchers.get(bucket);
            if (again != null) {
                return again;
            }
            Optional<SearcherManager> opt = tryOpenSearcher(bucket);
            if (opt.isEmpty()) {
                return null;
            }
            searchers.put(bucket, opt.get());
            return opt.get();
        }
    }

    @SneakyThrows
    private void refreshSearcher(String bucket) {
        SearcherManager mgr = searchers.get(bucket);
        if (mgr != null) {
            mgr.maybeRefresh();
        }
    }

    private void closeSearcher(String bucket) {
        SearcherManager mgr = searchers.remove(bucket);
        if (mgr != null) {
            try {
                mgr.close();
            } catch (Throwable t) {
                log.warn("Error closing SearcherManager for bucket {}: {}", bucket, t.getMessage());
            }
        }
    }

    @SneakyThrows
    private static void deleteDirectoryIfExists(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", p, e.getMessage());
                }
            });
        }
    }

    /**
     * Build a per-field analyzer that uses {@link KeywordAnalyzer} for the supplied
     * keyword field names (so QueryParser does not tokenize/lowercase their terms)
     * and falls back to the standard analyzer for everything else. The result
     * matches how the documents were originally indexed: KEYWORD fields stored
     * via StringField (unanalyzed), ANALYZED fields via TextField (standard analyzer).
     */
    private Analyzer buildQueryAnalyzer(Set<String> keywordFields) {
        Map<String, Analyzer> perField = new HashMap<>();
        KeywordAnalyzer kw = new KeywordAnalyzer();
        for (String f : keywordFields) {
            perField.put(f, kw);
        }
        return new PerFieldAnalyzerWrapper(analyzer, perField);
    }

    private static Document toLuceneDocument(LuceneDocument input) {
        Document d = new Document();
        // docId is always stored + indexed as a non-analyzed term so updateDocument / deleteDocuments
        // can use it as the unique key.
        d.add(new StringField(DOC_ID_FIELD, input.docId(), Field.Store.YES));
        for (Map.Entry<String, LuceneFieldValue> e : input.fields().entrySet()) {
            String name = e.getKey();
            if (DOC_ID_FIELD.equals(name)) {
                continue;
            }
            LuceneFieldValue v = e.getValue();
            if (v == null || v.value() == null) {
                continue;
            }
            switch (v.kind()) {
                case ANALYZED -> d.add(new TextField(name, v.value(), Field.Store.NO));
                case KEYWORD  -> d.add(new StringField(name, v.value(), Field.Store.NO));
                case STORED   -> d.add(new StoredField(name, v.value()));
            }
        }
        return d;
    }
}
