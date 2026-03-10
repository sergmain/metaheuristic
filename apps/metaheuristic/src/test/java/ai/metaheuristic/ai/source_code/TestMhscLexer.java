/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MhSourceCode lexer.
 * Validates token types and boundaries for all lexer rules.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestMhscLexer {

    private List<? extends Token> tokenize(String input) {
        MhSourceCodeLexer lexer = new MhSourceCodeLexer(CharStreams.fromString(input));
        return lexer.getAllTokens();
    }

    private void assertTokenTypes(String input, int... expectedTypes) {
        List<? extends Token> tokens = tokenize(input);
        // Filter out EOF
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(expectedTypes.length, nonEof.size(),
                "Token count mismatch for input '" + input + "'. Tokens: " +
                nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], nonEof.get(i).getType(),
                    "Token type mismatch at index " + i + " for input '" + input +
                    "'. Token text: '" + nonEof.get(i).getText() + "'");
        }
    }

    // ===================== ID token =====================

    @Test
    public void test_id_simple_word() {
        List<? extends Token> tokens = tokenize("synthetic");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("synthetic", tokens.get(0).getText());
    }

    @Test
    public void test_id_with_dots() {
        List<? extends Token> tokens = tokenize("mhdg-rg.call-cc");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("mhdg-rg.call-cc", tokens.get(0).getText());
    }

    @Test
    public void test_id_with_hyphens() {
        List<? extends Token> tokens = tokenize("mh.batch-line-splitter");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("mh.batch-line-splitter", tokens.get(0).getText());
    }

    @Test
    public void test_id_with_underscore() {
        List<? extends Token> tokens = tokenize("_private");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_id_does_not_include_colon() {
        // Critical: colon must NOT be part of ID, it's a separate token
        // This ensures "synthetic: ext" tokenizes as ID COLON ID, not as one ID
        List<? extends Token> tokens = tokenize("synthetic:");
        assertEquals(2, tokens.size(), "Expected 'synthetic' and ':' as separate tokens. Got: " +
                tokens.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("synthetic", tokens.get(0).getText());
        // The colon should be a separate token (implicit token from parser rules)
    }

    @Test
    public void test_id_colon_id_three_tokens() {
        // "call-cc:1.0.12" — colon splits it. After colon we have "1.0.12"
        // With current grammar: "1" is INT, ".0" and ".12" are not valid tokens
        // This test documents the CURRENT (broken) behavior — function versions after colon don't tokenize
        List<? extends Token> tokens = tokenize("call-cc:1.0.12");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        // At minimum we should get call-cc, :, and something for the version
        assertTrue(nonEof.size() >= 2,
                "Expected at least 2 tokens for 'call-cc:1.0.12'. Got: " +
                nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals("call-cc", nonEof.get(0).getText());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
    }

    @Test
    public void test_function_version_after_colon_needs_fix() {
        // This test demonstrates the version-after-colon problem.
        // "1.0.12" starts with a digit, so it's not a valid ID.
        // INT only matches digits, not dots.
        // We need a solution: either a VERSION lexer token, or restructure how function codes are handled.
        List<? extends Token> tokens = tokenize("1.0.12");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        // Currently this will be: INT("1"), then lexer errors on ".0.12"
        // This test just documents the problem
        assertEquals(MhSourceCodeLexer.INT, nonEof.get(0).getType());
        assertEquals("1", nonEof.get(0).getText());
        // The rest generates lexer errors — we need to fix this
    }

    @Test
    public void test_function_code_no_colon_single_id() {
        // Function codes WITHOUT colon-version should be a single ID
        // e.g., "mhdg-rg.call-cc-1.0.12" (version embedded with hyphens, not colon)
        List<? extends Token> tokens = tokenize("mhdg-rg.call-cc-1.0.12");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("mhdg-rg.call-cc-1.0.12", tokens.get(0).getText());
    }

    @Test
    public void test_colon_versioned_function_code_needs_quoting() {
        // If a function code uses colon for version (call-cc:1.0.12),
        // it must be quoted as a string: 'call-cc:1.0.12'
        // The unquoted form generates lexer errors on the version part.
        List<? extends Token> tokens = tokenize("'call-cc:1.0.12'");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.STRING, tokens.get(0).getType());
    }

    // ===================== INT token =====================

    @Test
    public void test_int_simple() {
        List<? extends Token> tokens = tokenize("60");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.INT, tokens.get(0).getType());
        assertEquals("60", tokens.get(0).getText());
    }

    @Test
    public void test_int_zero() {
        List<? extends Token> tokens = tokenize("0");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.INT, tokens.get(0).getType());
    }

    // ===================== STRING token =====================

    @Test
    public void test_string_double_quoted() {
        List<? extends Token> tokens = tokenize("\"hello world\"");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.STRING, tokens.get(0).getType());
        assertEquals("\"hello world\"", tokens.get(0).getText());
    }

    @Test
    public void test_string_single_quoted() {
        List<? extends Token> tokens = tokenize("'hello world'");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.STRING, tokens.get(0).getType());
    }

    @Test
    public void test_string_with_escaped_quote() {
        List<? extends Token> tokens = tokenize("\"he\\\"llo\"");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.STRING, tokens.get(0).getType());
    }

    @Test
    public void test_string_with_dot_extension() {
        List<? extends Token> tokens = tokenize("\".txt\"");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.STRING, tokens.get(0).getType());
        assertEquals("\".txt\"", tokens.get(0).getText());
    }

    // ===================== Comments =====================

    @Test
    public void test_line_comment_skipped() {
        List<? extends Token> tokens = tokenize("foo // this is a comment\nbar");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(2, nonEof.size());
        assertEquals("foo", nonEof.get(0).getText());
        assertEquals("bar", nonEof.get(1).getText());
    }

    @Test
    public void test_block_comment_skipped() {
        List<? extends Token> tokens = tokenize("foo /* block comment */ bar");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(2, nonEof.size());
        assertEquals("foo", nonEof.get(0).getText());
        assertEquals("bar", nonEof.get(1).getText());
    }

    // ===================== Whitespace =====================

    @Test
    public void test_whitespace_skipped() {
        List<? extends Token> tokens = tokenize("  foo  \t  bar  \n  baz  ");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(3, nonEof.size());
    }

    // ===================== Operators / delimiters =====================

    @Test
    public void test_arrow_left() {
        List<? extends Token> tokens = tokenize("<-");
        assertEquals(1, tokens.size());
        // <- is an implicit token from parser rules
    }

    @Test
    public void test_arrow_right() {
        List<? extends Token> tokens = tokenize("->");
        assertEquals(1, tokens.size());
    }

    @Test
    public void test_assign() {
        List<? extends Token> tokens = tokenize(":=");
        assertEquals(1, tokens.size());
    }

    @Test
    public void test_braces() {
        List<? extends Token> tokens = tokenize("{ }");
        assertEquals(2, tokens.size());
    }

    @Test
    public void test_question_mark() {
        List<? extends Token> tokens = tokenize("?");
        assertEquals(1, tokens.size());
    }

    // ===================== Keyword tokens (implicit) =====================

    @Test
    public void test_keyword_source() {
        // 'source' is an implicit token, should NOT be lexed as ID
        List<? extends Token> tokens = tokenize("source");
        assertEquals(1, tokens.size());
        // Should NOT be ID since it's a keyword
        // Note: ANTLR4 implicit tokens have higher priority than named rules
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType(),
                "'source' should be lexed as implicit keyword token, not ID");
    }

    @Test
    public void test_keyword_variables() {
        List<? extends Token> tokens = tokenize("variables");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType(),
                "'variables' should be lexed as implicit keyword token, not ID");
    }

    @Test
    public void test_keyword_meta() {
        List<? extends Token> tokens = tokenize("meta");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_timeout() {
        List<? extends Token> tokens = tokenize("timeout");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_ext() {
        List<? extends Token> tokens = tokenize("ext");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType(),
                "'ext' should be lexed as implicit keyword token, not ID");
    }

    @Test
    public void test_keyword_type() {
        List<? extends Token> tokens = tokenize("type");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType(),
                "'type' should be lexed as implicit keyword token, not ID");
    }

    @Test
    public void test_keyword_internal() {
        List<? extends Token> tokens = tokenize("internal");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_sequential() {
        List<? extends Token> tokens = tokenize("sequential");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_parallel() {
        List<? extends Token> tokens = tokenize("parallel");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_when() {
        List<? extends Token> tokens = tokenize("when");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    @Test
    public void test_keyword_cache() {
        List<? extends Token> tokens = tokenize("cache");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
    }

    // ===================== Combined sequences =====================

    @Test
    public void test_vardef_sequence_synthetic_colon_ext() {
        // "synthetic: ext=".txt"" — this is the exact sequence that failed
        List<? extends Token> tokens = tokenize("synthetic: ext = \".txt\"");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        // Should be: ID(':')(ext-keyword)('=')(STRING)
        assertTrue(nonEof.size() >= 4,
                "Expected at least 4 tokens. Got: " +
                nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType(), "First token should be ID 'synthetic'");
        assertEquals("synthetic", nonEof.get(0).getText());
        // Second token is ':'
        assertEquals(":", nonEof.get(1).getText());
        // Third token is 'ext' (keyword, not ID)
        assertEquals("ext", nonEof.get(2).getText());
        assertNotEquals(MhSourceCodeLexer.ID, nonEof.get(2).getType(), "'ext' should be keyword, not ID");
    }

    @Test
    public void test_vardef_sequence_type_equals_id() {
        // "type = task" — type is keyword, task is ID
        List<? extends Token> tokens = tokenize("type = task");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(3, nonEof.size());
        assertNotEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType(), "'type' should be keyword");
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(2).getType(), "'task' should be ID");
    }

    @Test
    public void test_process_decl_sequence() {
        // "mh.nop-1 := internal mh.nop {"
        List<? extends Token> tokens = tokenize("mh.nop-1 := internal mh.nop {");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 5,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("mh.nop-1", nonEof.get(0).getText());
        assertEquals(":=", nonEof.get(1).getText());
    }

    @Test
    public void test_condition_ternary_sequence() {
        // "hasObjectives ? true : false"
        List<? extends Token> tokens = tokenize("hasObjectives ? true : false");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 5,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("hasObjectives", nonEof.get(0).getText());
    }

    @Test
    public void test_condition_equality_with_string() {
        // amendmentStatus == "ACTIVE" ? true : false
        List<? extends Token> tokens = tokenize("amendmentStatus == \"ACTIVE\" ? true : false");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 7,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("amendmentStatus", nonEof.get(0).getText());
        assertEquals("==", nonEof.get(1).getText());
        assertEquals(MhSourceCodeLexer.STRING, nonEof.get(2).getType());
    }

    @Test
    public void test_parameterized_id_sequence() {
        // "requirementId{L+1}" should be: ID('{')ID('+')INT('}')
        List<? extends Token> tokens = tokenize("requirementId{L+1}");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 5,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("requirementId", nonEof.get(0).getText());
    }

    @Test
    public void test_range_operator() {
        // "0 .. 5" for for-loops
        // '..' is a single implicit token
        List<? extends Token> tokens = tokenize("0 .. 5");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(3, nonEof.size(),
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.INT, nonEof.get(0).getType());
        assertEquals("0", nonEof.get(0).getText());
        assertEquals("..", nonEof.get(1).getText());
        assertEquals(MhSourceCodeLexer.INT, nonEof.get(2).getType());
        assertEquals("5", nonEof.get(2).getText());
    }
}
