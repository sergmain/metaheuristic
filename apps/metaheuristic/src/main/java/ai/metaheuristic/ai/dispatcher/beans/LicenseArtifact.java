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

package ai.metaheuristic.ai.dispatcher.beans;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * ONE installed license. An installation holds a SET of these and every non-deleted row is live —
 * a trial cannot be extended, so continuing one means installing a second license beside the
 * first, and the effective entitlement is the union of the valid ones.
 *
 * <p>The row is a container for the token and its provenance, nothing more. Licensee, edition,
 * grants and validity all live inside the signed token in {@link #params}; copying any of them
 * into a column would create a second, unsigned copy of the facts the signature exists to protect.
 *
 * <p>{@link #tokenHash} carries a UNIQUE index so re-installing a license you already hold is a
 * no-op rather than a second row. {@link #deleted} is a flag rather than a DELETE so the audit
 * trail of what was once installed survives removal.
 *
 * @author Serge
 */
@Entity
@Table(name = "MH_LICENSE_ARTIFACT")
@Data
@NoArgsConstructor
@ToString(exclude = {"params"})
public class LicenseArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = -3793965896363343406L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** Epoch-millis this license was installed HERE. Audit/display only — never an input to validity. */
    @Column(name = "CREATED_ON", nullable = false)
    public long createdOn;

    /** SHA-256 hex of the compact JWS. Unique: the same license installed twice is one row. */
    @Column(name = "TOKEN_HASH", nullable = false)
    public String tokenHash;

    /** Removed by an operator. There is no revocation for an offline license, so this is the only retirement. */
    @Column(name = "IS_DELETED", nullable = false)
    public boolean deleted;

    /** {@code LicenseArtifactParams} as JSON; carries the compact JWS and its provenance. */
    @Column(name = "PARAMS", nullable = false)
    public String params;
}
