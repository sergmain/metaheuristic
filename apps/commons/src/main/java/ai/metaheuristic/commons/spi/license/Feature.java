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
 * A capability the vendor gates on. Opaque to MH: identity is the key string.
 * A record carrying one String, exactly like the dispatcher's SignalKind, so new
 * features are minted by callers (proprietary layers) without editing this type.
 *
 * @author Serge
 */
public record Feature(String key) {                 // e.g. new Feature("FEATURE_A")
    public Feature {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("250.010 feature key must be non-blank");
        }
    }
}
