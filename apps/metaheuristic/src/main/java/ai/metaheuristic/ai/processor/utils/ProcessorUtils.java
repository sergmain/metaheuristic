/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.commons.utils.SecUtils;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 7:41 AM
 */
public class ProcessorUtils {

    private static final Map<Integer, PublicKey> publicKeyMap = new HashMap<>();

    public static PublicKey createPublicKey(DispatcherLookupParamsYaml.Asset asset) {
        return publicKeyMap.computeIfAbsent(1, o-> SecUtils.getPublicKey(asset.publicKey));
    }
}