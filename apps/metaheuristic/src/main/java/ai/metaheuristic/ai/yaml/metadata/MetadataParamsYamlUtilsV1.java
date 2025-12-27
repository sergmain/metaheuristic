/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 1:28 AM
 */
public class MetadataParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<MetadataParamsYamlV1, MetadataParamsYamlV2, MetadataParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(MetadataParamsYamlV1.class);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV2 upgradeTo(@NonNull MetadataParamsYamlV1 src) {
        src.checkIntegrity();
        MetadataParamsYamlV2 trg = new MetadataParamsYamlV2();
        if (src.dispatcher!=null) {
            MetadataParamsYamlV2.ProcessorV2 value = new MetadataParamsYamlV2.ProcessorV2();
            for (Map.Entry<String, MetadataParamsYamlV1.DispatcherInfoV1> entry : src.dispatcher.entrySet()) {
                MetadataParamsYamlV1.DispatcherInfoV1 v = entry.getValue();
                value.states.put(entry.getKey(), new MetadataParamsYamlV2.ProcessorStateV2(v.code, v.processorId, v.sessionId));

            }
            trg.processors.put(ConstsApi.DEFAULT_PROCESSOR_CODE, value);
        }

        if (src.metadata!=null) {
            for (Map.Entry<String, String> o : src.metadata.entrySet()) {
                if (!Consts.META_FUNCTION_DOWNLOAD_STATUS.equals(o.getKey())) {
                    trg.metadata.put(o.getKey(), o.getValue());
                }
            }

            String statusYaml = src.metadata.get(Consts.META_FUNCTION_DOWNLOAD_STATUS);
            if (!S.b(statusYaml)) {
                FunctionDownloadStatusYaml fdsy = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.to(statusYaml);
                fdsy.statuses.stream().map(MetadataParamsYamlUtilsV1::toStatus).collect(Collectors.toCollection(() -> trg.statuses));
            }
        }
        trg.checkIntegrity();
        return trg;

    }

    private static MetadataParamsYamlV2.StatusV2 toStatus(FunctionDownloadStatusYaml.Status st) {
        return new MetadataParamsYamlV2.StatusV2(st.functionState, st.code, st.dispatcherUrl, st.sourcing, EnumsApi.ChecksumState.not_yet, EnumsApi.SignatureState.not_yet);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public MetadataParamsYamlUtilsV2 nextUtil() {
        return (MetadataParamsYamlUtilsV2) MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull MetadataParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV1 to(@NonNull String s) {
        if (S.b(s)) {
            return new MetadataParamsYamlV1();
        }
        final MetadataParamsYamlV1 p = getYaml().load(s);
        return p;
    }


}
