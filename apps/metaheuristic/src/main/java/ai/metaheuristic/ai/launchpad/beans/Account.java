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

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
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
@EqualsAndHashCode(of = {"username", "password", "token"})
public class Account implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    /**
     * as UUID
     */
    private String username;
    /**
     * as UUID with BCrypt
     */
    private String password;

    @Transient
    private String password2;

    @Column(name="is_acc_not_expired")
    private boolean accountNonExpired;

    @Column(name="is_not_locked")
    private boolean accountNonLocked;

    @Column(name="is_cred_not_expired")
    private boolean credentialsNonExpired;

    @Column(name="is_enabled")
    private boolean enabled;

    @Column(name="PUBLIC_NAME")
    private String publicName;

    private String mailAddress;
    private long phone;

    //TODO add checks on max length
    private String phoneAsStr;

    /**
     * токен для проверки логин/пароля/токена
     */
    private String token;

    @Column(name="created_on")
    private long createdOn;

    private String roles;

    public List<? extends GrantedAuthority> getAuthorities(){
        List<GrantedAuthority> authList = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(roles, ",");
        while (st.hasMoreTokens()) {
            authList.add(new SimpleGrantedAuthority(st.nextToken().trim()));
        }
        return authList;
    }

    public String getLogin() {
        return username + '=' + token;
    }
}
