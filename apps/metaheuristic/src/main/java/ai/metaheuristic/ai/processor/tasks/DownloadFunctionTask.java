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
package ai.metaheuristic.ai.processor.tasks;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of = {"functionCode", "assetUrl"}, callSuper = false)
public class DownloadFunctionTask extends ProcessorRestTask {
    public final String functionCode;
//    public final TaskParamsYaml.FunctionConfig functionConfig;

    public final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
    public final ProcessorAndCoreData.AssetUrl assetUrl;

/*
    private final Map<Integer, DispatcherLookupParamsYaml.Asset> assetMap = new HashMap<>();

    public DispatcherLookupParamsYaml.Asset getAsset() {
        return assetMap.computeIfAbsent(1, o-> initAsset());
    }

    private DispatcherLookupParamsYaml.Asset initAsset() {

        final DispatcherLookupParamsYaml.Asset asset = dispatcher.asset!=null
                ? dispatcher.asset
                : new DispatcherLookupParamsYaml.Asset(dispatcher.getDispatcherUrl().url, dispatcher.restUsername, dispatcher.restPassword);
        return asset;
    }
*/

}
