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

package ai.metaheuristic.api;

import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;

import java.time.Duration;
import java.time.Period;

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

    public static final String META_MH_TASK_PARAMS_VERSION = "mh.task-params-version";

    public static final String META_MH_FUNCTION_SUPPORTED_OS = "mh.function-supported-os";

    // === functions' specific metas

    // ===

    public static final String EMPTY_GRAPH = "strict digraph G { }";

    public static final ParamsVersion PARAMS_VERSION_1 = new ParamsVersion();

    public final static SourceCodeApiData.SourceCodeValidationResult SOURCE_CODE_VALIDATION_RESULT_OK = new SourceCodeApiData.SourceCodeValidationResult(OK, null);

    public static final String DEFAULT_PROCESSOR_CODE = "processor-01";

    public static final Duration SECONDS_1 = Duration.ofSeconds(1);
    public static final Duration SECONDS_3 = Duration.ofSeconds(3);
    public static final Duration SECONDS_5 = Duration.ofSeconds(5);
    public static final Duration SECONDS_6 = Duration.ofSeconds(6);
    public static final Duration SECONDS_9 = Duration.ofSeconds(9);
    public static final Duration SECONDS_10 = Duration.ofSeconds(10);
    public static final Duration SECONDS_11 = Duration.ofSeconds(11);
    public static final Duration SECONDS_19 = Duration.ofSeconds(19);
    public static final Duration SECONDS_23 = Duration.ofSeconds(23);
    public static final Duration SECONDS_29 = Duration.ofSeconds(29);
    public static final Duration SECONDS_31 = Duration.ofSeconds(31);
    public static final Duration SECONDS_60 = Duration.ofSeconds(60);
    public static final Duration SECONDS_120 = Duration.ofSeconds(120);
    public static final Duration SECONDS_300 = Duration.ofSeconds(300);
    public static final Duration SECONDS_3600 = Duration.ofSeconds(3600);
    public static final Duration DAYS_14 = Duration.ofDays(14);
    public static final Period DAYS_90 = Period.ofDays(90);
    public static final Period DAYS_IN_YEARS_3 = Period.ofDays(365*3);
}
