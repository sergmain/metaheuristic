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
package ai.metaheuristic.ai.processor.tasks;

import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of = {"variableId", "context"}, callSuper = false)
public class DownloadVariableTask extends ProcessorRestTask {
    public final Long variableId;
    public final EnumsApi.VariableContext context;
    public final long taskId;
    public final File targetDir;
    public final Long chunkSize;

    @Override
    public String toString() {
        return "DownloadVariableTask{" +
                "variableId='" + variableId + '\'' +
                "context='" + context + '\'' +
                ", targetDir=" + targetDir.getPath() +
                '}';
    }
}
