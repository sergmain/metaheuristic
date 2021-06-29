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
package ai.metaheuristic.ai.dispatcher.variable;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class InlineVariable {
    public LinkedHashMap<String, String> params = new LinkedHashMap<>();
    public String path;

    public InlineVariable() {
        this.path = "";
    }

    public InlineVariable(Map<String, String> params, String path) {
        this.params = params instanceof LinkedHashMap ? (LinkedHashMap<String, String>) params : new LinkedHashMap<>(params);
        this.path = path;
    }

    public InlineVariable asClone() {
        return new InlineVariable(new LinkedHashMap<>(params), path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InlineVariable that = (InlineVariable) o;

        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public void put(String key, String value) {
        params.put(key, value);
        path = path + ',' + key+':'+value;
    }

    public LinkedHashMap<String, String> toSortedMap() {
        return params.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
    }

}
