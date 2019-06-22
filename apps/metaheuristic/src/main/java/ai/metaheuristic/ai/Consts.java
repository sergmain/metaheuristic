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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.EnumsApi;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Consts {

    public static final int TASK_ORDER_START_VALUE = 1;

    public static final String PROTOCOL_DELIMITER = "://";
    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";

    public static final String SESSIONID_NAME = "JSESSIONID";

    public static final String REST_V1_URL = "/rest/v1";

    public static final String SERVER_REST_URL = "/srv";
    public static final String PAYLOAD_REST_URL = "/payload";
    public static final String UPLOAD_REST_URL = "/upload";

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    public static final String SEED = "seed";
    public static final String EPOCH = "epoch";

    public static final PageRequest PAGE_REQUEST_1_REC = PageRequest.of(0, 1);
    public static final PageRequest PAGE_REQUEST_10_REC = PageRequest.of(0, 10);
    public static final PageRequest PAGE_REQUEST_20_REC = PageRequest.of(0, 20);

    public static final Map<String, String> EMPTY_UNMODIFIABLE_MAP = Collections.unmodifiableMap(new HashMap<>(0));

    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    public static final String SNIPPET_DIR = "snippet";
    public static final String DATABASE_DIR = "database";
    public static final String EXPERIMENT_DIR = "experiment";
    public static final String SYSTEM_DIR = "system";
    public static final String RESOURCES_DIR = "resources";
    public static final String TASK_DIR = "task";
    public static final String ENV_HOT_DEPLOY_DIR = "env-hot-deploy";
    public static final String ARTIFACTS_DIR = "artifacts";

    public static final String METADATA_YAML_FILE_NAME = "metadata.yaml";
    public static final String RAW_FILE_NAME = "raw-file.txt";
    public static final String ENV_YAML_FILE_NAME = "env.yaml";
    public static final String LAUNCHPAD_YAML_FILE_NAME = "launchpad.yaml";

    public static final String METRICS_FILE_NAME = "metrics.yaml";
    public static final String SYSTEM_CONSOLE_OUTPUT_FILE_NAME = "system-console.log";

    public static final String PARAMS_YAML_MASK = "params-v%d.yaml";
    public static final String TASK_YAML = "task.yaml";

    public static final String WORKBOOK_INPUT_TYPE = "workbook-input-type";
    public static final String ML_MODEL_BIN = "ml_model.bin";
    public static final String LOCALHOST_IP = "127.0.0.1";


    public static final String MODEL_ATTR_ERROR_MESSAGE = "errorMessage";
    public static final String MODEL_ATTR_INFO_MESSAGES = "infoMessages";

    public static final String RESULT_FILE_EXTENSION = "result-file-extension";
    public static final String UNKNOWN_INFO = "[unknown]";


    public static final String STATIONS_CACHE = "stations";
    public static final String BATCHES_CACHE = "batches";
    public static final String EXPERIMENTS_CACHE = "experiments";
    public static final String PLANS_CACHE = "plans";
    public static final String SNIPPETS_CACHE = "snippets";
    public static final String MAIN_DOCUMENT_POOL_CODE_FOR_BATCH = "mainDocument";

}
