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

package ai.metaheuristic.ai.dispatcher.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static ai.metaheuristic.ai.dispatcher.license.LicenseInstallationUtils.MirrorAction;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The two decisions behind the installation identity. Spring-less on purpose — neither of them
 * needs a database or a filesystem to be decided, so neither needs one to be tested.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationUtilsTest {

    private static final String ID = "3f2c1a90-0000-0000-0000-000000000000";

    @Test
    public void test_mirror_missingFile_isWritten() {
        assertEquals(MirrorAction.WRITE, LicenseInstallationUtils.decideMirror(ID, null));
    }

    @Test
    public void test_mirror_blankFile_isWritten() {
        assertEquals(MirrorAction.WRITE, LicenseInstallationUtils.decideMirror(ID, ""));
        assertEquals(MirrorAction.WRITE, LicenseInstallationUtils.decideMirror(ID, "   \n "));
    }

    @Test
    public void test_mirror_agreeingFile_isLeftAlone() {
        // not rewritten on every boot, and trailing whitespace from an editor is not a disagreement.
        assertEquals(MirrorAction.LEAVE, LicenseInstallationUtils.decideMirror(ID, ID));
        assertEquals(MirrorAction.LEAVE, LicenseInstallationUtils.decideMirror(ID, ID + "\n"));
    }

    @Test
    public void test_mirror_disagreeingFile_isOverwritten_databaseWins() {
        // the file is never adopted: anyone who can write a text file could otherwise re-point
        // this installation's identity and silently move a bound license onto another machine.
        assertEquals(MirrorAction.WRITE, LicenseInstallationUtils.decideMirror(ID, "someone-elses-uuid"));
    }

    @Test
    public void test_pickAuthoritative_noRows_isFirstBoot() {
        assertNull(LicenseInstallationUtils.pickAuthoritative(List.of()));
    }

    @Test
    public void test_pickAuthoritative_singleRow() {
        assertEquals("a", LicenseInstallationUtils.pickAuthoritative(List.of("a")));
    }

    @Test
    public void test_pickAuthoritative_oldestWins() {
        // licences may already have been issued against the first id; picking the newest row
        // would invalidate them, and refusing to boot is forbidden (design Appendix E).
        assertEquals("oldest", LicenseInstallationUtils.pickAuthoritative(List.of("oldest", "newer", "newest")));
    }
}
