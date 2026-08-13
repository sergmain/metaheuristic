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

package ai.metaheuristic.commons.spi.license;

/**
 * Thrown by a gate when the running installation is not entitled to the capability being entered.
 *
 * <p>Unchecked on purpose. A gate sits at a capability's entry point, and every caller of that
 * entry point would otherwise have to declare or swallow a condition none of them can do anything
 * about — the only correct handling is at the edge, where it becomes a 4xx or a refusal message.
 *
 * <p>Carries the capability key rather than a licence state. A caller may legitimately want to
 * report WHICH capability was refused; why the licence does not grant it is the admin page's
 * business, not the caller's, and putting the state here would invite gates to branch on it.
 *
 * @author Serge
 */
public class LicenseException extends RuntimeException {

    /** The wire form of the capability that was refused, e.g. {@code Capability:SOMETHING}. */
    public final String featureKey;

    public LicenseException(String message, String featureKey) {
        super(message);
        this.featureKey = featureKey;
    }
}
