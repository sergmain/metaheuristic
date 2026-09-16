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

package ai.metaheuristic.commons.json.meta_storage;

import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.commons.json.versioning_json.JsonForVersioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MH_META_STORAGE_REGISTRY.PARAMS version chain, exercised by round-tripping it.
 *
 * <p>What is actually at risk here is the version stamp: a hand-written getVersion() hidden from the
 * serializer would omit it from the JSON, and version detection on read-back would silently answer 1
 * forever - correct today, wrong the moment a V2 exists. So the emitted string is asserted on, not
 * just the object that comes back from it.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class MetaStorageRegistryParamsUtilsTest {

    private static MetaStorageRegistryParams sample() {
        MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = "Batches of source-file paths found under C:/sandbox/github/derby, 100 paths per record";
        p.producer = "mh-dir-batcher-1.2";
        p.recKeyFormat = "batch-NNNN, 1-based, zero-padded to the width of the batch count";
        p.bodyFormat = "one absolute file path per line";
        p.execContextId = 12L;
        p.function = "mh.asset.dir-batcher_1.0";
        p.consumer = "list keys, take one, do the work, delete the record";
        return p;
    }

    @Test
    public void test_roundTripKeepsEveryField() {
        final String json = MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.toString(sample());

        final MetaStorageRegistryParams actual = MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.to(json);

        assertEquals(sample().desc, actual.desc, "desc must survive the round trip");
        assertEquals("mh-dir-batcher-1.2", actual.producer);
        assertEquals("batch-NNNN, 1-based, zero-padded to the width of the batch count", actual.recKeyFormat);
        assertEquals("one absolute file path per line", actual.bodyFormat);
        assertEquals(12L, actual.execContextId);
        assertEquals("mh.asset.dir-batcher_1.0", actual.function);
        assertEquals("list keys, take one, do the work, delete the record", actual.consumer);
        assertEquals(1, actual.version);
    }

    @Test
    public void test_theEmittedJsonCarriesItsVersion() {
        final String json = MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.toString(sample());

        assertTrue(json.contains("\"version\""), "the version stamp must reach the JSON, was: " + json);
        assertEquals(1, JsonForVersioning.getParamsVersion(json).version,
            "version detection reads the stamp back - without it every stored row would look like v1");
    }

    @Test
    public void test_nullableFieldsMayBeAbsent() {
        MetaStorageRegistryParams p = sample();
        p.execContextId = null;
        p.function = null;
        p.consumer = null;

        final MetaStorageRegistryParams actual =
            MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.to(MetaStorageRegistryParamsUtils.BASE_JSON_UTILS.toString(p));

        assertNull(actual.execContextId, "a table not written by a run has no ExecContext");
        assertNull(actual.function);
        assertNull(actual.consumer);
        assertEquals(sample().desc, actual.desc, "the required fields are unaffected");
    }
}