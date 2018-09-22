/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Consts {
    public static final String SESSIONID_NAME = "JSESSIONID";

    public static final String SERVER_REST_URL = "/rest-anon/srv";

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    public static final String SEED = "seed";
    public static final String EPOCH = "epoch";

    public static final PageRequest PAGE_REQUEST_1_REC = PageRequest.of(0, 1);

    public static final Map<String, String> EMPTY_UNMODIFIABLE_MAP = Collections.unmodifiableMap(new HashMap<>(0));

    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    public static final String DATASET_DIR = "dataset";
    public static final String SNIPPET_DIR = "snippet";
    public static final String FEATURE_DIR = "feature";
    public static final String EXPERIMENT_DIR = "experiment";

    public static final String RAW_FILE_NAME = "raw-file.txt";
    public static final String METRICS_FILE_NAME = "metrics.yaml";
    public static final String DATASET_FILE_NAME = "dataset.txt";
    public static final String DATASET_FILE_MASK = "dataset-%04d.txt";
    public static final String FEATURE_FILE_MASK = "feature-%04d.txt";
}
