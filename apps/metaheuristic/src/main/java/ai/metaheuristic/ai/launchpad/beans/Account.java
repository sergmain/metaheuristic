/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:19
 */
@Entity
@Table(name = "MH_ACCOUNT")
@Data
@EqualsAndHashCode(of = {"username", "password"})
public class Account implements UserDetails, Serializable, Cloneable {
    private static final long serialVersionUID = 708692073045562337L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

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

    @Column(name="mail_address")
    public String mailAddress;

    @Column(name="PHONE")
    public long phone;

    @Column(name="created_on")
    public long createdOn;

    public String roles;

    @Column(name="SECRET_KEY")
    public String secretKey;

    @Column(name="TWO_FA")
    public boolean twoFA;

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Object clone()  {
        Account a = new Account();
        BeanUtils.copyProperties(this, a);
        return a;
    }

    @Transient
    @JsonIgnore
    private String password2;

    //TODO add checks on max length
    @Transient
    @JsonIgnore
    private String phoneAsStr;

    @Transient
    @JsonIgnore
    private List<String> rolesAsList = null;

    @Transient
    @JsonIgnore
    private List<GrantedAuthority> grantedAuthorities = new ArrayList<>();

    public List<? extends GrantedAuthority> getAuthorities() {
        initRoles();
        return grantedAuthorities;
    }

    @Transient
    @JsonIgnore
    public boolean hasRole(String role) {
        initRoles();
        return rolesAsList.contains(role);
    }

    @Transient
    @JsonIgnore
    public List<String> getRolesAsList() {
        initRoles();
        return rolesAsList;
    }

    @Transient
    @JsonIgnore
    public void storeNewRole(String role) {
        synchronized (this) {
            this.phoneAsStr = role;
            rolesAsList = null;
            grantedAuthorities.clear();
            initRoles();
        }
    }

    private void initRoles() {
        if (rolesAsList==null) {
            synchronized (this) {
                if (rolesAsList==null) {
                    List<String> list = new ArrayList<>();
                    if (roles!=null) {
                        StringTokenizer st = new StringTokenizer(roles, ",");
                        while (st.hasMoreTokens()) {
                            String role = st.nextToken().trim();
                            list.add(role);
                            grantedAuthorities.add(new SimpleGrantedAuthority(role));
                        }
                    }
                    rolesAsList = list;
                }
            }
        }
    }

    public String getLogin() {
        return username;
    }

}
