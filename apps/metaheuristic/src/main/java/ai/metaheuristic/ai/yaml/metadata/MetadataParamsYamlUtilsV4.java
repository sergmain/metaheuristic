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

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 6:16 PM
 */
public class MetadataParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<MetadataParamsYamlV4, MetadataParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(MetadataParamsYamlV4.class);
    }

    @NonNull
    @Override
    public MetadataParamsYaml upgradeTo(@NonNull MetadataParamsYamlV4 src) {
        src.checkIntegrity();
        MetadataParamsYaml trg = new MetadataParamsYaml();
        if (trg.processorSessions==null) {
            throw new IllegalStateException("(trg.processorSessions==null)");
        }
        if (src.processorSessions!=null) {
            for (Map.Entry<String, MetadataParamsYamlV4.ProcessorSessionV4> entry : src.processorSessions.entrySet()) {
                final MetadataParamsYamlV4.ProcessorSessionV4 psV4 = entry.getValue();
                MetadataParamsYaml.ProcessorSession value = new MetadataParamsYaml.ProcessorSession(psV4.dispatcherCode, psV4.processorId, psV4.sessionId);
                value.cores.putAll(psV4.cores);

                psV4.quotas.stream().map(MetadataParamsYamlUtilsV4::toQuota).collect(Collectors.toCollection(() -> value.quotas));

                trg.processorSessions.put(entry.getKey(), value);
            }
        }

        trg.checkIntegrity();
        return trg;
    }

    private static MetadataParamsYaml.Quota toQuota(MetadataParamsYamlV4.QuotaV4 sV3) {
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
    public String toString(@NonNull MetadataParamsYamlV4 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV4 to(@NonNull String s) {
        if (S.b(s)) {
            return new MetadataParamsYamlV4();
        }
        final MetadataParamsYamlV4 p = getYaml().load(s);
        return p;
    }
}
