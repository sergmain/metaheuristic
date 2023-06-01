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

package ai.metaheuristic.ww2003.utils;

import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public final class CollectionUtils {

    private CollectionUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Stream<T> reversedStream(Collection<T> collection) {
        Object[] temp = collection.toArray();
        return (Stream<T>) reversedStream(temp);
    }

    public static <T> Stream<T> reversedStream(T[] array) {
        return IntStream.range(0, array.length).mapToObj(i -> array[array.length - i - 1]);
    }

    public static boolean equalListsOfLists(@Nullable List<List<String>> one, @Nullable List<List<String>> two){
        if (one == null && two == null){
            return true;
        }

        if(one == null || two == null || one.size() != two.size()){
            return false;
        }
        for (int i = 0; i < one.size(); i++) {
            if (!equalLists(one.get(i), two.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean equalLists(@Nullable List<String> one, @Nullable List<String> two){
        if (one == null && two == null){
            return true;
        }

        if(one == null || two == null || one.size() != two.size()){
            return false;
        }

        //to avoid messing the order of the lists we will use a copy
        one = new ArrayList<>(one);
        two = new ArrayList<>(two);

        Collections.sort(one);
        Collections.sort(two);
        return one.equals(two);
    }

    // in experiment state
    public static class EqualLists<T extends Comparable> {

        public boolean equal(@Nullable List<T> one, @Nullable List<T> two) {
            if (one == null && two == null) {
                return true;
            }

            if (one == null || two == null || one.size() != two.size()) {
                return false;
            }

            //to avoid messing the order of the lists we will use a copy
            one = new ArrayList<>(one);
            two = new ArrayList<>(two);

            Collections.sort(one);
            Collections.sort(two);
            return one.equals(two);
        }
    }
}