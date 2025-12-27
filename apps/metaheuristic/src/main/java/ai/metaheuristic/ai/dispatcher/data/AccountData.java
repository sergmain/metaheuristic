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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.account.SimpleAccount;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AccountData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewAccount {
        public String username;
        public String password;
        public String password2;
        public String publicName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class AccountsResult extends BaseDataClass {
        public Page<SimpleAccount> accounts;
        public EnumsApi.DispatcherAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class AccountResult extends BaseDataClass {
        public SimpleAccount account;

        public AccountResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public AccountResult(SimpleAccount account, String errorMessage) {
            this.account = account;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public AccountResult(SimpleAccount account) {
            this.account = account;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class AccountWithRoleResult extends BaseDataClass {
        public SimpleAccount account;
        public List<String> possibleRoles;

        public AccountWithRoleResult(SimpleAccount account, List<String> possibleRoles, @Nullable List<String> errorMessage) {
            this.account = account;
            this.possibleRoles = possibleRoles;
            this.errorMessages = errorMessage;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializableGrantedAuthority implements GrantedAuthority {
        @Serial
        private static final long serialVersionUID = 8923383713825441981L;

        public String authority;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        public String username;
        public String publicName;
        public Collection<GrantedAuthority> authorities;
        // this field contains value from ai.metaheuristic.ai.dispatcher.beans.Account.companyId
        public Long companyId;
        public String[] profiles;
    }
}
