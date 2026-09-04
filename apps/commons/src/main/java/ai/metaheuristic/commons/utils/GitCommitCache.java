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

package ai.metaheuristic.commons.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.jspecify.annotations.Nullable;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A Processor-side cache of materialized git commits, one directory per sha.
 *
 * <pre>
 * &lt;cacheRoot&gt;/
 *     .tmp-&lt;uuid&gt;/     in-progress extraction, never visible as an entry
 *     &lt;sha&gt;/           a complete, immutable materialization of that commit
 *     &lt;sha&gt;/
 * </pre>
 *
 * <p><b>Why a sha is a good cache key.</b> A commit names an immutable tree, so an entry can never go
 * stale and never needs revalidation or a lock to read. That is the property the whole design rests on.
 *
 * <p><b>Why the tmp-then-rename.</b> A crash halfway through an extraction must not leave a directory
 * that looks like a valid entry. Extraction happens in {@code .tmp-<uuid>/} and the entry appears via a
 * single {@link Files#move} - atomic within a filesystem - so {@code <sha>/} either exists complete or
 * does not exist. No marker file, no fsync ordering.
 *
 * <p><b>Why that also gives concurrency for free.</b> Two Tasks wanting the same new sha each extract
 * into their own tmp dir and both attempt the move. One wins; the loser sees the entry already there,
 * discards its tmp dir and uses the winner's. Two Tasks wanting DIFFERENT shas never touch the same
 * path at all. So no lock is needed here - the only step that needs one is the fetch into the shared
 * object store, which writes to a single repo.
 *
 * <p><b>Why Tasks still get a copy.</b> An entry is immutable in the sense that this cache never
 * rewrites it - but nothing stops an external Function from rewriting the scripts it was given. So a
 * Task runs against a copy in its own dir, and the cache entry stays intact for every other Task.
 *
 * <p>Error code prefix: {@code 01.923.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 12:10 AM
 */
@Slf4j
public class GitCommitCache {

    private static final String TMP_PREFIX = ".tmp-";

    public static Path entryPath(Path cacheRoot, String sha) {
        if (!GtiUtils.isSha(sha)) {
            throw new IllegalStateException("01.923.010 not a sha: " + sha);
        }
        return cacheRoot.resolve(sha);
    }

    /**
     * A QUERY, so it answers rather than throws: anything that isn't a sha simply isn't cached. The write
     * path keeps the strict check in {@link #entryPath} - refusing to CREATE an entry under a bogus name is
     * a different thing from being asked whether one exists.
     */
    public static boolean isCached(Path cacheRoot, @Nullable String sha) {
        return GtiUtils.isSha(sha) && Files.isDirectory(cacheRoot.resolve(sha));
    }

    /**
     * Returns the cache entry for {@code sha}, materializing it first if it isn't there yet.
     *
     * @param extractor writes the content of {@code sha} into the directory it is handed. Production
     *                  passes a git-backed extractor; a test passes a JGit-backed one. Neither is
     *                  standing in for the other - the directory to fill is simply an argument.
     */
    public static Path get(Path cacheRoot, String sha, Consumer<Path> extractor) throws IOException {
        final Path entry = entryPath(cacheRoot, sha);
        if (Files.isDirectory(entry)) {
            return entry;
        }
        Files.createDirectories(cacheRoot);

        final Path tmp = cacheRoot.resolve(TMP_PREFIX + UUID.randomUUID());
        Files.createDirectories(tmp);
        try {
            extractor.accept(tmp);
            try {
                Files.move(tmp, entry, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                throw new IOException("01.923.030 atomic move isn't supported for " + cacheRoot.toAbsolutePath(), e);
            }
            catch (FileSystemException e) {
                // ❗ Losing the race does NOT surface as FileAlreadyExistsException. POSIX rename() onto an
                // existing, NON-EMPTY directory fails with ENOTEMPTY, which arrives here as
                // DirectoryNotEmptyException - and a cache entry is never empty. Deciding by the exception
                // type is therefore wrong; the question is only whether the entry is there now.
                if (!Files.isDirectory(entry)) {
                    throw e;
                }
                // another thread materialized the same sha first. Its content is identical by definition
                // - both extracted the same immutable tree - so the loser discards its tmp and uses it.
                log.info("01.923.020 sha {} was materialized concurrently, using the existing entry", sha);
            }
            return entry;
        }
        finally {
            if (Files.exists(tmp)) {
                PathUtils.deleteDirectory(tmp);
            }
        }
    }

    /**
     * Copies a cache entry into the Task's own dir, because the Function may rewrite what it is given.
     * The subdirectory of the entry to copy comes from GitInfo.path, exactly as targets.src does for a
     * dispatcher-sourced Function.
     */
    public static void copyToTask(Path cacheEntry, @Nullable String pathInRepo, Path taskAssetDir) throws IOException {
        final Path src = pathInRepo==null || pathInRepo.isBlank() || ".".equals(pathInRepo)
            ? cacheEntry
            : cacheEntry.resolve(pathInRepo);
        if (!Files.isDirectory(src)) {
            throw new IOException("01.923.040 path '" + pathInRepo + "' isn't present in the cached commit at " + cacheEntry.toAbsolutePath());
        }
        Files.createDirectories(taskAssetDir);
        PathUtils.copyDirectory(src, taskAssetDir);
    }

    /** Layout of one repo's cache: a bare object store plus the commits materialized out of it. */
    public static Path objectsDir(Path repoRoot) {
        return repoRoot.resolve("objects");
    }

    public static Path commitsDir(Path repoRoot) {
        return repoRoot.resolve("commits");
    }

    /**
     * Unpacks a tar produced by {@code git archive} into {@code target}.
     *
     * <p>Entries are resolved against the target and checked to still be inside it, so a crafted archive
     * cannot write outside the cache.
     */
    public static void untar(Path tarFile, Path target) throws IOException {
        final Path root = target.toAbsolutePath().normalize();
        try (InputStream is = Files.newInputStream(tarFile);
             TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(is))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry())!=null) {
                final Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("01.923.050 archive entry escapes the target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    tar.transferTo(os);
                }
                if ((entry.getMode() & 0100)!=0) {
                    out.toFile().setExecutable(true, false);
                }
            }
        }
    }

    /** Every sha currently cached. Used by the janitor and by tests; never by the read path. */
    public static List<String> cachedShas(Path cacheRoot) throws IOException {
        if (!Files.isDirectory(cacheRoot)) {
            return List.of();
        }
        final List<String> shas = new ArrayList<>();
        try (Stream<Path> stream = Files.list(cacheRoot)) {
            stream.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .filter(GtiUtils::isSha)
                .forEach(shas::add);
        }
        return List.copyOf(shas);
    }

    /** Sweeps tmp dirs a crashed extraction left behind. They are never valid entries. */
    public static int sweepAbandoned(Path cacheRoot) throws IOException {
        if (!Files.isDirectory(cacheRoot)) {
            return 0;
        }
        int count = 0;
        final List<Path> tmps = new ArrayList<>();
        try (Stream<Path> stream = Files.list(cacheRoot)) {
            stream.filter(p -> p.getFileName().toString().startsWith(TMP_PREFIX)).forEach(tmps::add);
        }
        for (Path p : tmps) {
            PathUtils.deleteDirectory(p);
            count++;
        }
        return count;
    }
}
