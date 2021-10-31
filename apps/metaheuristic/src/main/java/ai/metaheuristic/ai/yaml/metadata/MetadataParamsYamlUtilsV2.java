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
 * Date: 12/29/2020
 * Time: 1:29 AM
 */
public class MetadataParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<MetadataParamsYamlV2, MetadataParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(MetadataParamsYamlV2.class);
    }

    @NonNull
    @Override
    public MetadataParamsYaml upgradeTo(@NonNull MetadataParamsYamlV2 src) {
        src.checkIntegrity();
        MetadataParamsYaml trg = new MetadataParamsYaml();
        for (Map.Entry<String, MetadataParamsYamlV2.ProcessorV2> entry : src.processors.entrySet()) {
            MetadataParamsYaml.Processor value =  new MetadataParamsYaml.Processor();
            trg.processors.put(entry.getKey(), value);
            if (entry.getValue()!=null) {
                for (Map.Entry<String, MetadataParamsYamlV2.ProcessorStateV2> stateV2Entry : entry.getValue().states.entrySet()) {
                    MetadataParamsYamlV2.ProcessorStateV2 v = stateV2Entry.getValue();
                    value.states.put(stateV2Entry.getKey(), new MetadataParamsYaml.ProcessorState(v.dispatcherCode, v.processorId, v.sessionId));
                }
            }
        }

        for (Map.Entry<String, String> o : src.metadata.entrySet()) {
            if (!Consts.META_FUNCTION_DOWNLOAD_STATUS.equals(o.getKey())) {
                trg.metadata.put(o.getKey(), o.getValue());
            }
        }
        src.statuses.stream().map(MetadataParamsYamlUtilsV2::toStatus).collect(Collectors.toCollection(()->trg.statuses));
        for (Map.Entry<String, MetadataParamsYamlV2.QuotasV2> entry : src.quotas.entrySet()) {
            MetadataParamsYaml.Quotas q = new MetadataParamsYaml.Quotas();
            trg.quotas.put(entry.getKey(), q);
            entry.getValue().quotas.stream().map(MetadataParamsYamlUtilsV2::toQuota).collect(Collectors.toCollection(()->q.quotas));
        }

        trg.checkIntegrity();
        return trg;
    }

    private static MetadataParamsYaml.Status toStatus(MetadataParamsYamlV2.StatusV2 sV2) {
        return new MetadataParamsYaml.Status(sV2.functionState, sV2.code, sV2.assetManagerUrl, sV2.sourcing, sV2.checksum, sV2.signature);
    }

    private static MetadataParamsYaml.Quota toQuota(MetadataParamsYamlV2.QuotaV2 sV2) {
        return new MetadataParamsYaml.Quota(sV2.taskId, sV2.tag, sV2.quota);
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
    public String toString(@NonNull MetadataParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public MetadataParamsYamlV2 to(@NonNull String s) {
        if (S.b(s)) {
            return new MetadataParamsYamlV2();
        }
        final MetadataParamsYamlV2 p = getYaml().load(s);
        return p;
    }
}
