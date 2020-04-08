/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

@Data
@AllArgsConstructor
public class SimpleVariableAndStorageUrl {
    public final String id;
    public final String variable;
    public final String storageUrl;
    public final String originalFilename;
    public final @Nullable String taskContextId;

    public SimpleVariableAndStorageUrl(Long id, String variable, String storageUrl, String originalFilename, @NonNull String taskContextId) {
        this.id = id.toString();
        this.variable = variable;
        this.storageUrl = storageUrl;
        this.originalFilename = originalFilename;
        this.taskContextId = taskContextId;
    }

    public SimpleVariableAndStorageUrl(Long id, String variable, String storageUrl, String originalFilename) {
        this.id = id.toString();
        this.variable = variable;
        this.storageUrl = storageUrl;
        this.originalFilename = originalFilename;
        this.taskContextId = null;
    }

    public DataStorageParams getParams() {
        return DataStorageParamsUtils.to(storageUrl);
    }
}
