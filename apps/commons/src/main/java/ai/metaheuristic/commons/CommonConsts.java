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

package ai.metaheuristic.commons;

import java.time.format.DateTimeFormatter;

public class CommonConsts {

    public static final String FIT_TYPE = "fit";
    public static final String PREDICT_TYPE = "predict";
    public static final String CHECK_FITTING_TYPE = "check-fitting";

    public static final String REST_V1_URL = "/rest/v1";
    public final static DateTimeFormatter EVENT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final String MULTI_LANG_STRING = "ИИИ, 日本語, natürlich";
    public static final String METAHEURISTIC_TEMP = "metaheuristic-temp";

    public static final int NODEJS_STANDALONE_PORT_NUMBER = 64968;
    public static final String METAHEURISTIC_USERHOME_PATH = ".metaheuristic";

    public static final String FRONTEND_URL = "http://localhost:"+ CommonConsts.NODEJS_STANDALONE_PORT_NUMBER;
    public static final String APP_UUID_NONE = "none";


    public static final String MH_FUNCTION_YAML = "mh-function.yaml";
    public static final String MH_BUNDLE_YAML = "mh-bundle.yaml";
    public static final String DEFAULT_FUNCTION_SRC_DIR = "src";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String GIT_REPO = "git-repo";
    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
}
