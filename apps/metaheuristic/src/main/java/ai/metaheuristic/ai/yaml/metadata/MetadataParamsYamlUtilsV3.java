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
        extends AbstractParamsYamlUtils<MetadataParamsYamlV3, MetadataParamsYamlV4, MetadataParamsYamlUtilsV4, Void, Void, Void> {

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
    public MetadataParamsYamlV4 upgradeTo(@NonNull MetadataParamsYamlV3 src) {
        src.checkIntegrity();
        MetadataParamsYamlV4 trg = new MetadataParamsYamlV4();
        if (trg.processorSessions==null) {
            throw new IllegalStateException("(trg.processorSessions==null)");
        }
        if (src.processorSessions!=null) {
            for (Map.Entry<String, MetadataParamsYamlV3.ProcessorSessionV3> entry : src.processorSessions.entrySet()) {
                final MetadataParamsYamlV3.ProcessorSessionV3 psV3 = entry.getValue();
                MetadataParamsYamlV4.ProcessorSessionV4 value = new MetadataParamsYamlV4.ProcessorSessionV4(psV3.dispatcherCode, psV3.processorId, psV3.sessionId);
                value.cores.putAll(psV3.cores);

                psV3.quotas.stream().map(MetadataParamsYamlUtilsV3::toQuota).collect(Collectors.toCollection(() -> value.quotas));

                trg.processorSessions.put(entry.getKey(), value);
            }
        }

        trg.checkIntegrity();
        return trg;
    }

    private static MetadataParamsYamlV4.QuotaV4 toQuota(MetadataParamsYamlV3.QuotaV3 sV3) {
        return new MetadataParamsYamlV4.QuotaV4(sV3.taskId, sV3.tag, sV3.quota);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public MetadataParamsYamlUtilsV4 nextUtil() {
        return (MetadataParamsYamlUtilsV4) MetadataParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
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
