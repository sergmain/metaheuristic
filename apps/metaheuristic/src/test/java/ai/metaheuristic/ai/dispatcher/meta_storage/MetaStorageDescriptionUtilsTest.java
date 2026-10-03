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

import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The description rule and the descriptor copy, as plain functions - no Spring, no doubles.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class MetaStorageDescriptionUtilsTest {

    private static MetaStorageRegistryParams fullDescriptor() {
        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = "old description";
        p.producer = "producer-1.0";
        p.recKeyFormat = "batch-NNNN";
        p.bodyFormat = "json";
        p.execContextId = 42L;
        p.function = "mh.asset.writer";
        p.consumer = "list, take, delete";
        return p;
    }

    // ---------- checkDescription ----------

    @Test
    public void test_checkDescription_acceptsText() {
        assertNull(MetaStorageDescriptionUtils.checkDescription("drone requirements, one record per reqId"));
    }

    @Test
    public void test_checkDescription_refusesNullEmptyAndBlank() {
        for (String d : new String[]{null, "", "   ", "\n\t "}) {
            final String error = MetaStorageDescriptionUtils.checkDescription(d);
            assertNotNull(error, "'" + d + "'");
            assertTrue(error.startsWith("01.954.020 "), error);
        }
    }

    @Test
    public void test_checkDescription_theLimitAppliesToTheStrippedText() {
        final String widest = "d".repeat(MetaStorageDescriptionUtils.MAX_DESCRIPTION_LENGTH);
        assertNull(MetaStorageDescriptionUtils.checkDescription(widest));
        // surrounding whitespace is not stored, so it does not count
        assertNull(MetaStorageDescriptionUtils.checkDescription("  " + widest + "\n"));

        final String error = MetaStorageDescriptionUtils.checkDescription(widest + "d");
        assertNotNull(error);
        assertTrue(error.startsWith("01.954.040 "), error);
        assertTrue(error.contains(String.valueOf(MetaStorageDescriptionUtils.MAX_DESCRIPTION_LENGTH + 1)), error);
    }

    // ---------- withDescription ----------

    @Test
    public void test_withDescription_withoutADescriptorCarriesOnlyTheDescription() {
        final MetaStorageRegistryParams p = MetaStorageDescriptionUtils.withDescription(null, "  new text \n");

        assertEquals("new text", p.desc, "stored stripped");
        assertNull(p.producer);
        assertNull(p.recKeyFormat);
        assertNull(p.bodyFormat);
        assertNull(p.execContextId);
        assertNull(p.function);
        assertNull(p.consumer);
    }

    @Test
    public void test_withDescription_keepsEveryOtherFieldOfTheDescriptor() {
        final MetaStorageRegistryParams p = MetaStorageDescriptionUtils.withDescription(fullDescriptor(), "new text");

        assertEquals("new text", p.desc);
        assertEquals("producer-1.0", p.producer);
        assertEquals("batch-NNNN", p.recKeyFormat);
        assertEquals("json", p.bodyFormat);
        assertEquals(42L, p.execContextId);
        assertEquals("mh.asset.writer", p.function);
        assertEquals("list, take, delete", p.consumer);
    }

    @Test
    public void test_withDescription_doesNotModifyTheDescriptorItWasGiven() {
        final MetaStorageRegistryParams existing = fullDescriptor();

        final MetaStorageRegistryParams p = MetaStorageDescriptionUtils.withDescription(existing, "new text");

        assertNotSame(existing, p);
        assertEquals("old description", existing.desc, "the given instance may be the one cached on the entity");
    }

    @Test
    public void test_withDescription_keepsLineBreaksInsideTheText() {
        final MetaStorageRegistryParams p = MetaStorageDescriptionUtils.withDescription(null, "first line\nsecond line");
        assertEquals("first line\nsecond line", p.desc);
    }
}
