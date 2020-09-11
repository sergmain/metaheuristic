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
package ai.metaheuristic.ai.yaml.env;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@ToString
public class EnvYaml {
    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final Map<String, String> envs = new ConcurrentHashMap<>();
    public final List<DiskStorage> disk = new ArrayList<>();

    @Nullable
    public DiskStorage findDiskStorageByCode(String code) {
        for (DiskStorage diskStorage : disk) {
            if (Objects.equals(diskStorage.code, code)) {
                return diskStorage;
            }
        }
        return null;
    }
}
