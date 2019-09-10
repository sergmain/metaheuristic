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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Serge
 * Date: 9/9/2019
 * Time: 4:49 PM
 */
@Slf4j
public class MetaUtils {

    public static Meta getMeta(List<Meta> metas, String... keys) {
        if (metas==null) {
            return null;
        }
        if (keys==null || keys.length==0) {
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
}
