/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.api.data.account;

import ai.metaheuristic.commons.account.AccountRoles;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

import java.util.List;

/**
 * @author Serge
 * Date: 8/30/2020
 * Time: 3:08 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleAccount {
    public Long id;
    public Long companyId;
    public String username;
    public String publicName;
    public boolean enabled;
    public long createdOn;
    public long updatedOn;
    public String roles;

    @Transient
    @JsonIgnore
    public final AccountRoles accountRoles = new AccountRoles(()-> roles, (o)->roles = o);

    @SuppressWarnings("unused")
    public List<AccountApiData.SerializableGrantedAuthority> getAuthorities() {
        return accountRoles.getAuthorities();
    }
}
