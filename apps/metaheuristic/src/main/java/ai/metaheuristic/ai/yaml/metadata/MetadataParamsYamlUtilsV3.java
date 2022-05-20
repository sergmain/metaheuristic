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

package ai.metaheuristic.ai.yaml.metadata;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 6:16 PM
 */
public class MetadataParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<MetadataParamsYamlV3, MetadataParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(MetadataParamsYamlV3.class);
    }

    @NonNull
    @Override
    public MetadataParamsYaml upgradeTo(@NonNull MetadataParamsYamlV3 src) {
        src.checkIntegrity();
        MetadataParamsYaml trg = new MetadataParamsYaml();
        if (trg.processorSessions==null || trg.metadata==null) {
            throw new IllegalStateException("(trg.processorSessions==null || trg.metadata==null)");
        }
        if (src.processorSessions!=null) {
            for (Map.Entry<String, MetadataParamsYamlV3.ProcessorSessionV3> entry : src.processorSessions.entrySet()) {
                final MetadataParamsYamlV3.ProcessorSessionV3 psV3 = entry.getValue();
                MetadataParamsYaml.ProcessorSession value = new MetadataParamsYaml.ProcessorSession(psV3.dispatcherCode, psV3.processorId, psV3.sessionId);
                value.cores.putAll(psV3.cores);

                psV3.quotas.stream().map(MetadataParamsYamlUtilsV3::toQuota).collect(Collectors.toCollection(() -> value.quotas));

                trg.processorSessions.put(entry.getKey(), value);
            }
        }

        if (src.metadata!=null) {
            for (Map.Entry<String, String> o : src.metadata.entrySet()) {
                if (!Consts.META_FUNCTION_DOWNLOAD_STATUS.equals(o.getKey())) {
                    trg.metadata.put(o.getKey(), o.getValue());
                }
            }
        }
        src.functions.stream().map(MetadataParamsYamlUtilsV3::toStatus).collect(Collectors.toCollection(()->trg.functions));

        trg.checkIntegrity();
        return trg;
    }

    private static MetadataParamsYaml.Function toStatus(MetadataParamsYamlV3.FunctionV3 sV3) {
        return new MetadataParamsYaml.Function(sV3.state, sV3.code, sV3.assetManagerUrl, sV3.sourcing, sV3.checksum, sV3.signature);
    }

    private static MetadataParamsYaml.Quota toQuota(MetadataParamsYamlV3.QuotaV3 sV3) {
        return new MetadataParamsYaml.Quota(sV3.taskId, sV3.tag, sV3.quota);
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
    public String toString(@NonNull MetadataParamsYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV3 to(@NonNull String s) {
        if (S.b(s)) {
            return new MetadataParamsYamlV3();
        }
        final MetadataParamsYamlV3 p = getYaml().load(s);
        return p;
    }
}
