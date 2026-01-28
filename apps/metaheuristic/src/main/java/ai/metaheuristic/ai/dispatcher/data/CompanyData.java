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

import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 8:57 PM
 */
public class CompanyData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SimpleCompaniesResult extends BaseDataClass {
        public Page<SimpleCompany> companies;
        public EnumsApi.DispatcherAssetMode assetMode;

        @JsonCreator
        public SimpleCompaniesResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class CompaniesResult extends BaseDataClass {
        public Page<Company> companies;
        public EnumsApi.DispatcherAssetMode assetMode;

        @JsonCreator
        public CompaniesResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyAccessControl {
        public String groups;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class CompanyResult extends BaseDataClass {
        public Company company;
        public final CompanyAccessControl companyAccessControl = new CompanyAccessControl();

        @JsonCreator
        public CompanyResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public CompanyResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public CompanyResult(Company company, String errorMessage) {
            this.company = company;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public CompanyResult(Company company) {
            this.company = company;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class SimpleCompanyResult extends BaseDataClass {
        public SimpleCompany company;
        public final CompanyAccessControl companyAccessControl = new CompanyAccessControl();

        @JsonCreator
        public SimpleCompanyResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public SimpleCompanyResult(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SimpleCompanyResult(SimpleCompany company, String errorMessage) {
            this.company = company;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public SimpleCompanyResult(SimpleCompany company) {
            this.company = company;
        }
    }

}
