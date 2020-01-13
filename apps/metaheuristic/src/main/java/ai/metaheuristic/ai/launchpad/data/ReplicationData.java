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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static class SnippetAsset extends BaseDataClass implements ReplicationAsset {
        public Snippet snippet;

        public SnippetAsset(List<String> errorMessages) {
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
    public static class PlanAsset extends BaseDataClass implements ReplicationAsset {
        public PlanImpl plan;
        public PlanAsset(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class PlanShortAsset {
        public String code;
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
        public final List<String> snippets = new ArrayList<>();
        public final List<PlanShortAsset> plans = new ArrayList<>();
        public final List<CompanyShortAsset> companies = new ArrayList<>();
        public final List<String> usernames = new ArrayList<>();

        public AssetStateResponse(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public AssetStateResponse(List<String> errorMessages) {
            addErrorMessages(errorMessages);
        }
    }

    @Data
    public static class AssetStateRequest {
        public final Map<Enums.AssetType, Long> assetTimestamp = new HashMap<>();
    }

}
