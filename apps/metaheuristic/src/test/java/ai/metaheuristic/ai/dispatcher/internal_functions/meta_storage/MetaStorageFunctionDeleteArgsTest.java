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

package ai.metaheuristic.ai.dispatcher.internal_functions.meta_storage;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two arguments action {@code delete} of {@code mh.meta-storage} resolves before it removes anything:
 * which table ({@code synthetic}) and which records ({@code keys}).
 *
 * <p>Both are stricter for delete than for select and upsert, and each test pins one side of that: an
 * answer that is accepted must be the right answer, and everything that is not an explicit answer must be
 * refused - with the error code that names which argument was missing - rather than fall back to a
 * default. The fallback ruled out here is the one select and upsert keep: an absent or unrecognised
 * synthetic flag read as the PRODUCTION table, and an absent key list read as every record of the type.
 *
 * <p>No doubles: the variable lookup is the function parameter production fills with the real variable
 * service, and here a Map fills it. Every assertion is on what the function returned or refused.
 */
@Execution(ExecutionMode.CONCURRENT)
public class MetaStorageFunctionDeleteArgsTest {

    /** Variable name -> value, standing in for the ExecContext's variables. A name not in the map reads as null. */
    private static final Map<String, String> VARIABLES = Map.of(
            "flagTrue", "true",
            "flagFalse", "false",
            "flagTrueUpperPadded", "  TRUE\n",
            "flagFalseMixedCase", "False",
            "flagBlank", "   ",
            "flagYes", "yes",
            "flagTypo", "ture",
            "flagOne", "1");

    @Test
    public void test_syntheticForDelete_trueAddressesTheSyntheticTable() {
        assertTrue(MetaStorageFunction.syntheticForDelete("flagTrue", VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_falseAddressesTheProductionTable() {
        assertFalse(MetaStorageFunction.syntheticForDelete("flagFalse", VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_caseAndSurroundingWhitespaceAreIgnored() {
        assertTrue(MetaStorageFunction.syntheticForDelete("flagTrueUpperPadded", VARIABLES::get),
                "'  TRUE\\n' is true");
        assertFalse(MetaStorageFunction.syntheticForDelete("flagFalseMixedCase", VARIABLES::get),
                "'False' is false");
    }

    @Test
    public void test_syntheticForDelete_absentMetaIsRefused() {
        assertRefused(Enums.InternalFunctionProcessing.meta_not_found, "01.942.240 ",
                () -> MetaStorageFunction.syntheticForDelete(null, VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_blankMetaIsRefused() {
        assertRefused(Enums.InternalFunctionProcessing.meta_not_found, "01.942.240 ",
                () -> MetaStorageFunction.syntheticForDelete("  ", VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_variableWithNoValueIsRefused() {
        // select and upsert read this as the PRODUCTION table - a delete must not
        assertRefused(Enums.InternalFunctionProcessing.general_business_error, "01.942.250 ",
                () -> MetaStorageFunction.syntheticForDelete("noSuchVariable", VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_blankValueIsRefused() {
        assertRefused(Enums.InternalFunctionProcessing.general_business_error, "01.942.250 ",
                () -> MetaStorageFunction.syntheticForDelete("flagBlank", VARIABLES::get));
    }

    @Test
    public void test_syntheticForDelete_valueThatIsNotTrueOrFalseIsRefused() {
        // a typo must never select a table, least of all the production one
        for (String name : List.of("flagYes", "flagTypo", "flagOne")) {
            assertRefused(Enums.InternalFunctionProcessing.general_business_error, "01.942.250 ",
                    () -> MetaStorageFunction.syntheticForDelete(name, VARIABLES::get));
        }
    }

    @Test
    public void test_keysForDelete_absentMetaIsRefused() {
        // select reads this as 'every record of the type' - a delete must not
        assertRefused(Enums.InternalFunctionProcessing.meta_not_found, "01.942.260 ",
                () -> MetaStorageFunction.keysForDelete(null, null));
    }

    @Test
    public void test_keysForDelete_metaWithoutAKeyListIsRefused() {
        assertRefused(Enums.InternalFunctionProcessing.meta_not_found, "01.942.260 ",
                () -> MetaStorageFunction.keysForDelete("keysVar", null));
    }

    @Test
    public void test_keysForDelete_emptyKeyListIsRefused() {
        assertRefused(Enums.InternalFunctionProcessing.data_not_found, "01.942.270 ",
                () -> MetaStorageFunction.keysForDelete("keysVar", List.of()));
    }

    @Test
    public void test_keysForDelete_returnsTheKeysInTheirOrder() {
        final List<String> keys = List.of("batch-0002", "batch-0001");
        assertEquals(keys, MetaStorageFunction.keysForDelete("keysVar", keys));
    }

    private static void assertRefused(
            Enums.InternalFunctionProcessing expectedProcessing, String expectedCodePrefix, Executable call) {
        final InternalFunctionException e = assertThrows(InternalFunctionException.class, call);
        assertEquals(expectedProcessing, e.result.processing, "processing, error: " + e.result.error);
        assertNotNull(e.result.error, "the refusal must carry an error message");
        assertTrue(e.result.error.startsWith(expectedCodePrefix),
                "expected the error to start with '" + expectedCodePrefix + "', was: " + e.result.error);
    }
}
