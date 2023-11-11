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

package ai.metaheuristic.ai.some;

import lombok.SneakyThrows;
import org.springframework.lang.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Sergio Lissner
 * Date: 11/11/2023
 * Time: 2:57 PM
 */
public class CompletableFutureExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        String name = "aaa";
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        
        
        
        CompletableFuture<String> cf1 =  CompletableFuture.supplyAsync(() -> getString("aaa", 5000), executor).handle((s, t) -> s != null ? s : "Hello, Stranger!");
        cf1.join();
        CompletableFuture<String> cf2 =  CompletableFuture.supplyAsync(() -> getString("bbb", 2000), executor).handle((s, t) -> s != null ? s : "Hello, Stranger!");
        cf2.join();

        long mills = System.currentTimeMillis();
        CompletableFuture.allOf(cf1, cf2).join();
        System.out.println("Mills: " +(System.currentTimeMillis() - mills));

        mills = System.currentTimeMillis();
        System.out.println(cf1.get());
        System.out.println("Mills #1: " +(System.currentTimeMillis() - mills));

        mills = System.currentTimeMillis();
        System.out.println(cf2.get());
        System.out.println("Mills #2: " +(System.currentTimeMillis() - mills));
    }

    @SneakyThrows
    @NonNull
    private static String getString(String name, long sleep) {
        long mills = System.currentTimeMillis();
        Thread.sleep(sleep);
        System.out.println("Thread is virtual: " + Thread.currentThread().isVirtual());
        long endMills = System.currentTimeMillis();
        System.out.println("Sleep:"+ sleep+", exec: " +(endMills - mills));
        return "Hello, " + name;
    }
}
