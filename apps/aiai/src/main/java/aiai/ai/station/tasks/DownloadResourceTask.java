/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.station.tasks;

import aiai.ai.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "taskId"}, callSuper = false)
public class DownloadResourceTask extends StationRestTask {
    public String id;
    public long taskId;
    public File targetDir;

    @Override
    public String toString() {
        return "DownloadResourceTask{" +
                "id='" + id + '\'' +
                ", targetDir=" + targetDir.getPath() +
                '}';
    }
}
