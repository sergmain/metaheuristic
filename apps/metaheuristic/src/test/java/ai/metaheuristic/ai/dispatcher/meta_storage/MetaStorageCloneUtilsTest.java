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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pure part of cloning a meta table: the checks that decide whether a clone may run, and the
 * chunked copy itself.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md. {@link MetaStorageCloneUtils#copyRecords} takes the store as
 * two function parameters - production passes the real services through them - and here they are the
 * methods of {@link InMemoryStore}, one honest implementation used unchanged by every test. It keeps
 * its own UNIQUE natural key exactly as the real tables do, so a write that collides fails here for
 * the same reason it would fail in H2. Assertions read what {@code copyRecords} returned and what the
 * store now holds - never how often the store was called.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class MetaStorageCloneUtilsTest {

    /**
     * One store: type -> (recKey -> body), keys kept ordered as the repositories order them.
     *
     * <p>{@link #insert} refuses a natural key that is already there, which is the UNIQUE constraint
     * on {@code (COMPANY_ID, TYPE, REC_KEY)} - a single company here, so the pair is the key.
     */
    static final class InMemoryStore {
        final Map<String, Map<String, String>> tables = new TreeMap<>();

        void put(String type, String recKey, String body) {
            tables.computeIfAbsent(type, t -> new TreeMap<>()).put(recKey, body);
        }

        List<String> keys(String type) {
            return new ArrayList<>(tables.getOrDefault(type, Map.of()).keySet());
        }

        List<MetaStorageData.Record> select(String type, List<String> recKeys) {
            final Map<String, String> table = tables.getOrDefault(type, Map.of());
            final List<MetaStorageData.Record> result = new ArrayList<>();
            for (String k : recKeys) {
                final String body = table.get(k);
                if (body!=null) {
                    result.add(new MetaStorageData.Record(type, k, body));
                }
            }
            return result;
        }

        int insert(List<MetaStorageData.Record> records) {
            for (MetaStorageData.Record r : records) {
                if (tables.getOrDefault(r.type(), Map.of()).containsKey(r.recKey())) {
                    throw new IllegalStateException("unique constraint violated: (" + r.type() + ", " + r.recKey() + ")");
                }
            }
            for (MetaStorageData.Record r : records) {
                put(r.type(), r.recKey(), r.body());
            }
            return records.size();
        }
    }

    private static InMemoryStore storeWith(String type, int records) {
        final InMemoryStore s = new InMemoryStore();
        for (int i = 0; i < records; i++) {
            s.put(type, String.format("k-%03d", i), "body-" + i);
        }
        return s;
    }

    private static MetaStorageCloneUtils.CopyOutcome copy(InMemoryStore source, String sourceType,
                                                          InMemoryStore target, String targetType, int chunkSize) {
        return MetaStorageCloneUtils.copyRecords(source.keys(sourceType), targetType, chunkSize,
                keys -> source.select(sourceType, keys), target::insert);
    }

    // ---------- checkNames ----------

    @Test
    public void test_checkNames_newNameInTheSameStoreIsAClone() {
        assertNull(MetaStorageCloneUtils.checkNames("drone-reqs", true, "drone-reqs-copy", true));
        assertNull(MetaStorageCloneUtils.checkNames("drone-reqs", false, "drone-reqs-copy", false));
    }

    @Test
    public void test_checkNames_sameNameInTheOtherStoreIsACopyBetweenDomains() {
        // two stores, two tables - the ordinary case of a copy between domains
        assertNull(MetaStorageCloneUtils.checkNames("drone-reqs", true, "drone-reqs", false));
        assertNull(MetaStorageCloneUtils.checkNames("drone-reqs", false, "drone-reqs", true));
    }

    @Test
    public void test_checkNames_sameNameInTheSameStoreIsRefused() {
        final String production = MetaStorageCloneUtils.checkNames("drone-reqs", true, "drone-reqs", true);
        assertNotNull(production);
        assertTrue(production.startsWith("01.952.020 "), production);
        assertTrue(production.contains("MH_META_STORAGE"), production);

        final String synthetic = MetaStorageCloneUtils.checkNames("drone-reqs", false, "drone-reqs", false);
        assertNotNull(synthetic);
        assertTrue(synthetic.startsWith("01.952.020 "), synthetic);
        assertTrue(synthetic.contains("MH_META_STORAGE_SYNTHETIC"), synthetic);
    }

    @Test
    public void test_checkNames_aMalformedTargetNameIsRefusedWithTheNameRuleItBroke() {
        final String trailingSpace = MetaStorageCloneUtils.checkNames("drone-reqs", true, "drone-reqs-copy ", true);
        assertNotNull(trailingSpace);
        assertTrue(trailingSpace.startsWith("01.946.020 "), trailingSpace);

        final String leadingDigit = MetaStorageCloneUtils.checkNames("drone-reqs", true, "1-copy", false);
        assertNotNull(leadingDigit);
        assertTrue(leadingDigit.startsWith("01.946.020 "), leadingDigit);
    }

    @Test
    public void test_checkNames_aTargetNameWiderThanTheColumnIsRefused() {
        final String tooLong = "a".repeat(MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH + 1);
        final String error = MetaStorageCloneUtils.checkNames("drone-reqs", true, tooLong, false);
        assertNotNull(error);
        assertTrue(error.startsWith("01.946.040 "), error);

        final String widest = "a".repeat(MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH);
        assertNull(MetaStorageCloneUtils.checkNames("drone-reqs", true, widest, false),
                "a name exactly as wide as the column is legal");
    }

    @Test
    public void test_checkNames_aMalformedTargetIsReportedBeforeTheSelfCloneRule() {
        // both rules broken: the name rule wins, because no store can ever hold that name at all
        final String error = MetaStorageCloneUtils.checkNames("bad name", true, "bad name", true);
        assertNotNull(error);
        assertTrue(error.startsWith("01.946.020 "), error);
    }

    // ---------- checkState ----------

    @Test
    public void test_checkState_aPopulatedSourceAndAnAbsentTargetMayBeCloned() {
        assertNull(MetaStorageCloneUtils.checkState("a", true, "b", true,
                new MetaStorageCloneUtils.ClonePreconditions(3, 0, false)));
    }

    @Test
    public void test_checkState_anEmptySourceIsNothingToClone() {
        final String error = MetaStorageCloneUtils.checkState("a", false, "b", true,
                new MetaStorageCloneUtils.ClonePreconditions(0, 0, false));
        assertNotNull(error);
        assertTrue(error.startsWith("01.952.040 "), error);
        assertTrue(error.contains("'a'") && error.contains("MH_META_STORAGE_SYNTHETIC"),
                "names the source and ITS store: " + error);
    }

    @Test
    public void test_checkState_aPopulatedTargetIsRefusedAndItsSizeReported() {
        final String error = MetaStorageCloneUtils.checkState("a", false, "b", true,
                new MetaStorageCloneUtils.ClonePreconditions(3, 12, false));
        assertNotNull(error);
        assertTrue(error.startsWith("01.952.060 "), error);
        assertTrue(error.contains("'b'") && error.contains("12 record(s)"), error);
        assertTrue(error.contains("in MH_META_STORAGE "), "names the TARGET's store: " + error);
    }

    @Test
    public void test_checkState_aTargetWithOnlyALeftoverDescriptorIsRefused() {
        final String error = MetaStorageCloneUtils.checkState("a", true, "b", true,
                new MetaStorageCloneUtils.ClonePreconditions(3, 0, true));
        assertNotNull(error);
        assertTrue(error.startsWith("01.952.080 "), error);
    }

    @Test
    public void test_checkState_anEmptySourceIsReportedFirst() {
        // with nothing to copy, the state of the target is not the interesting part
        final String error = MetaStorageCloneUtils.checkState("a", true, "b", true,
                new MetaStorageCloneUtils.ClonePreconditions(0, 5, true));
        assertNotNull(error);
        assertTrue(error.startsWith("01.952.040 "), error);
    }

    // ---------- copyRecords ----------

    @Test
    public void test_copyRecords_copiesEveryRecordUnderTheNewNameAcrossSeveralRounds() {
        final InMemoryStore source = storeWith("src", 7);
        final InMemoryStore target = new InMemoryStore();

        final MetaStorageCloneUtils.CopyOutcome outcome = copy(source, "src", target, "dst", 3);

        assertNull(outcome.failure());
        assertEquals(7, outcome.copied());
        assertEquals(source.tables.get("src"), target.tables.get("dst"),
                "every recKey with its body, rounds 3+3+1 included");
        assertFalse(target.tables.containsKey("src"), "the records are written under the TARGET name only");
    }

    @Test
    public void test_copyRecords_aListThatFillsTheLastRoundExactlyLosesNothing() {
        final InMemoryStore source = storeWith("src", 6);
        final InMemoryStore target = new InMemoryStore();

        final MetaStorageCloneUtils.CopyOutcome outcome = copy(source, "src", target, "dst", 3);

        assertNull(outcome.failure());
        assertEquals(6, outcome.copied());
        assertEquals(source.tables.get("src"), target.tables.get("dst"));
    }

    @Test
    public void test_copyRecords_oneRoundWhenTheChunkIsWiderThanTheTable() {
        final InMemoryStore source = storeWith("src", 4);
        final InMemoryStore target = new InMemoryStore();

        final MetaStorageCloneUtils.CopyOutcome outcome = copy(source, "src", target, "dst", MetaStorageCloneUtils.CLONE_CHUNK_SIZE);

        assertNull(outcome.failure());
        assertEquals(4, outcome.copied());
        assertEquals(source.tables.get("src"), target.tables.get("dst"));
    }

    @Test
    public void test_copyRecords_leavesTheSourceUntouched() {
        final InMemoryStore source = storeWith("src", 5);
        final Map<String, String> before = new TreeMap<>(source.tables.get("src"));

        copy(source, "src", new InMemoryStore(), "dst", 2);

        assertEquals(before, source.tables.get("src"));
        assertEquals(List.of("src"), new ArrayList<>(source.tables.keySet()), "nothing was written into the source store");
    }

    @Test
    public void test_copyRecords_bodiesTravelByteForByte() {
        // MH never parses a body, so a clone must not either: not-JSON, trailing newline, non-latin
        final InMemoryStore source = new InMemoryStore();
        source.put("src", "a", "not json at all");
        source.put("src", "b", "{\"k\":1}\n");
        source.put("src", "c", "требование \u2014 тело");
        final InMemoryStore target = new InMemoryStore();

        copy(source, "src", target, "dst", 2);

        assertEquals("not json at all", target.tables.get("dst").get("a"));
        assertEquals("{\"k\":1}\n", target.tables.get("dst").get("b"));
        assertEquals("требование \u2014 тело", target.tables.get("dst").get("c"));
    }

    @Test
    public void test_copyRecords_aKeyDeletedBetweenListingAndReadingIsNotCounted() {
        final InMemoryStore source = storeWith("src", 5);
        final List<String> listed = source.keys("src");
        // the record goes away after the listing was taken, as a draining consumer would do
        source.tables.get("src").remove("k-002");
        final InMemoryStore target = new InMemoryStore();

        final MetaStorageCloneUtils.CopyOutcome outcome = MetaStorageCloneUtils.copyRecords(listed, "dst", 2,
                keys -> source.select("src", keys), target::insert);

        assertNull(outcome.failure());
        assertEquals(4, outcome.copied(), "the outcome counts what was written, not what was listed");
        assertEquals(List.of("k-000", "k-001", "k-003", "k-004"), target.keys("dst"));
    }

    @Test
    public void test_copyRecords_anEmptyListCopiesNothing() {
        final InMemoryStore target = new InMemoryStore();

        final MetaStorageCloneUtils.CopyOutcome outcome = MetaStorageCloneUtils.copyRecords(List.of(), "dst", 3,
                keys -> new InMemoryStore().select("src", keys), target::insert);

        assertNull(outcome.failure());
        assertEquals(0, outcome.copied());
        assertTrue(target.tables.isEmpty());
    }

    @Test
    public void test_copyRecords_aFailingRoundStopsTheCopyAndReportsHowFarItGot() {
        final InMemoryStore source = storeWith("src", 7);
        final InMemoryStore target = new InMemoryStore();
        // a record that appeared in the target after it was checked - k-004 lands in the second round
        target.put("dst", "k-004", "written by someone else");

        final MetaStorageCloneUtils.CopyOutcome outcome = copy(source, "src", target, "dst", 3);

        assertNotNull(outcome.failure());
        assertTrue(outcome.failure().contains("unique constraint violated"), outcome.failure());
        assertEquals(3, outcome.copied(), "only the first round completed");
        // the first round is committed and stays; the failed round wrote nothing; the third never ran
        assertEquals(List.of("k-000", "k-001", "k-002", "k-004"), target.keys("dst"));
        assertEquals("written by someone else", target.tables.get("dst").get("k-004"),
                "a record that was already there is not overwritten");
    }

    @Test
    public void test_copyRecords_aNonPositiveChunkIsRefused() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageCloneUtils.copyRecords(List.of("k"), "dst", 0,
                        keys -> List.of(), records -> 0));
        assertTrue(e.getMessage().startsWith("01.952.100 "), e.getMessage());
    }

    @Test
    public void test_storeName() {
        assertEquals("MH_META_STORAGE", MetaStorageCloneUtils.storeName(true));
        assertEquals("MH_META_STORAGE_SYNTHETIC", MetaStorageCloneUtils.storeName(false));
    }
}
