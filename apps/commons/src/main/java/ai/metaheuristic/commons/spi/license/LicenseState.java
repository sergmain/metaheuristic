/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.spi.license;

/**
 * License states (Appendix E). valid() is true only for VALID. has(f) is always false when
 * !valid(). The state is surfaced to the admin UI; gating code depends only on valid()/has().
 *
 * @author Serge
 */
public enum LicenseState {
    NO_LICENSE,
    VALID,
    EXPIRED,
    NOT_YET_VALID,
    SIGNATURE_INVALID,
    INSTALL_ID_MISMATCH,
    PROFILE_CONSTRAINT_VIOLATED,
    MALFORMED,
    REVOKED,
    GRACE,
    UNAVAILABLE,
    TAMPER_DETECTED
}
