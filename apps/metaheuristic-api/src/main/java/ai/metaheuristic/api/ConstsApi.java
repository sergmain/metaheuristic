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

/**
 * @author Serge
 * Date: 7/5/2019
 * Time: 1:45 AM
 */
public class ConstsApi {

    public static final String ARTIFACTS_DIR = "artifacts";

    // === plan's meta
    //
    public static final String META_MH_RESULT_FILE_EXTENSION = "mh.result-file-extension";


    // === snippets' metas

    //
    public static final String META_MH_SNIPPET_PARAMS_AS_FILE_META = "mh.snippet-params-as-file";

    // extension for scripting file which will be executed as snippet
    public static final String META_MH_SNIPPET_PARAMS_FILE_EXT_META = "mh.snippet-params-file-ext";

    public static final String META_MH_TASK_PARAMS_VERSION = "mh.task-params-version";

    // legacy meta
    @Deprecated
    public static final String META_TASK_PARAMS_VERSION = "task-params-version";


    public static final String META_MH_SNIPPET_SUPPORTED_OS = "mh.snippet-supported-os";

}
