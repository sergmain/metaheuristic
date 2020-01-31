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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;

public class AccountData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class AccountsResult extends BaseDataClass {
        public Page<Account> accounts;
        public EnumsApi.LaunchpadAssetMode assetMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class AccountResult extends BaseDataClass {
        public Account account;

        public AccountResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public AccountResult(Account account, String errorMessage) {
            this.account = account;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public AccountResult(Account account) {
            this.account = account;
        }
    }
}
