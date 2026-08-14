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
 * A capability the vendor gates on. Opaque to the license manager: identity is the string, and
 * nothing here knows what it means.
 *
 * A feature is ONE name. The wire form carried inside a token is that name unchanged, so the
 * 'capabilities' claim stays a flat string array and matching stays set membership.
 *
 * A name has no parts. Nothing splits it, nothing groups by it, nothing derives meaning from any
 * span of it. A dot in 'MH.BATCH' is an ordinary character: 'MH.BATCH' and 'BATCH' are two
 * unrelated names, neither more specific than the other.
 *
 * There is deliberately NO enum of capability names anywhere in the license manager - an enum would
 * have to name jcons/Legal/RG concepts and would breach the seal. Callers write their own string
 * literals at the gate site, e.g. new Feature("MH.BATCH").
 *
 * UPDATE: a feature used to be two strings joined by a separator, and the wire form was built from
 * them. Nothing was gained by it and three things were paid for it: the issuer validated a shape,
 * this record forbade the separator inside either half, and the UI cut the leading span back off to
 * display a name it had been handed whole.
 *
 * <p>Error code prefix: {@code 01.250.} (unique to this class).
 *
 * @author Serge
 */
public record Feature(String name) {   // e.g. new Feature("MH.BATCH")

    public Feature {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("01.250.010 feature name must be non-blank");
        }
    }

}
