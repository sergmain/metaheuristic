/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.utils;

import java.util.*;

public class MapUtils {

    // https://stackoverflow.com/a/3074324/2672202
    public static class ValueThenKeyComparator<K extends Comparable<? super K>,
            V extends Comparable<? super V>>
            implements Comparator<Map.Entry<K, V>> {

        public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
            int cmp1 = a.getValue().compareTo(b.getValue());
            if (cmp1 != 0) {
                return cmp1;
            } else {
                return a.getKey().compareTo(b.getKey());
            }
        }

    }

    // https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values
    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(Map<K, V> map) {
        return sortWithComparator(map, Map.Entry.comparingByValue());
    }

    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortWithComparator(Map<K, V> map, Comparator<Map.Entry<K,V>> comparingByValue) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(comparingByValue);

        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValueDesc(Map<K, V> map) {
        List<Map.Entry<K, V>> list1 = new ArrayList<>(map.entrySet());
        list1.sort(Map.Entry.comparingByValue());

        List<Map.Entry<K, V>> list = new ArrayList<>();
        for (int i = list1.size() - 1; i >=0 ; i--) {
            list.add(list1.get(i));
        }

        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

}