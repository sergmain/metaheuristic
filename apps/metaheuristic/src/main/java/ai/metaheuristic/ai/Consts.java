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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("DeprecatedIsStillUsed")
public class Consts {

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";

    public static final String WEB_CONTAINER_SESSIONID_NAME = "JSESSIONID";

    public static final long SESSION_TTL = TimeUnit.MINUTES.toMillis(30);
    public static final long SESSION_UPDATE_TIMEOUT = TimeUnit.MINUTES.toMillis(2);


    public static final String SERVER_REST_URL_V2 = "/srv-v2";
    public static final String KEEP_ALIVE_REST_URL = "/keep-alive";
    public static final String UPLOAD_REST_URL = "/upload";
    public static final String VARIABLE_STATUS_REST_URL = "/variable-status";
    public static final String REST_ASSET_URL = "/rest/v1/asset";

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), StandardCharsets.UTF_8);

    public static final String SEED = "seed";

    public static final Long LONG_0 = 0L;
    public static final PageRequest PAGE_REQUEST_1_REC = PageRequest.of(0, 1);
    public static final PageRequest PAGE_REQUEST_10_REC = PageRequest.of(0, 10);
    public static final PageRequest PAGE_REQUEST_20_REC = PageRequest.of(0, 20);
    public static final PageRequest PAGE_REQUEST_100_REC = PageRequest.of(0, 100);

    public static final Map<String, String> EMPTY_UNMODIFIABLE_MAP = Collections.unmodifiableMap(new HashMap<>(0));

    public static final ByteArrayResource ZERO_BYTE_ARRAY_RESOURCE = new ByteArrayResource(new byte[0]);

    // dir at dispatcher where all functions will be stored
    public static final String FUNCTIONS_RESOURCE_DIR = "functions";

    public static final String EXPERIMENT_DIR = "experiment";
    public static final String SYSTEM_DIR = "system";
    public static final String RESOURCES_DIR = "resources";
    public static final String TASK_DIR = "task";

    public static final String METADATA_YAML_FILE_NAME = "metadata.yaml";
    public static final String METADATA_YAML_BAK_FILE_NAME = "metadata.yaml.bak";
    public static final String ENV_YAML_FILE_NAME = "env.yaml";
    public static final String DISPATCHER_YAML_FILE_NAME = "dispatcher.yaml";

    public static final String MH_SYSTEM_CONSOLE_OUTPUT_FILE_NAME = "mh-system-console.log";

    public static final String PARAMS_YAML_MASK = "params-v%d.yaml";
    public static final String TASK_YAML = "task.yaml";

    public static final String MH_EXEC_CONTEXT_INPUT_VARIABLE = "mh.exec-context-input-variable";
    public static final String LOCALHOST_IP = "127.0.0.1";


    public static final String MODEL_ATTR_ERROR_MESSAGE = "errorMessage";
    public static final String MODEL_ATTR_INFO_MESSAGES = "infoMessages";

    public static final String UNKNOWN_INFO = "[unknown]";


    public static final String COMPANIES_CACHE = "companies";
    public static final String ACCOUNTS_CACHE = "accounts";
    public static final String EXEC_CONTEXT_CACHE = "exec_contexts";
    public static final String PROCESSORS_CACHE = "processors";
    public static final String BATCHES_CACHE = "batches";
    public static final String EXPERIMENTS_CACHE = "experiments";
    public static final String SOURCE_CODES_CACHE = "source_codes";
    public static final String FUNCTIONS_CACHE = "functions";
    public static final String DISPATCHERS_CACHE = "dispatchers";

    public static final String MAIN_DOCUMENT_POOL_CODE_FOR_BATCH = "mainDocument";

    public static final String YAML_EXT = ".yaml";
    public static final String YML_EXT = ".yml";
    public static final String ZIP_EXT = ".zip";
    public static final String XML_EXT = ".xml";
    public static final String RAR_EXT = ".rar";

    public static final String HEADER_MH_IS_LAST_CHUNK = "mh-is-last-chunk";
    public static final String HEADER_MH_CHUNK_SIZE = "mh-chunk-size";
    public static final String RESOURCES_TO_CLEAN = "mh-to-clean";

    public static final String META_PREDICTED_DATA = "mh-predicted-data";
    public static final String META_FITTED = "mh-fitted";
    public static final String META_FUNCTION_DOWNLOAD_STATUS = "mh-function-download-status";

    public static final String RESULT_ZIP = "result.zip";
    public static final Long ID_1 = 1L;

    public static final String MH_NOP_FUNCTION = "mh.nop";
    public static final String MH_BATCH_SPLITTER_FUNCTION = "mh.batch-splitter";
    public static final String MH_BATCH_LINE_SPLITTER_FUNCTION = "mh.batch-line-splitter";

    public static final String MH_PERMUTE_VARIABLES_FUNCTION = "mh.permute-variables";
    public static final String MH_PERMUTE_VALUES_OF_VARIABLES_FUNCTION = "mh.permute-values-of-variables";

    public static final String MH_AGGREGATE_FUNCTION = "mh.aggregate";
    public static final String MH_EXPERIMENT_RESULT_PROCESSOR = "mh.experiment-result-processor";
    public static final String MH_EXPERIMENT_REDUCE_VARIABLES = "mh.experiment-reduce-variables";
    public static final String MH_FINISH_FUNCTION = "mh.finish";
    public static final String MH_BATCH_RESULT_PROCESSOR_FUNCTION = "mh.batch-result-processor";
    public static final String MH_EXEC_SOURCE_CODE_FUNCTION = "mh.exec-source-code";
    public static final String MH_EVALUATION_FUNCTION = "mh.evaluation";
    public static final String MH_INLINE_AS_VARIABLE_FUNCTION = "mh.inline-as-variable";
    public static final String MH_REDUCE_VALUES_FUNCTION = "mh.reduce-values";

    public static final ExecContextParamsYaml.FunctionDefinition MH_FINISH_FUNCTION_INSTANCE = new ExecContextParamsYaml.FunctionDefinition(MH_FINISH_FUNCTION, EnumsApi.FunctionExecContext.internal);

    public static final String UNKNOWN_FILENAME_IN_BATCH = Consts.UNKNOWN_INFO;
    public static final String TOP_LEVEL_CONTEXT_ID = "1";

    // Processor's version for communicating with dispatcher
    public static final int PROCESSOR_COMM_VERSION = new ProcessorCommParamsYaml().version;

    public static final String SOURCE_CODE_UID = "source-code-uid";
}
