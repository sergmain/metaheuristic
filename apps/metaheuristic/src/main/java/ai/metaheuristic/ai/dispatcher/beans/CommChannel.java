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
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * A single-use token that mints ONE {@link Account} bound to one SERVICE.
 *
 * <p>A communication channel is an authenticated account bound to one REST
 * endpoint. Deliberately PURPOSE-AGNOSTIC: {@link #serviceTag} is an opaque
 * label from whichever module declared the service and {@link #grantedRole} is
 * the role that module asked for. Nothing here names a business concept, and
 * <b>what the outside party does with the credential it receives is out of
 * scope.</b>
 *
 * <p><b>Single use is enforced by a column, not by cryptography.</b>
 * {@link #activatedOn} being non-null means used, permanently — one nullable
 * timestamp making "used" and "no longer valid" the same fact, written once, so
 * no caller can check one without the other.
 *
 * <p>{@link #expiredOn} is an absolute epoch-millis deadline held in the row
 * rather than embedded in the token: expiry then needs no sweep job, and
 * evaluating it needs no token parsing and no key material.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Entity
@Table(name = "MH_COMM_CHANNEL")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@ToString(exclude = {"token"})
public class CommChannel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** {@code mh_company.UNIQUE_ID} of the company issuing the channel. */
    @Column(name = "COMPANY_ID", nullable = false)
    public Long companyId;

    /** Opaque service label from the registry; decides {@link #grantedRole}. */
    @Column(name = "SERVICE_TAG", nullable = false)
    public String serviceTag;

    /**
     * The {@code ROLE_*} an activated account receives.
     *
     * <p>Copied from the registry at ISSUE time rather than re-resolved at
     * activation, so the grant is fixed at the moment an operator decided it. A
     * registry edited between issue and activation would otherwise change what a
     * token in someone's hands is worth.
     */
    @Column(name = "GRANTED_ROLE", nullable = false)
    public String grantedRole;

    /** High-entropy random, generated with {@code SecureRandom}. Unique installation-wide. */
    @Column(name = "TOKEN", nullable = false)
    public String token;

    /**
     * Who the issuer MEANT to send this to, recorded at issue time.
     *
     * <p>Audit only — it gates nothing. Whoever activates the token becomes the
     * counterparty; this field is what lets a human later notice that those two
     * are not the same. Recording it at activation would record whoever showed
     * up, which is a different fact and no use at all.
     */
    @Nullable
    @Column(name = "INTENDED_FOR")
    public String intendedFor;

    @Nullable
    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "CREATED_ON", nullable = false)
    public long createdOn;

    /** Absolute epoch-millis deadline; past this, activation is refused. */
    @Column(name = "EXPIRED_ON", nullable = false)
    public long expiredOn;

    @Column(name = "CREATED_BY_ACCOUNT_ID", nullable = false)
    public Long createdByAccountId;

    /** Non-null ⇒ already activated. This field IS the single-use guarantee. */
    @Nullable
    @Column(name = "ACTIVATED_ON")
    public Long activatedOn;

    /** The account minted on activation; null until then. */
    @Nullable
    @Column(name = "ACCOUNT_ID")
    public Long accountId;

    /** Withdrawn before activation, or revoked after it. */
    @Column(name = "IS_DELETED", nullable = false)
    public boolean deleted;
}
