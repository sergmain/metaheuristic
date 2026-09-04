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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 3:55 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class GtiUtilsLsRemoteTest {

    private static final String URL = "https://github.com/sergmain/metaheuristic-assets.git";
    private static final String SHA = "8f1c2d3e4a5b60718293a4b5c6d7e8f901234567";

    @Test
    public void test_lsRemoteCmdTargetsTheBranchRef() {
        assertEquals(List.of("git", "ls-remote", URL, "refs/heads/main"), GtiUtils.lsRemoteCmd(URL, "main"));
    }

    @Test
    public void test_isHeadRevision() {
        assertTrue(GtiUtils.isHeadRevision("HEAD"));
        assertTrue(GtiUtils.isHeadRevision("  HEAD  "));
        assertTrue(GtiUtils.isHeadRevision(null));
        assertTrue(GtiUtils.isHeadRevision(""));
        assertTrue(GtiUtils.isHeadRevision("   "));
        assertFalse(GtiUtils.isHeadRevision(SHA));
        assertFalse(GtiUtils.isHeadRevision("head"));
        assertFalse(GtiUtils.isHeadRevision("v1.0"));
    }

    @Test
    public void test_isSha() {
        assertTrue(GtiUtils.isSha(SHA));
        assertTrue(GtiUtils.isSha("ABCDEF0123456789abcdef0123456789ABCDEF01"));
        assertFalse(GtiUtils.isSha(null));
        assertFalse(GtiUtils.isSha(""));
        assertFalse(GtiUtils.isSha(SHA.substring(0, 39)), "a short sha isn't what ls-remote returns");
        assertFalse(GtiUtils.isSha(SHA + "0"));
        assertFalse(GtiUtils.isSha("g" + SHA.substring(1)), "'g' isn't a hex digit");
    }

    @Test
    public void test_parseLsRemoteOutputTakesTheSha() {
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput(SHA + "\trefs/heads/main"));
    }

    @Test
    public void test_parseLsRemoteOutputToleratesTrailingNewline() {
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput(SHA + "\trefs/heads/main\n"));
    }

    @Test
    public void test_parseLsRemoteOutputSkipsLeadingBlankLines() {
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput("\n\n" + SHA + "\trefs/heads/main\n"));
    }

    @Test
    public void test_parseLsRemoteOutputHandlesSpaceSeparator() {
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput(SHA + "   refs/heads/main"));
    }

    @Test
    public void test_parseLsRemoteOutputTakesTheFirstOfSeveralRefs() {
        final String other = "0123456789012345678901234567890123456789";
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput(
            SHA + "\trefs/heads/main\n" + other + "\trefs/heads/other"));
    }

    @Test
    public void test_parseLsRemoteOutputOnEmptyIsNull() {
        assertNull(GtiUtils.parseLsRemoteOutput(""), "an unknown branch produces no output at all");
        assertNull(GtiUtils.parseLsRemoteOutput(null));
        assertNull(GtiUtils.parseLsRemoteOutput("\n \n"));
    }

    @Test
    public void test_parseLsRemoteOutputIgnoresNonShaNoise() {
        assertNull(GtiUtils.parseLsRemoteOutput("fatal: repository not found"),
            "an error message on stdout must not be mistaken for a revision");
    }

    @Test
    public void test_parseLsRemoteOutputSkipsNoiseBeforeTheSha() {
        assertEquals(SHA, GtiUtils.parseLsRemoteOutput("warning: redirecting to " + URL + "\n" + SHA + "\trefs/heads/main"));
    }
}
