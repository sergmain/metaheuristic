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
 * A capability the vendor gates on. Opaque to the license manager: identity is the pair of strings,
 * and nothing here knows what any of them mean.
 *
 * A feature is a CATEGORY plus a VALUE, both opaque. The wire form carried inside a token is the
 * composite 'Category:VALUE', so the 'features' claim stays a flat string array and matching stays
 * set membership.
 *
 * There is deliberately NO enum of categories or values anywhere in the license manager - an enum
 * would have to name jcons/Legal/RG concepts and would breach the seal. Callers write their own
 * string literals at the gate site, e.g. new Feature("Capability", "RG").
 *
 * A grant of '<Category>:ANY' satisfies every query in that category; see FeatureMatchUtils.
 *
 * <p>Error code prefix: {@code 01.250.} (unique to this class).
 *
 * @author Serge
 */
public record Feature(String category, String value) {   // e.g. new Feature("Capability", "RG")

    /** Separator between category and value in the wire form. */
    public static final String SEPARATOR = ":";

    /** Wildcard value: granting '<Category>:ANY' grants every value in that category. */
    public static final String ANY = "ANY";

    public Feature {
        if (category == null || category.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("01.250.010 feature category and value must be non-blank");
        }
        if (category.contains(SEPARATOR) || value.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "01.250.020 feature category and value must not contain '" + SEPARATOR + "': " + category + SEPARATOR + value);
        }
    }

    /** The wire form stored in a token and compared during gating. */
    public String key() {
        return category + SEPARATOR + value;
    }
}
