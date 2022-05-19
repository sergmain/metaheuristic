/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.utils;

import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;

import java.util.*;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 9:16 PM
 */
public class MetadataUtils {

    public static void fixProcessorCodes(List<String> codes, Map<String, MetadataParamsYaml.ProcessorSession> map) {
        Set<String> forDeletion = new HashSet<>();
        for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> entry : map.entrySet()) {
            final LinkedHashMap<String, Long> cores = entry.getValue().cores;
            for (String key : cores.keySet()) {
                if (!codes.contains(key)) {
                    forDeletion.add(key);
                }
            }
            forDeletion.forEach(cores::remove);

            for (String code : codes) {
                if (!cores.containsKey(code)) {
                    cores.put(code, null);
                }
            }
        }
    }
}
