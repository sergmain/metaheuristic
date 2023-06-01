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

import ai.metaheuristic.ww2003.exception.CustomInterruptedException;
import org.springframework.lang.Nullable;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Serge
 * Date: 9/21/2019
 * Time: 6:49 PM
 */
@SuppressWarnings("BusyWait")
public class ThreadUtils {

    public record PatternMatcherResultWithTimeout(@Nullable String s, boolean withTimeout) {}

    // https://stackoverflow.com/a/910798/2672202
    public static class InterruptableCharSequence implements CharSequence {
        CharSequence inner;
        public InterruptableCharSequence(CharSequence inner) {
            this.inner = inner;
        }

        public char charAt(int index) {
            if (Thread.interrupted()) {
                throw new RuntimeException(new InterruptedException());
            }
            return inner.charAt(index);
        }

        public int length() {
            return inner.length();
        }

        public CharSequence subSequence(int start, int end) {
            return new InterruptableCharSequence(inner.subSequence(start, end));
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CustomInterruptedException();
        }
    }

    public static void waitTaskCompleted(ThreadPoolExecutor executor) throws InterruptedException {
        waitTaskCompleted(executor, 20);
    }

    public static void waitTaskCompleted(ThreadPoolExecutor executor, int numberOfPeriods) throws InterruptedException {
        int i = 0;
        while ((executor.getTaskCount() - executor.getCompletedTaskCount()) > 0) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            if (++i % numberOfPeriods == 0) {
                System.out.print("total: " + executor.getTaskCount() + ", completed: " + executor.getCompletedTaskCount());
                final Runtime rt = Runtime.getRuntime();
                System.out.println(", free: " + rt.freeMemory() + ", max: " + rt.maxMemory() + ", total: " + rt.totalMemory());
                i = 0;
            }
        }
    }


}
