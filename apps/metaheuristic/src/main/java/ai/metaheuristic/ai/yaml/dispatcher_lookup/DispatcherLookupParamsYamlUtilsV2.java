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
public class DispatcherLookupParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<DispatcherLookupParamsYamlV2, DispatcherLookupParamsYamlV3, DispatcherLookupParamsYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherLookupParamsYamlV2.class);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYamlV3 upgradeTo(@NonNull DispatcherLookupParamsYamlV2 src) {
        src.checkIntegrity();

        DispatcherLookupParamsYamlV3 trg = new DispatcherLookupParamsYamlV3();

        src.dispatchers.stream().map(DispatcherLookupParamsYamlUtilsV2::toDispatcher).collect(Collectors.toCollection(() -> trg.dispatchers));
        src.assetManagers.stream().map(DispatcherLookupParamsYamlUtilsV2::toAsset).collect(Collectors.toCollection(() -> trg.assetManagers));

        Set<DispatcherLookupParamsYamlV3.AssetManagerV3> assets = src.assetManagers.stream().map(DispatcherLookupParamsYamlUtilsV2::toAsset).collect(Collectors.toSet());
        trg.assetManagers.addAll(assets);

        trg.checkIntegrity();
        return trg;
    }

    private static DispatcherLookupParamsYamlV3.DispatcherLookupV3 toDispatcher(DispatcherLookupParamsYamlV2.DispatcherLookupV2 v2) {
        return new DispatcherLookupParamsYamlV3.DispatcherLookupV3(
                v2.taskProcessingTime, v2.disabled, v2.url, v2.signatureRequired, v2.publicKey, v2.lookupType,
                v2.authType, v2.restUsername, v2.restPassword, v2.assetManagerUrl, v2.priority, false);
    }

    private static DispatcherLookupParamsYamlV3.AssetManagerV3 toAsset(DispatcherLookupParamsYamlV2.AssetManagerV2 v2) {
        return new DispatcherLookupParamsYamlV3.AssetManagerV3(v2.url, v2.username, v2.password, v2.publicKey, v2.disabled);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public DispatcherLookupParamsYamlUtilsV3 nextUtil() {
        return (DispatcherLookupParamsYamlUtilsV3) DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull DispatcherLookupParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYamlV2 to(@NonNull String s) {
        if (S.b(s)) {
            return new DispatcherLookupParamsYamlV2();
        }
        final DispatcherLookupParamsYamlV2 p = getYaml().load(s);
        return p;
    }
}
