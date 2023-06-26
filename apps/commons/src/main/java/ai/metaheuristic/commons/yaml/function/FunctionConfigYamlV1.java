/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/3/2019
 * Time: 4:53 PM
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "code")
public class FunctionConfigYamlV1 implements Cloneable, BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (sourcing==null) {
            throw new CheckIntegrityFailedException("sourcing==null");
        }
        return true;
    }

    @SneakyThrows
    public FunctionConfigYamlV1 clone() {
        final FunctionConfigYamlV1 clone = (FunctionConfigYamlV1) super.clone();
        if (this.checksumMap!=null) {
            clone.checksumMap = new HashMap<>(this.checksumMap);
        }
        if (this.metas!=null) {
            clone.metas = new ArrayList<>(this.metas);
        }
        return clone;
    }

    /**
     * code of function, i.e. simple-app:1.0
     */
    public String code;
    @Nullable
    public String type;
    @Nullable
    public String file;
    /**
     * params for command line for invoking function
     * <p>
     * this isn't a holder for yaml-based config
     */
    @Nullable
    public String params;
    public String env;
    public EnumsApi.FunctionSourcing sourcing;
    @Nullable
    public Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
    @Nullable
    public GitInfo git;
    public boolean skipParams = false;
    public @Nullable List<Map<String, String>> metas = new ArrayList<>();
    @Nullable
    public String content;

}
