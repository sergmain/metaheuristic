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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The meta table naming rule: a latin letter, then latin letters, digits, hyphen, underscore, dot.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class MetaStorageNameUtilsTest {

    // ---------- names that must be accepted ----------

    @Test
    public void test_acceptsTheNamesTheSystemAlreadyMints() {
        // the shape mh.asset.dir-batcher produces, and the one MH-GIT-DELIVERY documents
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("mh.asset.dir-batch-for-requirements.1"));
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("mh.asset.dir-batch-for-requirements.12"));
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("mh.meta-table-registry"));
    }

    @Test
    public void test_acceptsEachAllowedCharacterClass() {
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("a"), "one letter is a whole name");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("Table"), "upper case");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("a1"), "digit after the first char");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("a-b"), "hyphen");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("a_b"), "underscore");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("a.b"), "dot");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName("Z9-_.z"), "all of them together");
    }

    // ---------- the first character ----------

    @Test
    public void test_rejectsALeadingDigit() {
        // a name that parses as a number is a name that will be compared, sorted or logged as one
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("1table"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("12"));
    }

    @Test
    public void test_rejectsALeadingDotHyphenOrUnderscore() {
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(".hidden"), "reads as a relative path");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("-x"), "reads as a command-line flag");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("_x"));
    }

    // ---------- emptiness ----------

    @Test
    public void test_rejectsNullAndEmptyAndBlank() {
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(null));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(""));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(" "));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("\t"));
    }

    // ---------- whitespace, including the invisible cases this exists for ----------

    @Test
    public void test_rejectsWhitespaceAnywhere() {
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a b"), "inner space");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("ab "), "trailing space - the typo that looks fine");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(" ab"), "leading space");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("ab\n"), "trailing newline from a shell here-doc");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a\tb"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a\rb"));
    }

    @Test
    public void test_rejectsAnEmbeddedNewlineEvenWhenBothLinesWouldBeValid() {
        // ❗ Java's ^ and $ are line anchors under MULTILINE; this pins that MULTILINE is NOT set,
        // so a two-line string cannot slip through by having a valid first line
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("good\nalso-good"));
    }

    // ---------- characters excluded for a downstream reason ----------

    @Test
    public void test_rejectsUrlUnsafeCharacters() {
        // the browse page puts the name in a path segment
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a/b"), "slash splits the path segment");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a%20b"), "percent is an escape introducer");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a?b"), "query separator");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a#b"), "fragment separator");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a&b"));
    }

    @Test
    public void test_rejectsQuotingAndEscapingCharacters() {
        // a name travels through JSON and YAML unescaped
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a\"b"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a'b"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a\\b"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("a:b"), "YAML key separator");
    }

    @Test
    public void test_rejectsNonLatinLetters() {
        // the rule is latin-only; a cyrillic 'а' is a different code point that renders identically
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("таблица"));
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("mh.аsset"), "cyrillic 'а' inside a latin name");
        assertFalse(MetaStorageNameUtils.isValidMetaTableName("表"));
    }

    // ---------- the throwing form ----------

    @Test
    public void test_validatePassesSilentlyForAValidName() {
        assertDoesNotThrow(() -> MetaStorageNameUtils.validateMetaTableName("mh.asset.dir-batch-for-requirements.1"));
    }

    @Test
    public void test_validateThrowsIllegalArgumentExceptionCarryingTheErrorCode() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageNameUtils.validateMetaTableName("1bad"));
        assertTrue(e.getMessage().startsWith("01.946.020"), "the code leads the message: " + e.getMessage());
    }

    @Test
    public void test_validateQuotesTheOffendingNameInFull() {
        // the usual cause is an invisible character, so the message must show the raw value
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageNameUtils.validateMetaTableName("good-name "));
        assertTrue(e.getMessage().contains("'good-name '"),
                "the trailing space must be visible between the quotes: " + e.getMessage());
    }

    @Test
    public void test_validateReportsNullRatherThanThrowingNullPointerException() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageNameUtils.validateMetaTableName(null));
        assertTrue(e.getMessage().startsWith("01.946.020"), e.getMessage());
    }

    // ---------- the length bound ----------

    private static String nameOfLength(int length) {
        return "a".repeat(length);
    }

    @Test
    public void test_acceptsANameExactlyAtTheColumnWidth() {
        assertEquals(50, MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH,
                "the bound is the VARCHAR width of TYPE and META_TABLE - the two are one fact");
        assertTrue(MetaStorageNameUtils.isValidMetaTableName(
                nameOfLength(MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH)));
    }

    @Test
    public void test_rejectsANameOneCharacterOverTheColumnWidth() {
        // one over is the case that matters: it is the one that would reach the database
        assertFalse(MetaStorageNameUtils.isValidMetaTableName(
                nameOfLength(MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH + 1)));
    }

    @Test
    public void test_acceptsTheDocumentedExampleWhichSitsWellUnderTheBound() {
        // mh.asset.dir-batch-for-requirements.12 from MH-GIT-DELIVERY 5.10 - the headroom is real
        // but not large, which is why the bound has to be stated rather than discovered
        final String documented = "mh.asset.dir-batch-for-requirements.12";
        assertTrue(documented.length() < MetaStorageNameUtils.META_TABLE_NAME_MAX_LENGTH);
        assertTrue(MetaStorageNameUtils.isValidMetaTableName(documented));
    }

    @Test
    public void test_aTooLongNameIsReportedAsTooLongRatherThanAsMalformed() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageNameUtils.validateMetaTableName(nameOfLength(60)));

        assertTrue(e.getMessage().startsWith("01.946.040"),
                "a length failure gets its own code, not the charset one: " + e.getMessage());
        assertTrue(e.getMessage().contains("60"), "the actual length is named: " + e.getMessage());
        assertTrue(e.getMessage().contains("50"), "and so is the limit: " + e.getMessage());
    }

    @Test
    public void test_aNameBreakingBothRulesIsReportedOnTheCharsetRule() {
        // the character defect is the more fundamental one - fixing the length would not make a name
        // with a space in it valid, so reporting length first would send the caller the wrong way
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MetaStorageNameUtils.validateMetaTableName(nameOfLength(60) + " "));
        assertTrue(e.getMessage().startsWith("01.946.020"), e.getMessage());
    }
}
