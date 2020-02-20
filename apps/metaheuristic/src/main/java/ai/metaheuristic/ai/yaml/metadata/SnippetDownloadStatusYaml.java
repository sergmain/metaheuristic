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

package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/3/2019
 * Time: 4:51 PM
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SnippetDownloadStatusYaml implements BaseParams {

    public final int version=1;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Status {
        public Enums.SnippetState snippetState;
        public String code;

        // actually this is assetUrl. left it as launchpadUrl until there will be versioning of this config
        public String launchpadUrl;
        public EnumsApi.SnippetSourcing sourcing;
        public boolean verified;
    }

    public List<Status> statuses = new ArrayList<>();

    @Override
    public boolean checkIntegrity() {
        return true;
    }
}
