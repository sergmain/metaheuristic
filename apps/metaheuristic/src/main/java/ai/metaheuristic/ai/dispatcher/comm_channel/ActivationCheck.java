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

package ai.metaheuristic.ai.dispatcher.comm_channel;

/**
 * Why a token may not be activated, or {@link #ok} when it may.
 *
 * <p>Includes the success case rather than returning {@code null} for it, so no
 * caller has to null-check and a {@code switch} over the causes can be
 * exhaustive.
 *
 * <p>❗ <b>These values are for the OPERATOR'S LOG and never for the caller.</b>
 * Telling a caller "expired" rather than "not found" confirms the token existed,
 * which turns a refusal into an oracle for enumerating valid tokens. An enum
 * helps keep that true: a String is one concatenation away from ending up in a
 * response message, whereas reaching one of these constants requires a
 * deliberate conversion that stands out in review.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public enum ActivationCheck {

    /** Activatable. */
    ok,

    /** No channel carries this token. */
    notFound,

    /** Withdrawn by an operator, or the channel was revoked. */
    withdrawn,

    /** Already activated. Terminal — the single-use guarantee. */
    alreadyActivated,

    /** Past its absolute deadline. */
    expired;

    public boolean isOk() {
        return this==ok;
    }
}
