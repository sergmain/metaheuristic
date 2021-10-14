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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 8:05 AM
 */
public class DispatcherLookupParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<DispatcherLookupParamsYamlV3, DispatcherLookupParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherLookupParamsYamlV3.class);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYaml upgradeTo(@NonNull DispatcherLookupParamsYamlV3 src) {
        src.checkIntegrity();

        DispatcherLookupParamsYaml trg = new DispatcherLookupParamsYaml();

        src.dispatchers.stream().map(DispatcherLookupParamsYamlUtilsV3::toDispatcher).collect(Collectors.toCollection(() -> trg.dispatchers));
        src.assetManagers.stream().map(DispatcherLookupParamsYamlUtilsV3::toAsset).collect(Collectors.toCollection(() -> trg.assetManagers));

        Set<DispatcherLookupParamsYaml.AssetManager> assets = src.assetManagers.stream().map(DispatcherLookupParamsYamlUtilsV3::toAsset).collect(Collectors.toSet());
        trg.assetManagers.addAll(assets);

        trg.checkIntegrity();
        return trg;
    }

    private static DispatcherLookupParamsYaml.DispatcherLookup toDispatcher(DispatcherLookupParamsYamlV3.DispatcherLookupV3 v3) {
        return new DispatcherLookupParamsYaml.DispatcherLookup(
                v3.taskProcessingTime, v3.disabled, v3.url, v3.signatureRequired, v3.publicKey, v3.lookupType,
                v3.authType, v3.restUsername, v3.restPassword, v3.assetManagerUrl, v3.priority, v3.dataSource);
    }

    private static DispatcherLookupParamsYaml.AssetManager toAsset(DispatcherLookupParamsYamlV3.AssetManagerV3 v3) {
        return new DispatcherLookupParamsYaml.AssetManager(v3.url, v3.username, v3.password, v3.publicKey, v3.disabled);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull DispatcherLookupParamsYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYamlV3 to(@NonNull String s) {
        if (S.b(s)) {
            return new DispatcherLookupParamsYamlV3();
        }
        final DispatcherLookupParamsYamlV3 p = getYaml().load(s);
        return p;
    }
}
