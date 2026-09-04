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

import static org.junit.jupiter.api.Assertions.*;

/**
 * asCode(repo) is BOTH the on-disk directory name for a repo's object store AND the key GitRepoSync
 * locks on. Those two must agree: if two spellings of one url produced two keys, two threads would hold
 * independent locks over the same directory. These tests pin that agreement.
 *
 * @author Sergio Lissner
 * Date: 9/4/2026
 * Time: 1:45 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class StrUtilsAsCodeTest {

    @Test
    public void test_theAssetsRepoNormalizesToTheExpectedDirName() {
        assertEquals("github_com-sergmain-metaheuristic-assets_git",
            StrUtils.asCode("https://github.com/sergmain/metaheuristic-assets.git"));
    }

    @Test
    public void test_schemeIsStripped() {
        assertEquals(StrUtils.asCode("https://github.com/x/y.git"), StrUtils.asCode("http://github.com/x/y.git"),
            "http and https name the same repo and must share one lock and one directory");
    }

    @Test
    public void test_caseIsNormalized() {
        assertEquals(StrUtils.asCode("https://github.com/x/y.git"), StrUtils.asCode("https://GitHub.COM/X/Y.git"));
    }

    @Test
    public void test_theResultIsSafeAsASingleDirName() {
        final String code = StrUtils.asCode("https://github.com:8443/sergmain/metaheuristic-assets.git#frag");
        assertFalse(code.contains("/"), code);
        assertFalse(code.contains(":"), code);
        assertFalse(code.contains("#"), code);
        assertFalse(code.contains("."), code);
    }

    @Test
    public void test_differentReposStayDifferent() {
        assertNotEquals(StrUtils.asCode("https://github.com/x/y.git"), StrUtils.asCode("https://github.com/x/z.git"));
        assertNotEquals(StrUtils.asCode("https://github.com/a/y.git"), StrUtils.asCode("https://gitlab.com/a/y.git"));
    }
}
