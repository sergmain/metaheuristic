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
package ai.metaheuristic.commons.permutation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class Permutation<T> {

    /*
        arr[]  ---> Input Array
        data[] ---> Temporary array to store current combination
        start & end ---> Staring and Ending indexes in arr[]
        index  ---> Current index in data[]
        r ---> Size of a combination to be printed
    */
    private int combinationUtil(ArrayList<T> arr, List<T> data, int start, int end, int index, int r, Function<List<T>, Boolean> acceptor) {
        // Current combination is ready to be printed, print it
        if (index == r) {
            if (data.size()!=r) {
                throw new IllegalStateException("data.length!=r");
            }
            acceptor.apply(new ArrayList<>(data));
            return 1;
        }

        // replace index with all possible elements. The condition
        // "end-i+1 >= r-index" makes sure that including one element
        // at index will make a combination with remaining elements
        // at remaining positions
        int result = 0;
        for (int i = start; i <= end && end - i + 1 >= r - index; i++) {
            data.set(index, arr.get(i));
            result += combinationUtil(arr, data, i + 1, end, index + 1, r, acceptor );
        }
        return result;
    }

    // The main function that prints all combinations of size r
    // in arr[] of size n. This function mainly uses combinationUtil()

    /**
     *
     * @param collection
     * @param r размер искомого массива, должен быть меньше чем n
     * @param acceptor
     * @return
     */
    public int printCombination(Collection<T> collection, int r, Function<List<T>, Boolean> acceptor) {
        final ArrayList<T> list = new ArrayList<>(collection);
        return printCombination(list, r, acceptor);
    }

    public int printCombination(ArrayList<T> arr, int r, Function<List<T>, Boolean> acceptor) {
        int n = arr.size();

        if (n<r) {
            throw new IllegalStateException("n<r");
        }
        // A temporary array to store all combination one by one
        // Print all combination using temprary array 'data[]'

        ArrayList<T> array = new ArrayList<>();
        for (int i = 0; i < r; i++) {
            array.add(null);
        }

        return combinationUtil(arr, array, 0, n - 1, 0, r, acceptor);
    }

    /*Driver function to check for above function*/
    public static void main(String[] args) {
        ArrayList<String> arr = new ArrayList<>(Arrays.asList("+1", "+2", "-3", "-4", "5!"));

        int r = 3;

        Permutation<String> permutation = new Permutation<>();
        System.out.println("\nTotal number of combination: " + permutation.printCombination(arr, r, data ->
                {
                    System.out.println("" + data);
                    return true;
                }
        ));
    }
}