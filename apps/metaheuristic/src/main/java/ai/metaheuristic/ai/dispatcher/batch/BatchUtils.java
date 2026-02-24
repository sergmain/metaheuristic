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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Sergio Lissner
 * Date: 5/25/2023
 * Time: 7:25 PM
 */
public class BatchUtils {

    public static String getActualExtension(SourceCodeStoredParamsYaml scspy, String defaultResultFileExtension) {
        return getActualExtension(SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source), defaultResultFileExtension);
    }

    static String getActualExtension(SourceCodeParamsYaml scpy, String defaultResultFileExtension) {
        final String ext = MetaUtils.getValue(scpy.source.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION);

        return S.b(ext)
                ? (StringUtils.isNotBlank(defaultResultFileExtension) ? defaultResultFileExtension : CommonConsts.BIN_EXT)
                : ext;
    }

    static void changeStateToPreparing(Batch b) {
            if (b.execState!=Enums.BatchExecState.Unknown.code && b.execState!=Enums.BatchExecState.Stored.code &&
                    b.execState != Enums.BatchExecState.Preparing.code) {
                throw new IllegalStateException("990.020 Can't change state to Preparing, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Preparing.code) {
                return;
            }
            b.execState = Enums.BatchExecState.Preparing.code;
    }
}
