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
 * A single-use token that sets the password of ONE EXISTING {@link Account}.
 *
 * <p><b>It does not create an account.</b> The account is created first, by an
 * admin who chooses its username and roles through the ordinary account
 * machinery; this row only carries the one-time secret that lets the intended
 * holder take possession of it. Separating creation from possession is what
 * makes the username KNOWN before the token is redeemed — a caller can then be
 * recorded against that account in advance, which is impossible if redemption
 * is what invents the identifier.
 *
 * <p>Deliberately PURPOSE-AGNOSTIC: no calling module is named here, and
 * nothing in this class records why the account exists.
 *
 * <p><b>Single use is enforced by a column, not by cryptography.</b>
 * {@link #redeemedOn} being non-null means redeemed, permanently. The column
 * makes "used" and "no longer valid" the same fact, recorded in the same write,
 * so no caller can check one without the other.
 *
 * <p>{@link #expiredOn} is an absolute epoch-millis deadline held in the row
 * rather than embedded in the token: expiry then needs no sweep job, and
 * evaluating it needs no token parsing and no key material.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Entity
@Table(name = "MH_INVITE")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@ToString(exclude = {"token"})
public class Invite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** {@code mh_company.UNIQUE_ID} of the account's company; scopes admin operations. */
    @Column(name = "COMPANY_ID", nullable = false)
    public Long companyId;

    /** The EXISTING {@link Account} whose password this token sets. */
    @Column(name = "ACCOUNT_ID", nullable = false)
    public Long accountId;

    /** High-entropy random, generated with {@code SecureRandom}. Unique installation-wide. */
    @Column(name = "TOKEN", nullable = false)
    public String token;

    @Nullable
    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "CREATED_ON", nullable = false)
    public long createdOn;

    /** Absolute epoch-millis deadline; past this, redemption is refused. */
    @Column(name = "EXPIRED_ON", nullable = false)
    public long expiredOn;

    @Column(name = "CREATED_BY_ACCOUNT_ID", nullable = false)
    public Long createdByAccountId;

    /** Non-null ⇒ already redeemed. This field IS the single-use guarantee. */
    @Nullable
    @Column(name = "REDEEMED_ON")
    public Long redeemedOn;

    @Column(name = "IS_DELETED", nullable = false)
    public boolean deleted;
}
