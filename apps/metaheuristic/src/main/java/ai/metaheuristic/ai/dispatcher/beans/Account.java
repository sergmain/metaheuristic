/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
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
public class Account implements UserDetails, Serializable, Cloneable {
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

    @Column(name="PUBLIC_NAME")
    public String publicName;

    @Nullable
    @Column(name="MAIL_ADDRESS")
    public String mailAddress;

    @Nullable
    @Column(name="PHONE")
    public String phone;

    //TODO add checks on max length
    @Nullable
    private String phoneAsStr;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="UPDATED_ON")
    public long updatedOn;

    @Nullable
    public String roles;

    @Nullable
    @Column(name="SECRET_KEY")
    public String secretKey;

    @Column(name="TWO_FA")
    public boolean twoFA;

    @Transient
    @JsonIgnore
    public final AccountRoles accountRoles = new AccountRoles(()-> roles, (o)->roles = o);

    public List<AccountData.SerializableGrantedAuthority> getAuthorities() {
        return accountRoles.getAuthorities().stream()
                .map(o->new AccountData.SerializableGrantedAuthority(o.authority))
                .collect(Collectors.toList());
    }

}
