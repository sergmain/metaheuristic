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

import ai.metaheuristic.commons.spi.license.LicenseState;
import org.jspecify.annotations.Nullable;

/**
 * The two decisions behind installing a license. Static and Spring-free: neither needs a database
 * to be made, so neither needs one to be tested.
 *
 * @author Serge
 */
public class LicenseArtifactUtils {

    private LicenseArtifactUtils() {
    }

    /** What an upload of an already-known token should do to its row. */
    public enum InstallAction {
        /** No row for this token — write one. */
        CREATE,
        /** A row exists but was removed — un-remove it rather than colliding with the unique index. */
        REVIVE,
        /** A live row already holds this exact token — do nothing and say so. */
        NOOP
    }

    /**
     * Re-installing a license you already hold is not a mistake worth reporting, so the same token
     * twice is a NOOP rather than an error. A row that was removed is REVIVEd rather than inserted:
     * TOKEN_HASH is unique, so a second insert would fail, and removal is a flag precisely so the
     * audit trail of what was once installed survives.
     */
    public static InstallAction decideInstall(@Nullable Boolean existingRowIsDeleted) {
        if (existingRowIsDeleted == null) {
            return InstallAction.CREATE;
        }
        return existingRowIsDeleted ? InstallAction.REVIVE : InstallAction.NOOP;
    }

    /**
     * Whether a verified token may be persisted at all.
     *
     * <p>Only two states are refused, and both mean "this is not a license of ours": the signature
     * did not check out, or the body could not be read. Everything else is accepted and listed
     * with its state.
     *
     * <p>❗ EXPIRED and NOT_YET_VALID are deliberately INSTALLABLE. A license that is valid today
     * expires tomorrow and simply stays in the set as EXPIRED — the set already holds expired
     * licenses by design — so refusing to install one would be inconsistent with the state the
     * system reaches on its own. NOT_YET_VALID is a license installed ahead of its window, which
     * is a normal thing to do. INSTALL_ID_MISMATCH is accepted for a different reason: listing it
     * tells the admin exactly which of the five things went wrong, where a blanket rejection would
     * only say "invalid".
     */
    public static boolean isInstallable(LicenseState state) {
        return state != LicenseState.SIGNATURE_INVALID && state != LicenseState.MALFORMED;
    }
}
