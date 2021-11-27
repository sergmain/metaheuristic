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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TestProcessTimeout {

    @Test
    public void testProcessTimeout() throws IOException, InterruptedException {

        File tempDir = DirUtils.createMhTempDir("test-process-timeout-");

        List<String> cmd = List.of("jshell");
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(tempDir);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final SystemProcessLauncher.StreamHolder streamHolder = new SystemProcessLauncher.StreamHolder();
        int exitCode;
        long timeout = TimeUnit.SECONDS.toMillis(15);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            final AtomicBoolean isRunned = new AtomicBoolean(false);
            final Thread reader = new Thread(() -> {
                try {
                    System.out.println("thread #"+Thread.currentThread().getId()+", time - " + new Date());
                    streamHolder.is = process.getInputStream();
                    int c;
                    isRunned.set(true);
                    while ((c = streamHolder.is.read()) != -1) {
                        baos.write(c);
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException();
                        }
                    }
                }
                catch (InterruptedException | IOException e) {
                    System.out.println("thread #"+Thread.currentThread().getId()+", current thread was interrupted");
                }
            });
            reader.start();

            final Thread timeoutThread = new Thread(() -> {
                try {
                    while (!isRunned.get()) {
                        System.out.println("thread #"+Thread.currentThread().getId()+", time - " + new Date());
                        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(200));
                    }
                    System.out.println("thread #"+Thread.currentThread().getId()+", time before sleep - " + new Date());
                    Thread.sleep(timeout);
                    System.out.println("thread #"+Thread.currentThread().getId()+", time before destroy - " + new Date());
                    process.destroy();
                    System.out.println("thread #"+Thread.currentThread().getId()+", time after destroy - " + new Date());
                }
                catch (InterruptedException e) {
                    System.out.println("thread #"+Thread.currentThread().getId()+", current thread was interrupted");
                }
            });
            timeoutThread.start();

            exitCode = process.waitFor();
            reader.join();
        }
        finally {
            if (streamHolder.is!=null) {
                streamHolder.is.close();
            }
        }
        System.out.println("exitCode: " + exitCode);
        System.out.println("exit value: " + process.exitValue());
        System.out.println("isAlive: " + process.isAlive());
        System.out.println("info: " + process.info());
        System.out.println("console:\n" + baos.toString());

        // TODO 2021.02.02 add here some actual checks
    }
}
