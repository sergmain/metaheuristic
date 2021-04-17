/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.experiment_result;

import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsV1;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsV2;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.lang.NonNull;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 6:15 PM
 */
public class ExperimentResultParamsJsonUtilsV1
        extends AbstractParamsJsonUtils<ExperimentResultParamsV1, ExperimentResultParamsV2, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public ExperimentResultParamsV2 upgradeTo(@NonNull ExperimentResultParamsV1 src) {
        throw new UpgradeNotSupportedException();
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ExperimentResultParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JsonProcessingException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public ExperimentResultParamsV1 to(@NonNull String s) {
        try {
            final ExperimentResultParamsV1 p = BaseJsonUtils.getMapper().readValue(s, ExperimentResultParamsV1.class);
            return p;
        }
        catch (JsonProcessingException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }
}
