/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.*;

/**
 * @author Serge
 * Date: 10/22/2020
 * Time: 12:52 PM
 */
public class CacheData {

    public static final Comparator<Sha256PlusLength> SHA_256_PLUS_LENGTH_COMPARATOR = (o1, o2) -> o1.sha256.equals(o2.sha256) ? Long.compare(o1.length, o2.length) : o1.sha256.compareTo(o2.sha256);

    public record SimpleKey(String key, String keyAsStr) {}

    @Data
    @AllArgsConstructor
    public static class Sha256PlusLength  {
        public String sha256;
        public long length;

        public String asString() {
            return sha256 + "###" + length;
        }
    }

    @Data
    public static class FullKey {
        public String functionCode;
        public String funcExec;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
        public final List<Sha256PlusLength> inputs = new ArrayList<>();
        public final List<Sha256PlusLength> metas = new ArrayList<>();

        public FullKey(String functionCode, String funcParams) {
            this.functionCode = functionCode;
            this.funcExec = funcParams;
        }

        @SneakyThrows
        public String asString() {
            return JsonUtils.getMapper().writeValueAsString(this);
        }
    }
}
