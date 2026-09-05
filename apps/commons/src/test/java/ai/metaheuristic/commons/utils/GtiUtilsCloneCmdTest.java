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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 12:00 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class GtiUtilsCloneCmdTest {

    private static final String URL = "https://github.com/sergmain/metaheuristic-assets.git";

    @Test
    public void test_shallowIsUnsafeForAnExplicitCommit() {
        assertFalse(GtiUtils.isShallowCloneSafe("main", "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"),
            "an explicit sha isn't reachable in a depth-1 clone, so shallow must be refused");
    }

    @Test
    public void test_shallowIsSafeForHead() {
        assertTrue(GtiUtils.isShallowCloneSafe("main", "HEAD"));
    }

    @Test
    public void test_shallowIsSafeForHeadWithSurroundingSpaces() {
        assertTrue(GtiUtils.isShallowCloneSafe("main", "  HEAD  "));
    }

    @Test
    public void test_shallowIsSafeForBlankCommit() {
        assertTrue(GtiUtils.isShallowCloneSafe("main", null));
        assertTrue(GtiUtils.isShallowCloneSafe("main", ""));
        assertTrue(GtiUtils.isShallowCloneSafe("main", "   "));
    }

    @Test
    public void test_shallowIsUnsafeWithoutABranch() {
        assertFalse(GtiUtils.isShallowCloneSafe(null, "HEAD"),
            "--depth 1 without --branch clones the default branch, which a later pull can't merge into");
        assertFalse(GtiUtils.isShallowCloneSafe("", "HEAD"));
        assertFalse(GtiUtils.isShallowCloneSafe("   ", "HEAD"));
    }

    @Test
    public void test_shallowIsCaseSensitiveForHead() {
        assertFalse(GtiUtils.isShallowCloneSafe("main", "head"),
            "'head' is a legitimate ref name, it isn't the same thing as HEAD");
    }

    @Test
    public void test_cloneCmdWithoutShallowIsTheFullClone() {
        final Path dir = Path.of("/tmp/mh-git").toAbsolutePath();
        final List<String> cmd = GtiUtils.cloneCmd(dir, URL, "main", false);
        assertEquals(List.of("git", "-C", dir.toString(), "clone", URL, "git-repo"), cmd);
    }

    @Test
    public void test_cloneCmdWithShallowCarriesDepthAndBranch() {
        final Path dir = Path.of("/tmp/mh-git").toAbsolutePath();
        final List<String> cmd = GtiUtils.cloneCmd(dir, URL, "main", true);
        assertEquals(List.of("git", "-C", dir.toString(), "clone", "--depth", "1", "--branch", "main", URL, "git-repo"), cmd);
    }

    @Test
    public void test_cloneCmdBranchIsIgnoredWhenNotShallow() {
        final Path dir = Path.of("/tmp/mh-git").toAbsolutePath();
        assertEquals(GtiUtils.cloneCmd(dir, URL, null, false), GtiUtils.cloneCmd(dir, URL, "main", false));
    }

    @Test
    public void test_shallowWithoutABranchTakesTheDefaultBranch() {
        // this is bundle delivery: no branch to give, because it always takes whatever remote HEAD points
        // at - master for metaheuristic-assets, main elsewhere
        final Path dir = Path.of("/tmp/mh-git").toAbsolutePath();
        assertEquals(List.of("git", "-C", dir.toString(), "clone", "--depth", "1", URL, "git-repo"),
            GtiUtils.cloneCmd(dir, URL, null, true));
        assertEquals(GtiUtils.cloneCmd(dir, URL, null, true), GtiUtils.cloneCmd(dir, URL, "  ", true));
    }

    @Test
    public void test_cloneCmdIsImmutable() {
        final Path dir = Path.of("/tmp/mh-git").toAbsolutePath();
        final List<String> cmd = GtiUtils.cloneCmd(dir, URL, "main", true);
        assertThrows(UnsupportedOperationException.class, () -> cmd.add("--quiet"));
    }
}
