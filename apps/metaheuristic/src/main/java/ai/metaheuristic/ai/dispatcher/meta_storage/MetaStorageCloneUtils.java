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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Cloning one meta table into a new one, as plain functions.
 *
 * <p>Two shapes, one operation. Inside a domain the clone stays in the store the source lives in and
 * gets a new name; between domains it crosses from MH_META_STORAGE to MH_META_STORAGE_SYNTHETIC or the
 * other way, under any name the caller chooses - the same one included. Both are "copy every record of
 * (companyId, source) in store A to (companyId, target) in store B", so both are one function with
 * the two stores as parameters.
 *
 * <p>❗ A clone CREATES a table. It never merges into an existing one and never relabels one: the target
 * must hold no record and no descriptor. A clone that wrote into a populated table would leave a table
 * no run ever produced - half the records from one place, half from another, and nothing recording
 * which is which. Run output is corrected by a new address, never by writing over an old one, and a
 * clone is held to the same rule.
 *
 * <p>❗ A target with a descriptor but no records is refused too. That descriptor describes a table that
 * no longer exists, and silently overwriting it with the source's would bury the one sign that someone
 * forgot to drop it - and silently KEEPING it would label the clone with someone else's description.
 *
 * <p>Spring-free and JPA-free on purpose: the store is reached only through the function parameters,
 * so the chunking and the renaming are exercised as plain functions and production passes the real
 * services through the same parameters.
 *
 * <p>Error code prefix: {@code 01.952.} (unique to this class).
 *
 * @author Serge
 */
public final class MetaStorageCloneUtils {

    /**
     * Keys per read-and-write round. Equal to the IN-clause chunk of the services, so one round is one
     * select and one write transaction - a table of any size is copied in many small transactions
     * rather than one that grows with the table.
     */
    public static final int CLONE_CHUNK_SIZE = MetaStorageService.MAX_KEYS_PER_QUERY;

    private MetaStorageCloneUtils() {
    }

    /**
     * What the stores said about source and target before anything was written.
     *
     * @param sourceRecords       records under the source name in the source store
     * @param targetRecords       records under the target name in the target store
     * @param targetHasDescriptor whether the target store holds a registry descriptor for the target name
     */
    public record ClonePreconditions(long sourceRecords, long targetRecords, boolean targetHasDescriptor) {}

    /**
     * The outcome of copying the records.
     *
     * @param copied  records the writer reported as written, summed over the rounds that completed
     * @param failure null when every round completed; otherwise the message of the failure that stopped
     *                the copy. ❗ The rounds before it are NOT undone - each was its own transaction - so
     *                {@code copied} is then the size of an INCOMPLETE target.
     */
    public record CopyOutcome(int copied, @Nullable String failure) {}

    /** The physical table a store flag selects, for messages. */
    public static String storeName(boolean production) {
        return production ? "MH_META_STORAGE" : "MH_META_STORAGE_SYNTHETIC";
    }

    /**
     * The checks that need nothing from the database. Run first, so a malformed request reads nothing.
     *
     * @return null when the names allow a clone, otherwise why they do not
     */
    @Nullable
    public static String checkNames(String sourceTable, boolean sourceProduction, String targetTable, boolean targetProduction) {
        try {
            MetaStorageNameUtils.validateMetaTableName(targetTable);
        }
        catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        // Same store AND same name is the only pairing that is not a clone. The same name in the other
        // store is the ordinary case of a copy between domains - two stores, two tables.
        if (sourceProduction==targetProduction && sourceTable.equals(targetTable)) {
            return "01.952.020 Meta table '" + sourceTable + "' can't be cloned onto itself in " + storeName(sourceProduction)
                    + ". Choose another name, or the other store.";
        }
        return null;
    }

    /**
     * The checks that need the current state of both stores.
     *
     * @return null when the clone may proceed, otherwise why it may not
     */
    @Nullable
    public static String checkState(String sourceTable, boolean sourceProduction, String targetTable, boolean targetProduction,
                                    ClonePreconditions p) {
        // A table exists only because records carry its name, so a source with none is not a table -
        // even if a descriptor for it is still lying around.
        if (p.sourceRecords()==0) {
            return "01.952.040 Meta table '" + sourceTable + "' holds no records in " + storeName(sourceProduction)
                    + ", there is nothing to clone.";
        }
        if (p.targetRecords()>0) {
            return "01.952.060 Meta table '" + targetTable + "' already exists in " + storeName(targetProduction)
                    + " with " + p.targetRecords() + " record(s). A clone creates a new table and never writes into an existing one.";
        }
        if (p.targetHasDescriptor()) {
            return "01.952.080 Meta table '" + targetTable + "' has no records in " + storeName(targetProduction)
                    + " but still has a registry descriptor. Drop that table first, so its descriptor goes with it.";
        }
        return null;
    }

    /**
     * Copy the records named by {@code recKeys}, round by round, renaming each to {@code targetTable}.
     *
     * <p>recKey and body travel unchanged - MH never parses a body, so a clone has no business reading
     * one either. Only the type changes, because the type IS the table.
     *
     * <p>A key that the reader no longer finds - a record deleted between listing and reading - is
     * simply absent from that round. The outcome counts what was written, not what was listed.
     *
     * <p>❗ A failing round stops the copy and is reported, not thrown: by then earlier rounds are
     * committed, and the one fact the caller cannot recover afterwards is how far the copy got.
     *
     * @param sourceReader the records of one round's keys, from the source store
     * @param targetWriter writes one round into the target store and returns how many records it wrote
     */
    public static CopyOutcome copyRecords(List<String> recKeys, String targetTable, int chunkSize,
                                          Function<List<String>, List<MetaStorageData.Record>> sourceReader,
                                          ToIntFunction<List<MetaStorageData.Record>> targetWriter) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("01.952.100 chunkSize must be positive, was: " + chunkSize);
        }
        int copied = 0;
        for (int from = 0; from < recKeys.size(); from += chunkSize) {
            final List<String> chunk = recKeys.subList(from, Math.min(from + chunkSize, recKeys.size()));
            try {
                final List<MetaStorageData.Record> read = sourceReader.apply(chunk);
                final List<MetaStorageData.Record> renamed = new ArrayList<>(read.size());
                for (MetaStorageData.Record r : read) {
                    renamed.add(new MetaStorageData.Record(targetTable, r.recKey(), r.body()));
                }
                if (!renamed.isEmpty()) {
                    copied += targetWriter.applyAsInt(renamed);
                }
            }
            catch (Throwable th) {
                return new CopyOutcome(copied, th.getMessage()==null ? th.toString() : th.getMessage());
            }
        }
        return new CopyOutcome(copied, null);
    }
}
