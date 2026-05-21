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

import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.commons.account.AccountRoles;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Envelope row for Account. Identity (USERNAME, COMPANY_ID, CREATED_ON) +
 * Spring-Security primitives (PASSWORD, IS_ENABLED, ROLES, IS_ACC_NOT_EXPIRED,
 * IS_NOT_LOCKED, IS_CRED_NOT_EXPIRED) stay here so every authentication is one
 * envelope-row read. Mutable profile/audit scalars (PUBLIC_NAME, MAIL_ADDRESS,
 * PHONE, PHONE_AS_STR, UPDATED_ON, SECRET_KEY, TWO_FA, PARAMS) live in
 * {@link AccountRevision}, append-only, looked up manually by HEAD_REVISION_ID
 * — see AccountService.
 *
 * User: Serg
 * Date: 12.08.13
 * Time: 23:19
 */
@Entity
@Table(name = "MH_ACCOUNT")
@Data
@EqualsAndHashCode(of = {"username", "password"})
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Account implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 708692073045562337L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "USERNAME")
    public String username;

    @Column(name = "PASSWORD")
    public String password;

    @Column(name="IS_ACC_NOT_EXPIRED")
    public boolean accountNonExpired;

    @Column(name="IS_NOT_LOCKED")
    public boolean accountNonLocked;

    @Column(name="IS_CRED_NOT_EXPIRED")
    public boolean credentialsNonExpired;

    @Column(name="IS_ENABLED")
    public boolean enabled;

    @Column(name="CREATED_ON")
    public long createdOn;

    // this field contains Authorities, not role. I.e. authority is "ROLE_" + role
    @Nullable
    public String roles;

    /** Mirror of the head revision's IS_DELETED. Flipped to true alongside a tombstone revision insert. */
    @Column(name = "IS_DELETED")
    public boolean deleted;

    /** Pointer to the latest AccountRevision row for this envelope. Repointed on every new-revision insert. */
    @Nullable
    @Column(name = "HEAD_REVISION_ID")
    public Long headRevisionId;

    @Transient
    @JsonIgnore
    public final AccountRoles accountRoles = new AccountRoles(()-> roles, (o)->roles = o);

    public List<AccountData.SerializableGrantedAuthority> getAuthorities() {
        return accountRoles.getAuthorities().stream()
                .map(o->new AccountData.SerializableGrantedAuthority(o.authority))
                .collect(Collectors.toList());
    }
}

