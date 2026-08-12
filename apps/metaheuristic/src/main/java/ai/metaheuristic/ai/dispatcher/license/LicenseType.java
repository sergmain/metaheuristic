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

/**
 * Which license backend is active. Derived from the bean that is actually wired, never guessed at
 * a call site and never branched on by gating code — it exists so the admin UI can say which
 * authority decides validity here.
 *
 * @author Serge
 */
public enum LicenseType {

    /** Scenario B: offline signed files, authority = us (the vendor). Profile {@code internal-lm}. */
    INTERNAL,

    /** Scenario A: AWS License Manager, authority = AWS. Profile {@code aws-lm} — reserved, not wired. */
    EXTERNAL
}
