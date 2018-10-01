/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.beans;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:19
 */
@Entity
@Table(name = "AIAI_ACCOUNT")
@Data
@EqualsAndHashCode(of = {"username", "password", "token"})
public class Account implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * as UUID
     */
    private String username;
    /**
     * as UUID with BCrypt
     */
    private String password;
    private String authorities;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private boolean enabled;
    private String mailAddress;
    private long phone;
    //TODO add checks on max length
    private String phoneAsStr;
    /**
     * токен для проверки логин/пароля/токена
     */
    private String token;

    public Collection<GrantedAuthority> getAuthorities() {
        StringTokenizer st = new StringTokenizer(authorities, ",");
        List<GrantedAuthority> authorityList = new ArrayList<>();
        while (st.hasMoreTokens()) {
            authorityList.add(new SimpleGrantedAuthority(st.nextToken()));
        }
        return authorityList;
    }

}
