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
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.Function;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:20 AM
 */
public class ReplicationData {

    public interface ReplicationAsset {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class AssetAcquiringError extends BaseDataClass implements ReplicationAsset {
        public AssetAcquiringError(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionAsset extends BaseDataClass implements ReplicationAsset {
        public Function function;

        public FunctionAsset(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyAsset extends BaseDataClass implements ReplicationAsset {
        public Company company;
        public CompanyAsset(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceCodeAsset extends BaseDataClass implements ReplicationAsset {
        public SourceCodeImpl sourceCode;
        public SourceCodeAsset(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountAsset extends BaseDataClass implements ReplicationAsset {
        public Account account;
        public AccountAsset(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "uid")
    public static class SourceCodeShortAsset {
        public String uid;
        public long updateOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "username")
    public static class AccountShortAsset {
        public String username;
        public long updateOn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "uniqueId")
    public static class CompanyShortAsset {
        public Long uniqueId;
        public long updateOn;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class AssetStateResponse extends BaseDataClass implements ReplicationAsset {
        public final List<String> functions = new ArrayList<>();
        public final List<SourceCodeShortAsset> sourceCodes = new ArrayList<>();
        public final List<CompanyShortAsset> companies = new ArrayList<>();
        public final List<AccountShortAsset> usernames = new ArrayList<>();

        public AssetStateResponse(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public AssetStateResponse(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }
}
