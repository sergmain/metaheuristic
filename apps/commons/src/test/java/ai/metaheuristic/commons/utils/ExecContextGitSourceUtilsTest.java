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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 3:40 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextGitSourceUtilsTest {

    private static final String REPO = "https://github.com/sergmain/metaheuristic-assets.git";
    private static final String SHA_1 = "1111111111111111111111111111111111111111";
    private static final String SHA_2 = "2222222222222222222222222222222222222222";

    // ---------- helpers: real config objects, not scripted doubles ----------

    private static FunctionConfigYaml gitFunction(String code, @org.jspecify.annotations.Nullable String commit) {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = code;
        cfg.function.sourcing = EnumsApi.FunctionSourcing.git;
        cfg.function.git = new GitInfo(REPO, "main", commit, "fn/" + code);
        return cfg;
    }

    private static FunctionConfigYaml dispatcherFunction(String code) {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = code;
        cfg.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        return cfg;
    }

    private static ExecContextParamsYaml.Process process(String processCode, String functionCode, EnumsApi.FunctionExecContext ctx) {
        final ExecContextParamsYaml.FunctionDefinition fd = new ExecContextParamsYaml.FunctionDefinition(functionCode, ctx);
        return new ExecContextParamsYaml.Process(processCode, processCode, "#1", fd);
    }

    private static Function<String, FunctionConfigYaml> registry(FunctionConfigYaml... configs) {
        final Map<String, FunctionConfigYaml> rows = new HashMap<>();
        for (FunctionConfigYaml cfg : configs) {
            rows.put(cfg.function.code, cfg);
        }
        return rows::get;
    }

    // ---------- collectExternalFunctionCodes ----------

    @Test
    public void test_collectSkipsInternalFunctions() {
        final List<ExecContextParamsYaml.Process> processes = List.of(
            process("p1", "mh.permute-variables", EnumsApi.FunctionExecContext.internal),
            process("p2", "fn-py", EnumsApi.FunctionExecContext.external));

        assertEquals(List.of("fn-py"), ExecContextGitSourceUtils.collectExternalFunctionCodes(processes, List.of()));
    }

    @Test
    public void test_collectDeduplicatesRepeatedCodes() {
        final List<ExecContextParamsYaml.Process> processes = List.of(
            process("p1", "fn-py", EnumsApi.FunctionExecContext.external),
            process("p2", "fn-py", EnumsApi.FunctionExecContext.external),
            process("p3", "fn-sh", EnumsApi.FunctionExecContext.external));

        assertEquals(List.of("fn-py", "fn-sh"), ExecContextGitSourceUtils.collectExternalFunctionCodes(processes, List.of()));
    }

    @Test
    public void test_collectIncludesPreAndPostFunctions() {
        final ExecContextParamsYaml.Process p = process("p1", "fn-main", EnumsApi.FunctionExecContext.external);
        p.preFunctions = List.of(new ExecContextParamsYaml.FunctionDefinition("fn-pre", EnumsApi.FunctionExecContext.external));
        p.postFunctions = List.of(new ExecContextParamsYaml.FunctionDefinition("fn-post", EnumsApi.FunctionExecContext.external));

        assertEquals(List.of("fn-main", "fn-pre", "fn-post"),
            ExecContextGitSourceUtils.collectExternalFunctionCodes(List.of(p), List.of()));
    }

    @Test
    public void test_collectReachesIntoGroupBodies() {
        final ExecContextParamsYaml.Group g = new ExecContextParamsYaml.Group("rung-2");
        g.body.add(process("gp1", "fn-in-group", EnumsApi.FunctionExecContext.external));

        final List<String> codes = ExecContextGitSourceUtils.collectExternalFunctionCodes(
            List.of(process("p1", "fn-main", EnumsApi.FunctionExecContext.external)), List.of(g));

        assertEquals(List.of("fn-main", "fn-in-group"), codes,
            "a grafted group body executes as a real task, so its Function needs pinning too");
    }

    @Test
    public void test_collectOnEmptyDagIsEmpty() {
        assertTrue(ExecContextGitSourceUtils.collectExternalFunctionCodes(List.of(), List.of()).isEmpty());
    }

    // ---------- resolveGitSources ----------

    @Test
    public void test_resolveReturnsNullWhenNoGitFunction() {
        assertNull(ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-jar"), registry(dispatcherFunction("fn-jar")), git -> SHA_1),
            "no git-sourced Function means nothing to pin, and null keeps it out of the stored yaml");
    }

    @Test
    public void test_resolveReturnsNullWhenNoFunctionCodes() {
        assertNull(ExecContextGitSourceUtils.resolveGitSources(List.of(), registry(), git -> SHA_1));
    }

    @Test
    public void test_resolveHeadIsReplacedByConcreteSha() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(gitFunction("fn-py", "HEAD")), git -> SHA_1);

        assertNotNull(actual);
        assertEquals(1, actual.gitSourceInfos.size());
        assertEquals(SHA_1, actual.gitSourceInfos.get(0).git.commit);
        assertNotEquals("HEAD", actual.gitSourceInfos.get(0).git.commit);
    }

    @Test
    public void test_resolveBlankCommitIsAlsoHeadBased() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(gitFunction("fn-py", null)), git -> SHA_1);

        assertNotNull(actual);
        assertEquals(SHA_1, actual.gitSourceInfos.get(0).git.commit);
    }

    @Test
    public void test_resolveLeavesAnExplicitCommitAlone() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(gitFunction("fn-py", SHA_2)), git -> SHA_1);

        assertNotNull(actual);
        assertEquals(SHA_2, actual.gitSourceInfos.get(0).git.commit,
            "a descriptor that already pinned a revision must not be re-resolved to the branch tip");
    }

    @Test
    public void test_resolveCarriesRepoBranchAndPathThrough() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(gitFunction("fn-py", "HEAD")), git -> SHA_1);

        assertNotNull(actual);
        final ExecContextParamsYaml.GitSourceInfo info = actual.gitSourceInfos.get(0);
        assertEquals("fn-py", info.functionCode);
        assertEquals(REPO, info.git.repo);
        assertEquals("main", info.git.branch);
        assertEquals("fn/fn-py", info.git.path);
    }

    @Test
    public void test_resolveKeepsOnlyGitSourcedFunctions() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-jar", "fn-py"),
            registry(dispatcherFunction("fn-jar"), gitFunction("fn-py", "HEAD")),
            git -> SHA_1);

        assertNotNull(actual);
        assertEquals(1, actual.gitSourceInfos.size());
        assertEquals("fn-py", actual.gitSourceInfos.get(0).functionCode);
    }

    @Test
    public void test_resolveSkipsUnregisteredFunctionCode() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-missing", "fn-py"), registry(gitFunction("fn-py", "HEAD")), git -> SHA_1);

        assertNotNull(actual);
        assertEquals(1, actual.gitSourceInfos.size(),
            "an unregistered code is SourceCode validation's verdict to make, not this walk's");
    }

    @Test
    public void test_resolveFailsLoudlyWhenHeadCantBeResolved() {
        final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            ExecContextGitSourceUtils.resolveGitSources(
                List.of("fn-py"), registry(gitFunction("fn-py", "HEAD")), git -> null));

        assertTrue(e.getMessage().startsWith("01.921.020"), e.getMessage());
        assertTrue(e.getMessage().contains("fn-py"), e.getMessage());
    }

    @Test
    public void test_resolvePinsTwoFunctionsIndependently() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-a", "fn-b"),
            registry(gitFunction("fn-a", "HEAD"), gitFunction("fn-b", SHA_2)),
            git -> SHA_1);

        assertNotNull(actual);
        assertEquals(2, actual.gitSourceInfos.size());
        assertEquals(SHA_1, actual.find("fn-a").git.commit);
        assertEquals(SHA_2, actual.find("fn-b").git.commit);
    }

    @Test
    public void test_pinnedRevisionIsACopyNotTheDescriptorsOwnObject() {
        final FunctionConfigYaml cfg = gitFunction("fn-py", "HEAD");
        final GitInfo descriptorGit = cfg.function.git;

        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(cfg), git -> SHA_1);

        assertNotNull(actual);
        assertEquals(SHA_1, actual.gitSourceInfos.get(0).git.commit);
        assertEquals("HEAD", descriptorGit.commit,
            "resolving must not write the sha back into the Function descriptor's own GitInfo");
    }

    // ---------- pinGitRevision: the pin reaching TaskParamsYaml ----------

    private static ExecContextParamsYaml.GitSources pinned(String functionCode, String commit) {
        final ExecContextParamsYaml.GitSources gs = new ExecContextParamsYaml.GitSources();
        gs.gitSourceInfos.add(new ExecContextParamsYaml.GitSourceInfo(functionCode,
            new ExecContextParamsYaml.GitParams(REPO, "main", commit, "fn/" + functionCode)));
        return gs;
    }

    private static TaskParamsYaml.FunctionConfig taskFunctionConfig(String code, EnumsApi.FunctionSourcing sourcing, GitInfo git) {
        final TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.code = code;
        fc.sourcing = sourcing;
        fc.git = git;
        return fc;
    }

    @Test
    public void test_pinReplacesHeadInTheTaskConfig() {
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig(
            "fn-py", EnumsApi.FunctionSourcing.git, new GitInfo(REPO, "main", "HEAD", "fn/fn-py"));

        ExecContextGitSourceUtils.pinGitRevision(fc, pinned("fn-py", SHA_1));

        assertEquals(SHA_1, fc.git.commit);
        assertEquals(REPO, fc.git.repo);
        assertEquals("main", fc.git.branch);
        assertEquals("fn/fn-py", fc.git.path);
    }

    @Test
    public void test_pinDoesNotMutateTheSharedDescriptorGitInfo() {
        final GitInfo shared = new GitInfo(REPO, "main", "HEAD", "fn/fn-py");
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig("fn-py", EnumsApi.FunctionSourcing.git, shared);

        ExecContextGitSourceUtils.pinGitRevision(fc, pinned("fn-py", SHA_1));

        assertEquals("HEAD", shared.commit,
            "toFunctionConfig shares the descriptor's GitInfo, so pinning must assign a new object, never write through");
        assertNotSame(shared, fc.git);
    }

    @Test
    public void test_pinLeavesADispatcherSourcedFunctionAlone() {
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig("fn-jar", EnumsApi.FunctionSourcing.dispatcher, null);

        ExecContextGitSourceUtils.pinGitRevision(fc, pinned("fn-jar", SHA_1));

        assertNull(fc.git, "a dispatcher-sourced Function has no git revision to pin");
    }

    @Test
    public void test_pinIsANoopWhenTheExecContextPinnedNothing() {
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig(
            "fn-py", EnumsApi.FunctionSourcing.git, new GitInfo(REPO, "main", "HEAD", "fn/fn-py"));

        ExecContextGitSourceUtils.pinGitRevision(fc, null);

        assertEquals("HEAD", fc.git.commit,
            "an ExecContext created before pinning existed must still produce tasks");
    }

    @Test
    public void test_pinIsANoopWhenThisCodeWasNotPinned() {
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig(
            "fn-py", EnumsApi.FunctionSourcing.git, new GitInfo(REPO, "main", "HEAD", "fn/fn-py"));

        ExecContextGitSourceUtils.pinGitRevision(fc, pinned("fn-other", SHA_1));

        assertEquals("HEAD", fc.git.commit);
    }

    @Test
    public void test_pinOverridesAnExplicitCommitWithTheExecContextsOwn() {
        final TaskParamsYaml.FunctionConfig fc = taskFunctionConfig(
            "fn-py", EnumsApi.FunctionSourcing.git, new GitInfo(REPO, "main", SHA_2, "fn/fn-py"));

        ExecContextGitSourceUtils.pinGitRevision(fc, pinned("fn-py", SHA_2));

        assertEquals(SHA_2, fc.git.commit,
            "the ExecContext is the authority for what this task runs, even when it agrees with the descriptor");
    }

    @Test
    public void test_findReturnsNullForUnknownCode() {
        final ExecContextParamsYaml.GitSources actual = ExecContextGitSourceUtils.resolveGitSources(
            List.of("fn-py"), registry(gitFunction("fn-py", "HEAD")), git -> SHA_1);

        assertNotNull(actual);
        assertNull(actual.find("fn-nope"));
    }
}
