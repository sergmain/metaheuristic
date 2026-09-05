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
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A git-sourced Function's revision may be a full sha or HEAD, and nothing else. Tags in particular are
 * refused: a tag can be moved or deleted, so it looks stable while being able to change what an
 * already-running ExecContext believes it pinned.
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 7:05 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class GitSupportedRevisionTest {

    private static final String SHA = "8f1c2d3e4a5b60718293a4b5c6d7e8f901234567";

    private static FunctionConfigYaml gitFunction(String commit) {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = "fn-py:1.0";
        cfg.function.type = "python";
        cfg.function.env = "python-3";
        cfg.function.sourcing = EnumsApi.FunctionSourcing.git;
        cfg.function.git = new GitInfo("https://github.com/sergmain/metaheuristic-assets.git", "main", commit, "fn");
        return cfg;
    }

    // ---------------------------------------------------------------- the predicate

    @Test
    public void test_aFullShaIsSupported() {
        assertTrue(GtiUtils.isSupportedRevision(SHA));
        assertTrue(GtiUtils.isSupportedRevision("  " + SHA + "  "));
        assertTrue(GtiUtils.isSupportedRevision(SHA.toUpperCase()));
    }

    @Test
    public void test_headIsSupported() {
        assertTrue(GtiUtils.isSupportedRevision("HEAD"));
        assertTrue(GtiUtils.isSupportedRevision("  HEAD "));
        assertTrue(GtiUtils.isSupportedRevision(null), "an unset commit means the same as HEAD");
        assertTrue(GtiUtils.isSupportedRevision(""));
    }

    @Test
    public void test_aTagIsNotSupported() {
        assertFalse(GtiUtils.isSupportedRevision("v1.0"));
        assertFalse(GtiUtils.isSupportedRevision("v1.0.3"));
        assertFalse(GtiUtils.isSupportedRevision("release-2026-09"));
        assertFalse(GtiUtils.isSupportedRevision("refs/tags/v1.0"));
    }

    @Test
    public void test_aBranchNameIsNotSupported() {
        assertFalse(GtiUtils.isSupportedRevision("main"));
        assertFalse(GtiUtils.isSupportedRevision("master"));
        assertFalse(GtiUtils.isSupportedRevision("origin/main"));
    }

    @Test
    public void test_aShortShaIsNotSupported() {
        assertFalse(GtiUtils.isSupportedRevision(SHA.substring(0, 7)),
            "a short sha is ambiguous as the repo grows");
        assertFalse(GtiUtils.isSupportedRevision(SHA.substring(0, 39)));
    }

    @Test
    public void test_aRelativeRevisionIsNotSupported() {
        assertFalse(GtiUtils.isSupportedRevision("HEAD~2"));
        assertFalse(GtiUtils.isSupportedRevision("HEAD^"));
        assertFalse(GtiUtils.isSupportedRevision("head"), "'head' is a legitimate ref name, not HEAD");
    }

    @Test
    public void test_theMessageNamesWhatWasFoundAndWhatIsAllowed() {
        final String msg = GtiUtils.unsupportedRevisionMessage("v1.0");
        assertTrue(msg.contains("v1.0"), msg);
        assertTrue(msg.contains("sha"), msg);
        assertTrue(msg.contains("HEAD"), msg);
        assertTrue(msg.contains("Tags"), msg);
    }

    // ---------------------------------------------------------------- registration-time validation

    @Test
    public void test_registrationAcceptsAShaAndHead() {
        assertTrue(FunctionCoreUtils.validate(gitFunction(SHA).function).isOk);
        assertTrue(FunctionCoreUtils.validate(gitFunction("HEAD").function).isOk);
    }

    @Test
    public void test_registrationRejectsATag() {
        final FunctionApiData.FunctionConfigStatus status = FunctionCoreUtils.validate(gitFunction("v1.0").function);
        assertFalse(status.isOk, "a tag must not be registrable");
        assertNotNull(status.error);
        assertTrue(status.error.startsWith("401.043"), status.error);
        assertTrue(status.error.contains("v1.0"), status.error);
    }

    @Test
    public void test_registrationRejectsABranchName() {
        final FunctionApiData.FunctionConfigStatus status = FunctionCoreUtils.validate(gitFunction("main").function);
        assertFalse(status.isOk);
        assertNotNull(status.error);
        assertTrue(status.error.startsWith("401.043"), status.error);
    }

    @Test
    public void test_registrationStillRejectsAMissingGitBlock() {
        final FunctionConfigYaml cfg = gitFunction(SHA);
        cfg.function.git = null;
        final FunctionApiData.FunctionConfigStatus status = FunctionCoreUtils.validate(cfg.function);
        assertFalse(status.isOk);
        assertNotNull(status.error);
        assertTrue(status.error.startsWith("401.042"), status.error);
    }

    @Test
    public void test_aDispatcherSourcedFunctionIsUnaffected() {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = "fn-jar:1.0";
        cfg.function.type = "java";
        cfg.function.env = "java-21";
        cfg.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        final FunctionConfigYaml.Target target = new FunctionConfigYaml.Target();
        target.file = "fn.jar";
        cfg.function.targets.put("mh-default", target);

        assertTrue(FunctionCoreUtils.validate(cfg.function).isOk,
            "the revision rule applies to git sourcing only");
    }
}
