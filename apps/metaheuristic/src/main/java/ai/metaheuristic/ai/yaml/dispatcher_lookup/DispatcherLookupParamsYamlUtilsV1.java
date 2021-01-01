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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 12/31/2020
 * Time: 8:05 AM
 */
public class DispatcherLookupParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<DispatcherLookupParamsYamlV1, DispatcherLookupParamsYamlV2, DispatcherLookupParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherLookupParamsYamlV1.class);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYamlV2 upgradeTo(@NonNull DispatcherLookupParamsYamlV1 src, @Nullable Long ... vars) {
        src.checkIntegrity();
        DispatcherLookupParamsYamlV2 trg = new DispatcherLookupParamsYamlV2();

        src.dispatchers.stream().map(DispatcherLookupParamsYamlUtilsV1::toDispatcher).collect(Collectors.toCollection(() -> trg.dispatchers));

        Set<DispatcherLookupParamsYamlV2.AssetV2> assets = src.dispatchers.stream().filter(o->o.asset!=null).map(o->new DispatcherLookupParamsYamlV2.AssetV2(o.url, o.asset.username, o.asset.password, o.publicKey, o.disabled)).collect(Collectors.toSet());
        trg.assets.addAll(assets);

        trg.checkIntegrity();
        return trg;
    }

    private static DispatcherLookupParamsYamlV2.DispatcherLookupV2 toDispatcher(DispatcherLookupParamsYamlV1.DispatcherLookupV1 v1) {
        return new DispatcherLookupParamsYamlV2.DispatcherLookupV2(
                v1.taskProcessingTime, v1.disabled, v1.url, v1.signatureRequired, v1.publicKey,
                v1.lookupType, v1.authType, v1.restUsername, v1.restPassword, v1.asset==null ? v1.url : v1.asset.url);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public DispatcherLookupParamsYamlUtilsV2 nextUtil() {
        return (DispatcherLookupParamsYamlUtilsV2) DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull DispatcherLookupParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherLookupParamsYamlV1 to(@NonNull String s) {
        if (S.b(s)) {
            return new DispatcherLookupParamsYamlV1();
        }
        final DispatcherLookupParamsYamlV1 p = getYaml().load(s);
        return p;
    }


}
