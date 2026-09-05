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
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ❗ Delivering a bundle from git says NOTHING about a Function's sourcing.
 *
 * <p>A Function whose artifacts sit beside its own mh-function.yaml is an ordinary dispatcher-sourced
 * Function, whether that yaml arrived in a zip or was cloned - it needs no git block at all, and its
 * artifacts are packaged into the bundle as bytes.
 *
 * <p>A git block means something different and narrower: the artifacts live in ANOTHER repo, which may
 * not be the repo the bundle was delivered from.
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 10:40 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class BundleUtilsVerifyTest {

    private static final String SHA = "8f1c2d3e4a5b60718293a4b5c6d7e8f901234567";

    private static Path funcDir;

    @BeforeAll
    public static void setUp() throws Exception {
        funcDir = Files.createTempDirectory("mh-verify-test-");
        Files.createDirectories(funcDir.resolve("src"));
        Files.writeString(funcDir.resolve("src").resolve("fn.jar"), "not really a jar");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (funcDir!=null && Files.exists(funcDir)) {
            org.apache.commons.io.file.PathUtils.deleteDirectory(funcDir);
        }
    }

    private static BundleData.FunctionConfigAndFile artifactsBesideTheYaml() {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = "fn-local:1.0";
        cfg.function.type = "java";
        cfg.function.env = "java-21";
        cfg.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        final FunctionConfigYaml.Target target = new FunctionConfigYaml.Target();
        target.src = "src";
        target.file = "fn.jar";
        cfg.function.targets.put("mh-default", target);
        return new BundleData.FunctionConfigAndFile(cfg, funcDir);
    }

    private static BundleData.FunctionConfigAndFile artifactsInAnotherRepo(String commit, String repo) {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = "fn-remote:1.0";
        cfg.function.type = "python";
        cfg.function.env = "python-3";
        cfg.function.sourcing = EnumsApi.FunctionSourcing.git;
        cfg.function.git = new GitInfo(repo, "master", commit, "fn/remote");
        return new BundleData.FunctionConfigAndFile(cfg, funcDir);
    }

    // ---------------------------------------------------------------- no git block needed

    @Test
    public void test_artifactsBesideTheYamlNeedNoGitBlock() {
        final BundleData.FunctionConfigAndFile fcy = artifactsBesideTheYaml();
        assertNull(fcy.config().function.git, "the common case declares no git block at all");
        assertFalse(BundleUtils.verify(fcy, funcDir), "a Function with its artifacts in place must verify");
    }

    @Test
    public void test_theSameFunctionVerifiesRegardlessOfHowItWasDelivered() {
        // verify() takes only the config and the dir the artifacts are in; there is no delivery parameter,
        // which is the point - a cloned bundle and an uploaded zip reach here identically
        assertFalse(BundleUtils.verify(artifactsBesideTheYaml(), funcDir));
    }

    @Test
    public void test_aMissingArtifactIsStillCaught() {
        final BundleData.FunctionConfigAndFile fcy = artifactsBesideTheYaml();
        fcy.config().function.targets.get("mh-default").file = "absent.jar";
        assertTrue(BundleUtils.verify(fcy, funcDir), "dispatcher sourcing must still require the file to exist");
    }

    // ---------------------------------------------------------------- a git block is verified, not replaced

    @Test
    public void test_aGitSourcedFunctionIsAcceptedAndNoLongerThrows() {
        assertFalse(BundleUtils.verify(artifactsInAnotherRepo(SHA, "https://github.com/other/repo.git"), funcDir),
            "an author-declared git block is the only valid way to say where artifacts live");
        assertFalse(BundleUtils.verify(artifactsInAnotherRepo("HEAD", "https://github.com/other/repo.git"), funcDir));
    }

    @Test
    public void test_aGitSourcedFunctionNeedsNoArtifactsOnDisk() {
        // the artifacts are in another repo, which is not cloned at bundling time - so nothing on disk is
        // checked, unlike dispatcher sourcing
        final Path emptyDir = funcDir.resolve("nothing-here");
        assertFalse(BundleUtils.verify(artifactsInAnotherRepo(SHA, "https://github.com/other/repo.git"), emptyDir));
    }

    @Test
    public void test_aGitSourcedFunctionWithABadRevisionIsRejected() {
        assertTrue(BundleUtils.verify(artifactsInAnotherRepo("v1.0", "https://github.com/other/repo.git"), funcDir),
            "a tag isn't a supported revision");
        assertTrue(BundleUtils.verify(artifactsInAnotherRepo("head", "https://github.com/other/repo.git"), funcDir),
            "'head' isn't HEAD");
    }

    @Test
    public void test_aGitSourcedFunctionWithNoRepoIsRejected() {
        assertTrue(BundleUtils.verify(artifactsInAnotherRepo(SHA, ""), funcDir));
    }

    @Test
    public void test_aGitSourcedFunctionWithNoGitBlockIsRejected() {
        final BundleData.FunctionConfigAndFile fcy = artifactsInAnotherRepo(SHA, "https://github.com/other/repo.git");
        fcy.config().function.git = null;
        assertTrue(BundleUtils.verify(fcy, funcDir));
    }
}
