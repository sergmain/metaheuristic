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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The two decisions behind the installation identity, as pure functions so they can be pinned
 * without Spring, a database or a filesystem.
 *
 * @author Serge
 */
public class LicenseInstallationUtils {

    private LicenseInstallationUtils() {
    }

    /** What to do with the mirror file, given the authoritative value and what the file holds. */
    public enum MirrorAction {
        /** File is missing, empty, or disagrees — rewrite it from the authoritative value. */
        WRITE,
        /** File already agrees — leave it alone rather than rewriting on every boot. */
        LEAVE
    }

    /**
     * The DB row is authoritative and the file is a convenience copy, so a disagreement is
     * resolved by rewriting the file — never by adopting its value. Adopting it would let anyone
     * who can write a text file re-point this installation's identity and so silently transfer a
     * bound license onto a machine it was not issued for.
     */
    public static MirrorAction decideMirror(String authoritativeId, @Nullable String fileValue) {
        if (fileValue == null || fileValue.isBlank()) {
            return MirrorAction.WRITE;
        }
        return authoritativeId.equals(fileValue.strip()) ? MirrorAction.LEAVE : MirrorAction.WRITE;
    }

    /**
     * Which row is the identity when the "exactly one row" invariant has been violated.
     *
     * <p>Oldest wins. A second row can only appear through a race at first boot or a bad restore,
     * and by then licences may already have been issued against the first id; picking the newest
     * would invalidate them. Failing hard instead would leave the dispatcher unable to boot, which
     * Appendix E forbids — the application MUST always start so an admin can act.
     *
     * @return null when there is no row yet (first boot)
     */
    @Nullable
    public static <T> T pickAuthoritative(List<T> rowsOldestFirst) {
        return rowsOldestFirst.isEmpty() ? null : rowsOldestFirst.getFirst();
    }
}
