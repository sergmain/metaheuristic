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
package aiai.ai.yaml.env;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
public class EnvYaml {
    Map<String, String> envs = new LinkedHashMap<>();
    List<DiskStorage> disk = new ArrayList<>();

    public DiskStorage findDiskStorageByCode(String code) {
        for (DiskStorage diskStorage : disk) {
            if (Objects.equals(diskStorage.code, code)) {
                return diskStorage;
            }
        }
        return null;
    }
}
