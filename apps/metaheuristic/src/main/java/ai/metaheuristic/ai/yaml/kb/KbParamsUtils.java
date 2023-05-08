/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.yaml.kb;

import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;

import java.util.Map;

public class KbParamsUtils {

    private static final KbParamsUtilsV1 UTILS_V_1 = new KbParamsUtilsV1();
    private static final KbParamsUtilsV1 DEFAULT_UTILS = UTILS_V_1;

    public static final BaseYamlUtils<KbParams> UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, UTILS_V_1
            ),
            DEFAULT_UTILS
    );
}
