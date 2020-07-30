/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.data.Meta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 9/9/2019
 * Time: 4:49 PM
 */
@Slf4j
public class MetaUtils {

    public static boolean isTrue(@Nullable Meta m) {
        return m!=null && "true".equals(m.getValue());
    }

    public static boolean isFalse(@Nullable Meta m) {
        return !isTrue(m);
    }

    public static boolean isTrue(@Nullable List<Map<String, String>> metas, String... keys) {
        return isTrue(getMeta(metas, keys));
    }

/*
    public static boolean isTrue(@Nullable List<Meta> metas, String... keys) {
        return isTrue(getMeta(metas, keys));
    }
*/

    public static boolean isFalse(@Nullable List<Map<String, String>> metas, String... keys) {
        return isFalse(getMeta(metas, keys));
    }

/*
    public static boolean isFalse(@Nullable List<Meta> metas, String... keys) {
        return isFalse(getMeta(metas, keys));
    }

*/
/*
    public static @Nullable String getValue(@Nullable List<Meta> metas, String... keys) {
        Meta m = getMeta(metas, keys);
        return m!=null ? m.getValue() : null;
    }
*/

    public static @Nullable String getValue(@Nullable List<Map<String, String>> metas, String... keys) {
        Meta m = getMeta(metas, keys);
        return m!=null ? m.getValue() : null;
    }

/*
    public static @Nullable Meta getMeta(@Nullable List<Meta> metas, @NonNull String... keys) {
        if (metas==null) {
            return null;
        }
        if (keys.length==0) {
            return null;
        }
        for (Meta meta : metas) {
            for (String key : keys) {
                if (meta.key.equals(key)) {
                    return meta;
                }
            }
        }
        return null;
    }
*/

    public static @Nullable Meta getMeta(@Nullable List<Map<String, String>> metas, @NonNull String... keys) {
        if (metas==null) {
            return null;
        }
        if (keys.length==0) {
            return null;
        }
        for (Map<String, String> meta : metas) {
            for (String key : keys) {
                // because the map is created from yaml actual Class of value could be Boolean
                Object o = meta.get(key);
                if (o!=null) {
                    return new Meta(key, o.toString(), null);
                }
            }
        }
        return null;
    }
}
