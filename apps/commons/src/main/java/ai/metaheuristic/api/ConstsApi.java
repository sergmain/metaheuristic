/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.api;

import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

/**
 * @author Serge
 * Date: 7/5/2019
 * Time: 1:45 AM
 */
public class ConstsApi {

    public static final String ARTIFACTS_DIR = "artifacts";
    public static final String MH_ENV_FILE = "mh-env.yaml";

    // name of inline variable
    public static final String MH_HYPER_PARAMS = "mh.hyper-params";

    // === source code's meta
    //
    public static final String META_MH_RESULT_FILE_EXTENSION = "mh.result-file-extension";


    // === functions' metas

    //
    public static final String META_MH_FUNCTION_PARAMS_AS_FILE_META = "mh.function-params-as-file";

    // extension for scripting file which will be executed as a function
    public static final String META_MH_FUNCTION_PARAMS_FILE_EXT_META = "mh.function-params-file-ext";

    public static final String META_MH_TASK_PARAMS_VERSION = "mh.task-params-version";

    public static final String META_MH_FITTING_DETECTION_SUPPORTED = "mh.fitting-detection-supported";

    public static final String META_MH_FUNCTION_SUPPORTED_OS = "mh.function-supported-os";

    // === functions' specific metas

    public static final String META_MH_OUTPUT_IS_DYNAMIC = "output-is-dynamic";

    // ===

    public static final String EMPTY_GRAPH = "strict digraph G { }";

    public static final YamlVersion YAML_VERSION_1 = new YamlVersion();

    public final static SourceCodeApiData.SourceCodeValidationResult SOURCE_CODE_VALIDATION_RESULT_OK = new SourceCodeApiData.SourceCodeValidationResult(OK, null);
}
