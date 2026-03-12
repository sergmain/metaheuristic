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
        List<? extends Token> tokens = tokenize("synthetic:");
        assertEquals(2, tokens.size(), "Expected 'synthetic' and ':' as separate tokens. Got: " +
                tokens.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("synthetic", tokens.get(0).getText());
    }

    @Test
    public void test_id_colon_id_three_tokens() {
        List<? extends Token> tokens = tokenize("call-cc:1.0.12");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 2,
                "Expected at least 2 tokens for 'call-cc:1.0.12'. Got: " +
                nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals("call-cc", nonEof.get(0).getText());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
    }

    @Test
    public void test_function_version_after_colon_needs_fix() {
        List<? extends Token> tokens = tokenize("1.0.12");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(MhSourceCodeLexer.INT, nonEof.get(0).getType());
        assertEquals("1", nonEof.get(0).getText());
    }

    @Test
    public void test_function_code_no_colon_single_id() {
        List<? extends Token> tokens = tokenize("mhdg-rg.call-cc-1.0.12");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.ID, tokens.get(0).getType());
        assertEquals("mhdg-rg.call-cc-1.0.12", tokens.get(0).getText());
    }

    @Test
    public void test_colon_versioned_function_code_needs_quoting() {
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
        List<? extends Token> tokens = tokenize("source");
        assertEquals(1, tokens.size());
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
        List<? extends Token> tokens = tokenize("synthetic: ext = \".txt\"");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 4,
                "Expected at least 4 tokens. Got: " +
                nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType(), "First token should be ID 'synthetic'");
        assertEquals("synthetic", nonEof.get(0).getText());
        assertEquals(":", nonEof.get(1).getText());
        assertEquals("ext", nonEof.get(2).getText());
        assertNotEquals(MhSourceCodeLexer.ID, nonEof.get(2).getType(), "'ext' should be keyword, not ID");
    }

    @Test
    public void test_vardef_sequence_type_equals_id() {
        List<? extends Token> tokens = tokenize("type = task");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(3, nonEof.size());
        assertNotEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType(), "'type' should be keyword");
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(2).getType(), "'task' should be ID");
    }

    @Test
    public void test_process_decl_sequence() {
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
        List<? extends Token> tokens = tokenize("hasObjectives ? true : false");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 5,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("hasObjectives", nonEof.get(0).getText());
    }

    @Test
    public void test_condition_equality_with_string() {
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
        List<? extends Token> tokens = tokenize("requirementId{L+1}");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 5,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("requirementId", nonEof.get(0).getText());
    }

    // ===================== DEF_REF token =====================

    @Test
    public void test_def_ref_simple() {
        List<? extends Token> tokens = tokenize("${my_version}");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.DEF_REF, tokens.get(0).getType());
        assertEquals("${my_version}", tokens.get(0).getText());
    }

    @Test
    public void test_def_ref_with_digits() {
        List<? extends Token> tokens = tokenize("${ver2}");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.DEF_REF, tokens.get(0).getType());
        assertEquals("${ver2}", tokens.get(0).getText());
    }

    @Test
    public void test_def_ref_underscore_start() {
        List<? extends Token> tokens = tokenize("${_private}");
        assertEquals(1, tokens.size());
        assertEquals(MhSourceCodeLexer.DEF_REF, tokens.get(0).getType());
    }

    @Test
    public void test_def_ref_embedded_in_function_code() {
        // "mhdg-rg.call-cc-${ver}" should tokenize as: ID DEF_REF
        List<? extends Token> tokens = tokenize("mhdg-rg.call-cc-${ver}");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertEquals(2, nonEof.size(),
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertEquals(MhSourceCodeLexer.ID, nonEof.get(0).getType());
        assertEquals("mhdg-rg.call-cc-", nonEof.get(0).getText());
        assertEquals(MhSourceCodeLexer.DEF_REF, nonEof.get(1).getType());
        assertEquals("${ver}", nonEof.get(1).getText());
    }

    @Test
    public void test_def_ref_not_confused_with_template_param() {
        // "{L}" should NOT be DEF_REF
        List<? extends Token> tokens = tokenize("{L}");
        List<? extends Token> nonEof = tokens.stream().filter(t -> t.getType() != Token.EOF).toList();
        assertTrue(nonEof.size() >= 2,
                "Tokens: " + nonEof.stream().map(t -> "'" + t.getText() + "'(" + t.getType() + ")").toList());
        assertNotEquals(MhSourceCodeLexer.DEF_REF, nonEof.get(0).getType(),
                "{L} should not be lexed as DEF_REF");
    }

    @Test
    public void test_def_keyword_lexed() {
        List<? extends Token> tokens = tokenize("def");
        assertEquals(1, tokens.size());
        assertNotEquals(MhSourceCodeLexer.ID, tokens.get(0).getType(),
                "'def' should be lexed as keyword, not ID");
    }

    @Test
    public void test_range_operator() {
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
