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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class CollectionUtils {

    public static <T> List<T> asList(T t) {
        List<T> list = new ArrayList<T>();
        list.add(t);
        return list;
    }

    public static <T> Set<T> asSet(T t) {
        Set<T> set = new HashSet<>();
        set.add(t);
        return set;
    }

    public static <T> List<List<T>> parseAsPages(List<T> ids, int pageSize) {
        if (ids.isEmpty() || pageSize==0) {
            return List.of();
        }
        List<List<T>> result = new ArrayList<>();
        if (pageSize==1) {
            for (T id : ids) {
                result.add(List.of(id));
            }
            return result;
        }

        int pageNums = (ids.size() / pageSize) + (ids.size() == pageSize ? 0 : 1);
        log.debug("pageNums: {}", pageNums);
        for (int i = 0; i < pageNums; i++) {
            int fromIndex = i * pageSize;
            int toIndex = Math.min(ids.size(), (i + 1) * pageSize);
            log.debug("fromIndex: {}", fromIndex);
            log.debug("toIndex: {}", toIndex);
            final List<T> subList = ids.subList(fromIndex, toIndex);
            log.debug("subList: {}", subList);
            result.add(subList);
        }
        return result;
    }

    public static boolean checkTagAllowed(@Nullable String taskTag, @Nullable String processorTags) {
        boolean taskTagEmpty = S.b(taskTag);
        if (taskTagEmpty) {
            return true;
        }

        boolean processorTagEmpty = S.b(processorTags);
        if (processorTagEmpty) {
            return false;
        }

        return toSet(processorTags).contains(taskTag.strip());
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

    public static boolean isNotEmpty(@Nullable Map<?,?> map) {
        return !org.springframework.util.CollectionUtils.isEmpty(map);
    }

    public static List<String> toPlainList(Collection<List<String>> inputResourceCodes) {
        final List<String> codes = new ArrayList<>();
        inputResourceCodes.forEach(codes::addAll);
        return codes;
    }

    public static boolean isMapEquals( @Nullable Map<String, String> map1, @Nullable Map<String, String> map2) {
        if (isEmpty(map1) && isEmpty(map2)) {
            return true;
        }
        if (isNotEmpty(map1) && isEmpty(map2)) {
            return false;
        }

        if (isEmpty(map1) && isNotEmpty(map2)) {
            return false;
        }
        if (!isEquals(map1.keySet(), map2.keySet())) {
            return false;
        }
        for (String key : map1.keySet()) {
            if (!Objects.equals(map1.get(key), map2.get(key))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isEquals(@Nullable Collection<String> l1, @Nullable Collection<String> l2) {
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
