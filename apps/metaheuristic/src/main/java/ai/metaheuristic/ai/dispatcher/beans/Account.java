/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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
@NoArgsConstructor
public class Account implements UserDetails, Serializable, Cloneable {
    private static final long serialVersionUID = 708692073045562337L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
    public Long id;

    @Version
    private Integer version;

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "USERNAME")
    @NonNull
    public String username;

    @Column(name = "PASSWORD")
    @NonNull
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

    @Column(name="MAIL_ADDRESS")
    public String mailAddress;

    @Column(name="PHONE")
    public String phone;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name="UPDATED_ON")
    public long updatedOn;

    @Nullable
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

    @NonNull
    @Transient
    @JsonIgnore
    private String password2;

    @Transient
    @JsonIgnore
    public boolean maskPassword = false;

    @NonNull
    public String getPassword() {
        return maskPassword ? "" : password;
    }

    @NonNull
    public String getPassword2() {
        return maskPassword ? "" : password2;
    }

    //TODO add checks on max length
    @Transient
    @JsonIgnore
    @Nullable
    private String phoneAsStr;

    @Data
    private static class InitedRoles {
        public  boolean inited;
        public final List<String> roles = new ArrayList<>();

        public void reset() {
            inited = false;
            roles.clear();
        }
        public boolean contains(@NonNull String role) {
            if (!inited) {
                throw new IllegalStateException("(!inited)");
            }
            return roles.contains(role);
        }

        public void removeRole(String role) {
            roles.remove(role);
        }

        public @NonNull String asString() {
            return String.join(", ", roles);
        }
    }

    @Transient
    @JsonIgnore
    public final InitedRoles initedRoles = new InitedRoles();

    @Transient
    @JsonIgnore
    private final List<SerializableGrantedAuthority> grantedAuthorities = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializableGrantedAuthority implements GrantedAuthority {
        private static final long serialVersionUID = 8923383713825441981L;
        public String authority;
    }

    public List<SerializableGrantedAuthority> getAuthorities() {
        initRoles();
        return grantedAuthorities;
    }

    @Transient
    @JsonIgnore
    public boolean hasRole(@NonNull String role) {
        initRoles();
        return initedRoles.contains(role);
    }

    @Transient
    @JsonIgnore
    public @NonNull List<String> getRolesAsList() {
        initRoles();
        return Collections.unmodifiableList(initedRoles.roles);
    }

    @Transient
    @JsonIgnore
    public void removeRole(@NonNull String role) {
        synchronized (this) {
            initedRoles.removeRole(role);
            this.roles = initedRoles.asString();
            initedRoles.reset();

            grantedAuthorities.clear();
            initRoles();
        }
    }

    private void initRoles() {
        if (initedRoles.inited) {
            return;
        }
        synchronized (this) {
            if (roles!=null) {
                StringTokenizer st = new StringTokenizer(roles, ",");
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    if (S.b(token)) {
                        continue;
                    }
                    String role = token.trim();
                    initedRoles.roles.add(role);
                    grantedAuthorities.add(new SerializableGrantedAuthority(role));
                }
            }
        }
    }

    @Transient
    @JsonIgnore
    public String getLogin() {
        return username;
    }

}
