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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.S;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class CollectionUtils {

    public static boolean checkTagAllowed(@Nullable String taskTag, @Nullable String processorTag) {
        boolean taskTagEmpty = S.b(taskTag);
        boolean processorTagEmpty = S.b(processorTag);

        if (taskTagEmpty) {
            return true;
        }
        if (processorTagEmpty) {
            return false;
        }

        return org.springframework.util.CollectionUtils.containsAny(toSet(taskTag), toSet(processorTag));
    }

    private static Set<String> toSet(String tags) {
        final Set<String> set = new HashSet<>();
        String[] arr = StringUtils.split(tags, ',');
        Stream.of(arr).forEach(s-> set.add(s.strip()));
        return set;
    }

    public static boolean isNotEmpty(@Nullable Collection<?> collection) {
        return collection!=null && !collection.isEmpty();
    }

    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(@Nullable Map<?,?> map) {
        return org.springframework.util.CollectionUtils.isEmpty(map);
    }

    public static List<String> toPlainList(Collection<List<String>> inputResourceCodes) {
        final List<String> codes = new ArrayList<>();
        inputResourceCodes.forEach(codes::addAll);
        return codes;
    }

    public static boolean isEquals(@Nullable List<String> l1, @Nullable List<String> l2) {
        if (isEmpty(l1) && isEmpty(l2)) {
            return true;
        }
        if (isEmpty(l1) && isNotEmpty(l2)) {
            return false;
        }
        if (isNotEmpty(l1) && isEmpty(l2)) {
            return false;
        }

        // ###IDEA###, why?
        //noinspection ConstantConditions
        if (l1.size()!=l2.size()) {
            return false;
        }
        for (String s : l1) {
            if (!l2.contains(s)) {
                return false;
            }
        }
        return true;
    }
}
