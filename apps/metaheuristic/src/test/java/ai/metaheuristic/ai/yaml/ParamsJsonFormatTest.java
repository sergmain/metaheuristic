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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParams;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParams;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsUtils;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParams;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The four params families owned by an ExecContext - ExecContextParams, ExecContextGraphParams,
 * ExecContextTaskStateParams, ExecutionGateParams - are stored as compact (not pretty-printed) JSON,
 * each starting its version chain at 1.
 *
 * <p>Two kinds of assertion, kept in separate tests on purpose: the FORMAT tests pin what is written
 * to the PARAMS column; the ROUND-TRIP tests pin that every field survives write -> read, including
 * the shapes a JSON mapper handles differently from yaml: final collections without setters, maps
 * keyed by Long / Integer, enums, nested nullable objects, and strings carrying quotes and CR/LF.
 *
 * @author Sergio Lissner
 * Date: 9/26/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class ParamsJsonFormatTest {

    private static final String DOT =
        "strict digraph G {\r\n  41599 [ ctxid=\"1\" ];\r\n  41600 [ ctxid=\"1,2\" ];\r\n  41599 -> 41600;\r\n}\r\n";

    // ------------------------------------------------------------------ helpers

    private static String head(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }

    private static JsonNode assertCompactJsonV1(String s, String family) {
        assertTrue(s.startsWith("{") && s.endsWith("}"), family + ": expected a JSON object, got: " + head(s));
        assertEquals(-1, s.indexOf('\n'), family + ": JSON must not be pretty-printed");
        assertEquals(-1, s.indexOf('\r'), family + ": JSON must not be pretty-printed");
        // the version is read by a token scan that stops at 'version'; anywhere but first means a full extra lexing pass
        assertTrue(s.startsWith("{\"version\":1,"), family + ": 'version' must be the first property, got: " + head(s));
        final JsonNode root = BaseJsonUtils.getMapper().readTree(s);
        final JsonNode version = root.get("version");
        assertNotNull(version, family + ": top-level 'version' is missing");
        assertEquals(1, version.asInt(), family + ": version");
        return root;
    }

    private static ExecContextParams.Variable fullVariable(String name) {
        final ExecContextParams.Variable v = new ExecContextParams.Variable(EnumsApi.DataSourcing.git, name);
        v.context = EnumsApi.VariableContext.local;
        v.git = new ExecContextParams.GitParams("https://example.com/v.git", "dev", "HEAD", "data");
        v.disk = new ExecContextParams.DiskParams("*.txt", "dir-code", "/tmp/dir");
        v.parentContext = true;
        v.type = "json";
        v.setNullable(true);
        v.ext = ".txt";
        return v;
    }

    private static ExecContextParams.Process fullProcess(String code) {
        final ExecContextParams.Process pr = new ExecContextParams.Process(code + "-name", code, "1",
            new ExecContextParams.FunctionDefinition("fn-" + code, "p=1", EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.type));
        pr.logic = EnumsApi.SourceCodeSubProcessLogic.and;
        pr.timeoutBeforeTerminate = 30L;
        pr.inputs.add(fullVariable(code + "-in"));
        pr.outputs.add(new ExecContextParams.Variable(code + "-out"));
        pr.metas.add(Map.of("meta-key", "meta-value"));
        pr.cache = new ExecContextParams.Cache(true, false, true);
        pr.tag = "tag-1";
        pr.priority = 5;
        pr.condition = "a > 1";
        pr.triesAfterError = 3;
        final ExecContextParams.Graft graft = new ExecContextParams.Graft("grp-A");
        graft.inputBindings.add("inA");
        graft.inputBindings.add("inB");
        graft.outputBindings.add("outC");
        graft.driver = "run-now";
        graft.at = "root";
        pr.graft = graft;
        return pr;
    }

    private static ExecContextParams fullExecContextParams() {
        final ExecContextParams p = new ExecContextParams();
        p.clean = true;
        p.sourceCodeUid = "sc-uid-1";
        p.desc = "desc with \"quotes\"";
        p.processesGraph = DOT;
        p.columnNames.put(0, "col-a");
        p.columnNames.put(2, "col-c");
        p.execContextGraph = new ExecContextParams.ExecContextGraph(11L, 12L, DOT);
        p.gitSources = new ExecContextParams.GitSources();
        p.gitSources.gitSourceInfos.add(new ExecContextParams.GitSourceInfo("fn-git",
            new ExecContextParams.GitParams("https://example.com/r.git", "main", "0123abcd", "assets")));

        p.variables.globals = List.of("g1", "g2");
        p.variables.inputs.add(fullVariable("in1"));
        p.variables.outputs.add(new ExecContextParams.Variable("out1"));
        p.variables.inline.put("mh.hyper-params", Map.of("k1", "v1"));

        p.processes.add(fullProcess("proc-1"));

        final ExecContextParams.Group g = new ExecContextParams.Group("grp-A");
        g.body.add(fullProcess("b#1"));
        g.inputs.add(new ExecContextParams.Variable("g-in"));
        g.outputs.add(new ExecContextParams.Variable("g-out"));
        g.internalContextId = "1,2";
        g.resetPointProcessCode = "b#1";
        p.groups.add(g);
        return p;
    }

    private static void assertProcessEquals(ExecContextParams.Process expected, ExecContextParams.Process actual) {
        final String pc = expected.processCode;
        assertEquals(expected.processName, actual.processName, pc);
        assertEquals(expected.processCode, actual.processCode, pc);
        assertEquals(expected.internalContextId, actual.internalContextId, pc);
        assertEquals(expected.function, actual.function, pc);
        assertEquals(expected.logic, actual.logic, pc);
        assertEquals(expected.timeoutBeforeTerminate, actual.timeoutBeforeTerminate, pc);
        assertEquals(expected.inputs, actual.inputs, pc);
        assertEquals(expected.outputs, actual.outputs, pc);
        assertEquals(expected.metas, actual.metas, pc);
        assertEquals(expected.cache, actual.cache, pc);
        assertEquals(expected.tag, actual.tag, pc);
        assertEquals(expected.priority, actual.priority, pc);
        assertEquals(expected.condition, actual.condition, pc);
        assertEquals(expected.triesAfterError, actual.triesAfterError, pc);
        assertEquals(expected.graft, actual.graft, pc);
    }

    // ------------------------------------------------------------------ ExecContextParams

    @Test
    public void test_execContextParams_isCompactJsonVersion1() {
        final String s = ExecContextParamsUtils.BASE_UTILS.toString(fullExecContextParams());
        assertCompactJsonV1(s, "ExecContextParams");
        assertFalse(s.contains("processMap"), "processMap is a runtime index, it must not be stored");
        // enums are stored by name(), never by toString(): DataSourcing carries a Lombok toString that is not its name,
        // and a stored value tied to toString() becomes unreadable the day that toString() changes
        assertTrue(s.contains("\"sourcing\":\"git\""), "DataSourcing must be stored by name()");
        assertFalse(s.contains("EnumsApi."), "no enum may be stored through its toString()");
    }

    @Test
    public void test_execContextParams_roundTripKeepsEveryField() {
        final ExecContextParams src = fullExecContextParams();
        final String s = ExecContextParamsUtils.BASE_UTILS.toString(src);
        final ExecContextParams back = ExecContextParamsUtils.BASE_UTILS.to(s);

        assertTrue(back.clean);
        assertEquals("sc-uid-1", back.sourceCodeUid);
        assertEquals(src.desc, back.desc);
        assertEquals(DOT, back.processesGraph);
        assertEquals(Map.of(0, "col-a", 2, "col-c"), back.columnNames);
        assertEquals(src.execContextGraph, back.execContextGraph);
        assertNotNull(back.gitSources);
        assertEquals(src.gitSources.gitSourceInfos, back.gitSources.gitSourceInfos);

        assertEquals(List.of("g1", "g2"), back.variables.globals);
        assertEquals(src.variables.inputs, back.variables.inputs);
        assertEquals(src.variables.outputs, back.variables.outputs);
        assertEquals(src.variables.inline, back.variables.inline);

        assertEquals(1, back.processes.size());
        assertProcessEquals(src.processes.get(0), back.processes.get(0));

        assertEquals(1, back.groups.size());
        final ExecContextParams.Group expectedGroup = src.groups.get(0);
        final ExecContextParams.Group g = back.groups.get(0);
        assertEquals("grp-A", g.name);
        assertEquals(1, g.body.size());
        assertProcessEquals(expectedGroup.body.get(0), g.body.get(0));
        assertEquals(expectedGroup.inputs, g.inputs);
        assertEquals(expectedGroup.outputs, g.outputs);
        assertEquals("1,2", g.internalContextId);
        assertEquals("b#1", g.resetPointProcessCode);

        // the runtime index is rebuilt from the deserialized lists, main processes and group bodies alike
        assertNotNull(back.findProcess("proc-1"));
        assertNotNull(back.findProcess("b#1"));

        // the document is stable across write -> read -> write
        assertEquals(s, ExecContextParamsUtils.BASE_UTILS.toString(back));
    }

    @Test
    public void test_execContextParams_emptyRoundTrips() {
        final ExecContextParams back = ExecContextParamsUtils.BASE_UTILS.to(
            ExecContextParamsUtils.BASE_UTILS.toString(new ExecContextParams()));

        assertFalse(back.clean);
        assertNull(back.sourceCodeUid);
        assertNull(back.desc);
        assertTrue(back.processes.isEmpty());
        assertTrue(back.groups.isEmpty());
        assertNull(back.variables.globals);
        assertTrue(back.variables.inputs.isEmpty());
        assertTrue(back.variables.outputs.isEmpty());
        assertTrue(back.variables.inline.isEmpty());
        assertEquals(ConstsApi.EMPTY_GRAPH, back.processesGraph);
        assertTrue(back.columnNames.isEmpty());
        assertNull(back.execContextGraph);
        assertNull(back.gitSources);
    }

    // ------------------------------------------------------------------ ExecContextGraphParams

    @Test
    public void test_graphParams_isCompactJsonVersion1() {
        final ExecContextGraphParams src = new ExecContextGraphParams();
        src.graph = DOT;
        assertCompactJsonV1(ExecContextGraphParamsUtils.BASE_UTILS.toString(src), "ExecContextGraphParams");
    }

    @Test
    public void test_graphParams_roundTripKeepsGraph() {
        final ExecContextGraphParams src = new ExecContextGraphParams();
        src.graph = DOT;
        final ExecContextGraphParams back = ExecContextGraphParamsUtils.BASE_UTILS.to(
            ExecContextGraphParamsUtils.BASE_UTILS.toString(src));
        assertEquals(DOT, back.graph);
    }

    // ------------------------------------------------------------------ ExecContextTaskStateParams

    private static ExecContextTaskStateParams fullTaskState() {
        final ExecContextTaskStateParams src = new ExecContextTaskStateParams();
        src.states.put(45064L, EnumsApi.TaskExecState.OK);
        src.states.put(45065L, EnumsApi.TaskExecState.NONE);
        src.states.put(45066L, EnumsApi.TaskExecState.ERROR);
        src.triesWasMade.put(45064L, 2);
        src.triesWasMade.put(45066L, 0);
        return src;
    }

    @Test
    public void test_taskStateParams_isCompactJsonVersion1() {
        assertCompactJsonV1(ExecContextTaskStateParamsUtils.BASE_UTILS.toString(fullTaskState()), "ExecContextTaskStateParams");
    }

    @Test
    public void test_taskStateParams_roundTripKeepsLongKeys() {
        final ExecContextTaskStateParams src = fullTaskState();
        final ExecContextTaskStateParams back = ExecContextTaskStateParamsUtils.BASE_UTILS.to(
            ExecContextTaskStateParamsUtils.BASE_UTILS.toString(src));
        // Map.equals compares keys with equals(): a key read back as String would not match the Long
        assertEquals(src.states, back.states);
        assertEquals(src.triesWasMade, back.triesWasMade);
        assertEquals(EnumsApi.TaskExecState.OK, back.states.get(45064L));
    }

    // ------------------------------------------------------------------ ExecutionGateParams

    private static ExecutionGateParams fullGate() {
        final ExecutionGateParams src = new ExecutionGateParams();
        src.triggeredByTaskId = 42L;
        src.functionCode = "fn-1";
        src.processorId = 7L;
        src.matchedPattern = "OutOfMemory.*";
        src.consoleExcerpt = "line1\nline2 \"quoted\"";
        src.incrementTries = true;
        return src;
    }

    @Test
    public void test_gateParams_isCompactJsonVersion1() {
        assertCompactJsonV1(ExecutionGateParamsUtils.BASE_UTILS.toString(fullGate()), "ExecutionGateParams");
    }

    @Test
    public void test_gateParams_roundTripKeepsEveryField() {
        final ExecutionGateParams src = fullGate();
        final ExecutionGateParams back = ExecutionGateParamsUtils.BASE_UTILS.to(
            ExecutionGateParamsUtils.BASE_UTILS.toString(src));
        assertEquals(src, back);
    }

    @Test
    public void test_gateParams_allNullRoundTrips() {
        final ExecutionGateParams back = ExecutionGateParamsUtils.BASE_UTILS.to(
            ExecutionGateParamsUtils.BASE_UTILS.toString(new ExecutionGateParams()));
        assertNull(back.triggeredByTaskId);
        assertNull(back.functionCode);
        assertNull(back.processorId);
        assertNull(back.matchedPattern);
        assertNull(back.consoleExcerpt);
        assertFalse(back.incrementTries);
    }
}
