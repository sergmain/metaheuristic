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

import java.io.Serial;
import java.io.Serializable;

/**
 * The identity of THIS dispatcher installation. Exactly one row, written once at first boot.
 *
 * <p>Its only job is to be the value an {@code installationId}-bound license is checked against.
 * The id itself is a random UUID held in {@link #params}; it is deliberately NOT derived from
 * hardware, MAC or hostname, because re-hosting or scaling a deployment must not invalidate a
 * license bound to it — a derived identity would break exactly at the moment a customer moves
 * machines, which is when they can least afford it.
 *
 * <p>This row is authoritative. The value is also mirrored to a file for operator convenience,
 * but on disagreement the row wins and the file is rewritten.
 *
 * @author Serge
 */
@Entity
@Table(name = "MH_LICENSE_INSTALLATION")
@Data
@NoArgsConstructor
public class LicenseInstallation implements Serializable {

    @Serial
    private static final long serialVersionUID = 648230566337180990L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "CREATED_ON", nullable = false)
    public long createdOn;

    /** {@code LicenseInstallationParams} as JSON; carries the installation UUID. */
    @Column(name = "PARAMS", nullable = false)
    public String params;
}
