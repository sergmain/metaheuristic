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

package ai.metaheuristic.commons.yaml.batch;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import javax.annotation.Nonnull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 8/19/2020
 * Time: 3:40 AM
 */
public class BatchItemMappingYamlUtilsV1
        extends AbstractParamsYamlUtils<BatchItemMappingYamlV1, BatchItemMappingYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BatchItemMappingYamlV1.class);
    }

    @Nonnull
    @Override
    public BatchItemMappingYaml upgradeTo(@Nonnull BatchItemMappingYamlV1 src) {
        src.checkIntegrity();
        BatchItemMappingYaml trg = new BatchItemMappingYaml();
        trg.targetDir = src.targetDir;
        trg.filenames = src.filenames;
        trg.key = src.key;
        trg.checkIntegrity();
        return trg;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
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
    public String toString(@Nonnull BatchItemMappingYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public BatchItemMappingYamlV1 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final BatchItemMappingYamlV1 p = getYaml().load(yaml);
        return p;
    }


}
