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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 8/18/2023
 * Time: 9:54 PM
 */
public abstract class DispatcherLookupExtendedParams {

    // Collections.unmodifiableMap
    public final Map<ProcessorAndCoreData.DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> lookupExtendedMap;
    public final Map<ProcessorAndCoreData.AssetManagerUrl, DispatcherLookupParamsYaml.AssetManager> assets = new HashMap<>();

    @Data
    public static class DispatcherLookupExtended {
        public final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
        public final DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup;
        public final DispatcherSchedule schedule;

        @Nullable
        public final ThreadUtils.CommonThreadLocker<PublicKey> locker;

        public DispatcherLookupExtended(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup, DispatcherSchedule schedule) {
            this.dispatcherUrl = dispatcherUrl;
            this.dispatcherLookup = dispatcherLookup;
            this.schedule = schedule;
            locker = this.dispatcherLookup.publicKey==null ? null : new ThreadUtils.CommonThreadLocker<>(() -> SecUtils.getPublicKey(this.dispatcherLookup.publicKey));
        }

        @Nullable
        public PublicKey getPublicKey() {
            return locker==null ? null : locker.get();
        }
    }

    public DispatcherLookupExtendedParams(DispatcherLookupParamsYaml dispatcherLookupConfig) {
        Map<ProcessorAndCoreData.DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> dispatcherLookupExtendedMap = Map.of();
        try {
            final Map<ProcessorAndCoreData.DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> map = new HashMap<>();
            for (DispatcherLookupParamsYaml.DispatcherLookup dispatcher : dispatcherLookupConfig.dispatchers) {
                ProcessorAndCoreData.DispatcherUrl dispatcherServerUrl = new ProcessorAndCoreData.DispatcherUrl(dispatcher.url);
                DispatcherLookupExtendedParams.DispatcherLookupExtended lookupExtended = new DispatcherLookupExtendedParams.DispatcherLookupExtended(dispatcherServerUrl, dispatcher, DispatcherSchedule.createDispatcherSchedule(dispatcher.taskProcessingTime));
                map.put(dispatcherServerUrl, lookupExtended);
            }
            dispatcherLookupExtendedMap = Collections.unmodifiableMap(map);
            dispatcherLookupConfig.assetManagers.forEach(asset -> assets.put(new ProcessorAndCoreData.AssetManagerUrl(asset.url), asset));
        }
        finally {
            lookupExtendedMap = dispatcherLookupExtendedMap;
        }
    }

    @Nullable
    public DispatcherLookupExtendedParams.DispatcherLookupExtended getDispatcher(ProcessorAndCoreData.DispatcherUrl dispatcherUrl) {
        return lookupExtendedMap.get(dispatcherUrl);
    }

    public List<ProcessorAndCoreData.DispatcherUrl> getAllEnabledDispatchers() {
        return lookupExtendedMap.values().stream().filter(o->!o.dispatcherLookup.disabled).map(o->o.dispatcherUrl).collect(Collectors.toList());
    }

    @Nullable
    public DispatcherLookupParamsYaml.AssetManager getAssetManager(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        return assets.get(assetManagerUrl);
    }


}
