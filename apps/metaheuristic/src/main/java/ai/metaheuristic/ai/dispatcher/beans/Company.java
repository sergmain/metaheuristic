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

package ai.metaheuristic.ai.dispatcher.beans;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Envelope row for Company. Identity-only: ID, VERSION, UNIQUE_ID + revision plumbing.
 * Mutable scalars (NAME, PARAMS) live in {@link CompanyRevision}, append-only,
 * looked up manually by HEAD_REVISION_ID — see CompanyService.
 *
 * @author Serge
 * Date: 10/27/2019
 * Time: 7:10 PM
 */
@Entity
@Table(name = "MH_COMPANY")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Company implements Serializable {
    @Serial
    private static final long serialVersionUID = -159889135750827404L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "UNIQUE_ID")
    public Long uniqueId;

    /** Mirror of the head revision's IS_DELETED. Flipped to true alongside a tombstone revision insert. */
    @Column(name = "IS_DELETED")
    public boolean deleted;

    /** Pointer to the latest CompanyRevision row for this envelope. Repointed on every new-revision insert. */
    @Nullable
    @Column(name = "HEAD_REVISION_ID")
    public Long headRevisionId;
}

