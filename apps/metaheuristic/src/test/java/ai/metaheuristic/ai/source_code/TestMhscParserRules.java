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
import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeParser;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for individual MhSourceCode parser rules.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestMhscParserRules {

    private static class ParseResult<T> {
        final T tree;
        final List<String> errors;
        ParseResult(T tree, List<String> errors) { this.tree = tree; this.errors = errors; }
        void assertNoErrors() { assertTrue(errors.isEmpty(), "Parse errors: " + errors); }
        void assertHasErrors() { assertFalse(errors.isEmpty(), "Expected parse errors but got none"); }
    }

    private MhSourceCodeParser makeParser(String input, List<String> errors) {
        MhSourceCodeLexer lexer = new MhSourceCodeLexer(CharStreams.fromString(input));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col, String msg, RecognitionException e) {
                errors.add("LEXER " + line + ":" + col + " " + msg);
            }
        });
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MhSourceCodeParser p = new MhSourceCodeParser(tokens);
        p.removeErrorListeners();
        p.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col, String msg, RecognitionException e) {
                errors.add("PARSER " + line + ":" + col + " " + msg);
            }
        });
        return p;
    }

    private ParseResult<MhSourceCodeParser.IdRefContext> parseIdRef(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).idRef(), e);
    }
    private ParseResult<MhSourceCodeParser.VarDefContext> parseVarDef(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).varDef(), e);
    }
    private ParseResult<MhSourceCodeParser.VarDefListContext> parseVarDefList(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).varDefList(), e);
    }
    private ParseResult<MhSourceCodeParser.MetaDeclContext> parseMetaDecl(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).metaDecl(), e);
    }
    private ParseResult<MhSourceCodeParser.ConditionExprContext> parseConditionExpr(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).conditionExpr(), e);
    }
    private ParseResult<MhSourceCodeParser.FunctionRefContext> parseFunctionRef(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).functionRef(), e);
    }
    private ParseResult<MhSourceCodeParser.ProcessDeclContext> parseProcessDecl(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).processDecl(), e);
    }
    private ParseResult<MhSourceCodeParser.VariablesBlockContext> parseVariablesBlock(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).variablesBlock(), e);
    }
    private ParseResult<MhSourceCodeParser.CompilationUnitContext> parseCompilationUnit(String input) {
        List<String> e = new ArrayList<>(); return new ParseResult<>(makeParser(input, e).compilationUnit(), e);
    }

    // ===================== idRef =====================

    @Test public void test_idRef_simple() {
        var r = parseIdRef("synthetic");
        r.assertNoErrors();
        assertEquals(1, r.tree.idPart().size());
        assertEquals("synthetic", r.tree.getText());
    }

    @Test public void test_idRef_dotted() {
        var r = parseIdRef("mh.nop-1");
        r.assertNoErrors();
        assertEquals("mh.nop-1", r.tree.getText());
    }

    @Test public void test_idRef_function_code() {
        var r = parseIdRef("mhdg-rg.call-cc-1.0.4");
        r.assertNoErrors();
        assertEquals("mhdg-rg.call-cc-1.0.4", r.tree.getText());
    }

    @Test public void test_idRef_parameterized_simple() {
        var r = parseIdRef("requirementId{L}");
        r.assertNoErrors();
        assertEquals(2, r.tree.idPart().size());
        assertEquals("requirementId", r.tree.idPart(0).getText());
        assertEquals("{L}", r.tree.idPart(1).getText());
    }

    @Test public void test_idRef_parameterized_plus() {
        var r = parseIdRef("topLevelReqs{L+1}");
        r.assertNoErrors();
        assertEquals(2, r.tree.idPart().size());
        assertEquals("{L+1}", r.tree.idPart(1).getText());
    }

    @Test public void test_idRef_parameterized_minus() {
        var r = parseIdRef("requirementId{L-1}");
        r.assertNoErrors();
        assertEquals("{L-1}", r.tree.idPart(1).getText());
    }

    @Test public void test_idRef_multiple_parts() {
        var r = parseIdRef("prefix{L}suffix{L+1}");
        r.assertNoErrors();
        assertEquals(4, r.tree.idPart().size());
    }

    // ===================== varDef =====================

    @Test public void test_varDef_simple() {
        var r = parseVarDef("projectCode");
        r.assertNoErrors();
        assertEquals("projectCode", r.tree.idRef().getText());
        assertTrue(r.tree.varModifier() == null || r.tree.varModifier().isEmpty());
    }

    @Test public void test_varDef_with_ext() {
        var r = parseVarDef("synthetic: ext=\".txt\"");
        r.assertNoErrors();
        assertEquals("synthetic", r.tree.idRef().getText());
        assertEquals(1, r.tree.varModifier().size());
        assertEquals("\".txt\"", r.tree.varModifier(0).STRING().getText());
    }

    @Test public void test_varDef_with_type() {
        var r = parseVarDef("projectTask: type=task");
        r.assertNoErrors();
        assertEquals("projectTask", r.tree.idRef().getText());
        assertEquals(1, r.tree.varModifier().size());
        assertEquals("task", r.tree.varModifier(0).ID().getText());
    }

    @Test public void test_varDef_type_and_ext() {
        var r = parseVarDef("topLevelReqs: type=requirement, ext=\".jsonl\"");
        r.assertNoErrors();
        assertEquals("topLevelReqs", r.tree.idRef().getText());
        assertEquals(2, r.tree.varModifier().size());
    }

    @Test public void test_varDef_nullable_modifier() {
        var r = parseVarDef("requirementIdObj: type=requirement-id, nullable, ext=\".txt\"");
        r.assertNoErrors();
        assertEquals(3, r.tree.varModifier().size());
        assertEquals("nullable", r.tree.varModifier(1).getText());
    }

    @Test public void test_varDef_nullable_shorthand() {
        var r = parseVarDef("ancestorStack1?");
        r.assertNoErrors();
        assertEquals("ancestorStack1", r.tree.idRef().getText());
        assertTrue(r.tree.getText().endsWith("?"));
    }

    @Test public void test_varDef_modifiers_and_nullable_shorthand() {
        var r = parseVarDef("ancestorStack: type=ancestor-stack, ext=\".jsonl\"?");
        r.assertNoErrors();
        assertEquals(2, r.tree.varModifier().size());
        assertTrue(r.tree.getText().endsWith("?"));
    }

    @Test public void test_varDef_array() {
        var r = parseVarDef("items: array");
        r.assertNoErrors();
        assertEquals("array", r.tree.varModifier(0).getText());
    }

    @Test public void test_varDef_sourcing() {
        var r = parseVarDef("data: sourcing=dispatcher");
        r.assertNoErrors();
        assertEquals(1, r.tree.varModifier().size());
    }

    @Test public void test_varDef_parentContext() {
        var r = parseVarDef("result: parentContext");
        r.assertNoErrors();
        assertEquals("parentContext", r.tree.varModifier(0).getText());
    }

    // ===================== varDefList =====================

    @Test public void test_varDefList_single() {
        var r = parseVarDefList("projectCode");
        r.assertNoErrors();
        assertEquals(1, r.tree.varDef().size());
    }

    @Test public void test_varDefList_three_simple() {
        var r = parseVarDefList("projectCode, projectTask, topLevelPolicy");
        r.assertNoErrors();
        assertEquals(3, r.tree.varDef().size());
        assertEquals("projectCode", r.tree.varDef(0).idRef().getText());
        assertEquals("projectTask", r.tree.varDef(1).idRef().getText());
        assertEquals("topLevelPolicy", r.tree.varDef(2).idRef().getText());
    }

    @Test public void test_varDefList_with_modifiers() {
        var r = parseVarDefList(
                "projectTask: type=task, ext=\".txt\",\n" +
                "decompositionPolicy: type=decomposition-policy, ext=\".txt\",\n" +
                "topLevelPolicy: type=top-level-policy, ext=\".txt\"");
        r.assertNoErrors();
        assertEquals(3, r.tree.varDef().size());
    }

    @Test public void test_varDefList_mixed_nullable() {
        var r = parseVarDefList("projectCode, ancestorStack1?");
        r.assertNoErrors();
        assertEquals(2, r.tree.varDef().size());
    }

    // ===================== metaDecl =====================

    @Test public void test_metaDecl_single_string() {
        var r = parseMetaDecl("meta projectCode = \"projectCode\"");
        r.assertNoErrors();
        assertEquals(1, r.tree.metaEntry().size());
        assertEquals("projectCode", r.tree.metaEntry(0).idRef(0).getText());
        assertEquals("\"projectCode\"", r.tree.metaEntry(0).STRING().getText());
    }

    @Test public void test_metaDecl_multiple() {
        var r = parseMetaDecl("meta rg-mode = \"task\", variable-for-output = \"topLevelReqs\"");
        r.assertNoErrors();
        assertEquals(2, r.tree.metaEntry().size());
    }

    @Test public void test_metaDecl_block_form() {
        var r = parseMetaDecl("meta { rg-mode = \"task\", variable-for-output = \"topLevelReqs\" }");
        r.assertNoErrors();
        assertEquals(2, r.tree.metaEntry().size());
    }

    @Test public void test_metaDecl_many_entries() {
        var r = parseMetaDecl(
                "meta number-of-lines-per-task = \"1\",\n" +
                "     variable-for-splitting = \"topLevelReqs\",\n" +
                "     output-is-dynamic = \"true\",\n" +
                "     output-variable = \"reqJson0\",\n" +
                "     is-array = \"false\"");
        r.assertNoErrors();
        assertEquals(5, r.tree.metaEntry().size());
    }

    @Test public void test_metaDecl_idRef_value() {
        var r = parseMetaDecl("meta key = someValue");
        r.assertNoErrors();
        assertEquals(1, r.tree.metaEntry().size());
        // idRef(0) = key, idRef(1) = someValue
        assertEquals(2, r.tree.metaEntry(0).idRef().size());
    }

    // ===================== conditionExpr =====================

    @Test public void test_conditionExpr_bare_boolean() {
        var r = parseConditionExpr("hasObjectives");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondBareBooleanContext.class, r.tree);
    }

    @Test public void test_conditionExpr_true() {
        var r = parseConditionExpr("true");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondTrueContext.class, r.tree);
    }

    @Test public void test_conditionExpr_false() {
        var r = parseConditionExpr("false");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondFalseContext.class, r.tree);
    }

    @Test public void test_conditionExpr_ternary() {
        var r = parseConditionExpr("hasObjectives ? true : false");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondTernaryContext.class, r.tree);
    }

    @Test public void test_conditionExpr_equality_string() {
        var r = parseConditionExpr("amendmentStatus == \"ACTIVE\" ? true : false");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondTernaryContext.class, r.tree);
    }

    @Test public void test_conditionExpr_not() {
        var r = parseConditionExpr("!done");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondNotContext.class, r.tree);
    }

    @Test public void test_conditionExpr_and() {
        var r = parseConditionExpr("a && b");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondAndContext.class, r.tree);
    }

    @Test public void test_conditionExpr_or() {
        var r = parseConditionExpr("a || b");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondOrContext.class, r.tree);
    }

    @Test public void test_conditionExpr_comparison_gt() {
        var r = parseConditionExpr("currIndex > factorialOf - 1");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondCompareContext.class, r.tree);
    }

    @Test public void test_conditionExpr_grouped() {
        var r = parseConditionExpr("(a && b) || c");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondOrContext.class, r.tree);
    }

    @Test public void test_conditionExpr_nested_ternary() {
        var r = parseConditionExpr("x == 1 ? true : false");
        r.assertNoErrors();
        assertInstanceOf(MhSourceCodeParser.CondTernaryContext.class, r.tree);
    }

    // ===================== functionRef =====================

    @Test public void test_functionRef_external() {
        var r = parseFunctionRef("mhdg-rg.call-cc-1.0.4");
        r.assertNoErrors();
        assertEquals("mhdg-rg.call-cc-1.0.4", r.tree.idRef().getText());
    }

    @Test public void test_functionRef_internal() {
        var r = parseFunctionRef("internal mh.nop");
        r.assertNoErrors();
        assertEquals("mh.nop", r.tree.idRef().getText());
    }

    @Test public void test_functionRef_internal_complex() {
        var r = parseFunctionRef("internal mhdg-rg.read-project-task");
        r.assertNoErrors();
        assertEquals("mhdg-rg.read-project-task", r.tree.idRef().getText());
    }

    // ===================== variablesBlock =====================

    @Test public void test_variablesBlock_single_input() {
        var r = parseVariablesBlock("variables { <- projectCode }");
        r.assertNoErrors();
        assertEquals(1, r.tree.variablesElement().size());
    }

    @Test public void test_variablesBlock_inputs_and_outputs() {
        var r = parseVariablesBlock("variables { <- projectCode, inputData\n -> result: type=output, ext=\".json\" }");
        r.assertNoErrors();
        assertEquals(2, r.tree.variablesElement().size());
    }

    @Test public void test_variablesBlock_globals() {
        var r = parseVariablesBlock("variables { global globalVar1, globalVar2 }");
        r.assertNoErrors();
        assertEquals(1, r.tree.variablesElement().size());
    }

    @Test public void test_variablesBlock_inline() {
        var r = parseVariablesBlock("variables { inline myInline { key1 = \"val1\"\n key2 = \"val2\" } }");
        r.assertNoErrors();
        assertEquals(1, r.tree.variablesElement().size());
    }

    // ===================== processDecl =====================

    @Test public void test_processDecl_minimal() {
        var r = parseProcessDecl("mh.nop-1 := internal mh.nop { }");
        r.assertNoErrors();
        assertEquals("mh.nop-1", r.tree.idRef().getText());
    }

    @Test public void test_processDecl_with_timeout() {
        var r = parseProcessDecl("mh.nop-1 := internal mh.nop { timeout 60 }");
        r.assertNoErrors();
        assertNotNull(r.tree.processElement());
        assertEquals(1, r.tree.processElement().size());
    }

    @Test public void test_processDecl_with_outputs_and_meta() {
        var r = parseProcessDecl(
                "mhdg-rg.read-project-task := internal mhdg-rg.read-project-task {\n" +
                "    meta projectCode = \"projectCode\"\n" +
                "    -> projectTask: type=task, ext=\".txt\"\n" +
                "    timeout 60\n" +
                "}");
        r.assertNoErrors();
        assertEquals(3, r.tree.processElement().size());
    }

    @Test public void test_processDecl_with_inputs_outputs_cache() {
        var r = parseProcessDecl(
                "mhdg-rg.top-level-task := mhdg-rg.call-cc-1.0.4 {\n" +
                "    name \"Convert project-level task to top-level requirements\"\n" +
                "    meta rg-mode = \"task\"\n" +
                "    <- projectCode, projectTask, topLevelPolicy\n" +
                "    -> topLevelReqs: type=requirement, ext=\".jsonl\"\n" +
                "    timeout 120\n" +
                "    cache on\n" +
                "}");
        r.assertNoErrors();
        assertEquals(6, r.tree.processElement().size());
    }

    @Test public void test_processDecl_with_condition() {
        var r = parseProcessDecl(
                "mh.nop-objectives := internal mh.nop {\n" +
                "    when hasObjectives ? true : false\n" +
                "    sequential {\n" +
                "        inner-proc := internal mh.nop { timeout 10 }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_condition_equality() {
        var r = parseProcessDecl(
                "mh.nop-active := internal mh.nop {\n" +
                "    when amendmentStatus == \"ACTIVE\" ? true : false\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_parallel_subprocess() {
        var r = parseProcessDecl(
                "mh.nop-gate := internal mh.nop {\n" +
                "    parallel {\n" +
                "        branch-a := internal mh.nop { }\n" +
                "        branch-b := internal mh.nop { }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_sequential_subprocess() {
        var r = parseProcessDecl(
                "mh.batch := internal mh.batch-line-splitter {\n" +
                "    sequential {\n" +
                "        step1 := internal mh.nop { timeout 10 }\n" +
                "        step2 := internal mh.nop { timeout 20 }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_tag_and_priority() {
        var r = parseProcessDecl(
                "my-process := some-function {\n" +
                "    tag ai\n" +
                "    priority -1\n" +
                "    timeout 60\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_positive_priority() {
        var r = parseProcessDecl(
                "my-process := some-function {\n" +
                "    priority 5\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_tries() {
        var r = parseProcessDecl(
                "my-process := some-function {\n" +
                "    tries 3\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_processDecl_with_pre_post_functions() {
        var r = parseProcessDecl(
                "my-process := main-function {\n" +
                "    pre internal mh.pre-func\n" +
                "    post internal mh.post-func\n" +
                "}");
        r.assertNoErrors();
    }

    // ===================== compilationUnit (minimal sources) =====================

    @Test public void test_compilationUnit_empty_source() {
        var r = parseCompilationUnit("source \"test-1.0\" { }");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_strict() {
        var r = parseCompilationUnit("source \"test-1.0\" (strict) { }");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_clean() {
        var r = parseCompilationUnit("source \"test-1.0\" (clean) { }");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_strict_clean() {
        var r = parseCompilationUnit("source \"test-1.0\" (strict, clean) { }");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_instances() {
        var r = parseCompilationUnit("source \"test-1.0\" (instances = 3) { }");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_with_variables_and_process() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" (strict) {\n" +
                "    variables {\n" +
                "        <- projectCode\n" +
                "    }\n" +
                "    mh.nop-1 := internal mh.nop {\n" +
                "        timeout 60\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_two_processes() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" {\n" +
                "    proc1 := internal mh.nop { timeout 10 }\n" +
                "    proc2 := some-func-1.0 {\n" +
                "        <- projectCode\n" +
                "        -> result: type=output, ext=\".txt\"\n" +
                "        timeout 60\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_with_forLoop() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" {\n" +
                "    for L in 0 .. 3 {\n" +
                "        step{L} := internal mh.nop { timeout 10 }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_with_template() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" {\n" +
                "    template myTmpl(x) {\n" +
                "        step := internal mh.nop { timeout 10 }\n" +
                "    }\n" +
                "    @myTmpl(someArg)\n" +
                "}");
        // Template call at source level is not valid — only inside subProcessBlock
        // So this should fail. Let me check grammar...
        // sourceElement allows: variablesBlock | metasBlock | processDecl | templateDecl | forLoop
        // templateCall is NOT in sourceElement. It's in processOrControl.
        // This test should have errors.
        r.assertHasErrors();
    }

    @Test public void test_compilationUnit_template_call_in_subprocess() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" {\n" +
                "    template myTmpl(x) {\n" +
                "        step := internal mh.nop { timeout 10 }\n" +
                "    }\n" +
                "    wrapper := internal mh.nop {\n" +
                "        sequential {\n" +
                "            @myTmpl(someArg)\n" +
                "        }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_nested_subprocesses() {
        var r = parseCompilationUnit(
                "source \"test-1.0\" {\n" +
                "    outer := internal mh.nop {\n" +
                "        sequential {\n" +
                "            inner := internal mh.nop {\n" +
                "                parallel {\n" +
                "                    a := internal mh.nop { }\n" +
                "                    b := internal mh.nop { }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}");
        r.assertNoErrors();
    }

    @Test public void test_compilationUnit_with_comments() {
        var r = parseCompilationUnit(
                "// This is a source code\n" +
                "source \"test-1.0\" {\n" +
                "    /* block comment */\n" +
                "    proc1 := internal mh.nop { timeout 10 } // inline comment\n" +
                "}");
        r.assertNoErrors();
    }

    // ===================== negative tests =====================

    @Test public void test_processDecl_missing_assign() {
        var r = parseProcessDecl("mh.nop-1 internal mh.nop { }");
        r.assertHasErrors();
    }

    @Test public void test_processDecl_missing_function() {
        var r = parseProcessDecl("mh.nop-1 := { }");
        r.assertHasErrors();
    }

    @Test public void test_varDef_missing_value_after_type() {
        var r = parseVarDef("x: type=");
        r.assertHasErrors();
    }
}
