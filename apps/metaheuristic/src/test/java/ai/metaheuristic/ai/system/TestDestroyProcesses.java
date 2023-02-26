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

package ai.metaheuristic.ai.system;

import ai.metaheuristic.ai.core.SystemProcessLauncher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 5/1/2019
 * Time: 9:01 PM
 */
@Slf4j
public class TestDestroyProcesses {

    @Test
    public void testDestroyProcesses() throws IOException, InterruptedException {

        if (!StringUtils.startsWithIgnoreCase(System.getProperty("os.name"), "Windows")) {
            log.info("this test can't be run on non-window OS");
        }
        File f = new File("config\\exe\\HelloWorldCmd.exe");
        if (!f.exists()) {
            log.info("this test can't run, exe file doesn't exist.");
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(List.of(f.getPath()));
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        final SystemProcessLauncher.StreamHolder streamHolder = new SystemProcessLauncher.StreamHolder();

        final AtomicBoolean isRun = new AtomicBoolean(false);
/*
        final Thread reader = new Thread(() -> {
            try {
                log.info("thread #" + Thread.currentThread().getId() + ", start receiving stream from external process");
                streamHolder.is = process.getInputStream();
                int c;
                isRun.set(true);
                while ((c = streamHolder.is.read()) != -1) {
                    bos.write(c);
                }
            } catch (IOException e) {
                log.error("Error collect data from output stream", e);
            }
        });
        reader.start();
*/

//        int exitCode = process.waitFor();
//        reader.join();

        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        LinkedList<ProcessHandle> handles = new LinkedList<>();
        SystemProcessLauncher.collectHandlers(handles, process.toHandle());
        int numberOfAlive = getNumberOfAlive(handles);
        assertNotEquals(0, numberOfAlive);

        log.info("Number of handlers before destroying: {}", handles.size());
        for (ProcessHandle handle : handles) {
            log.info("\tPID: {}", handle.pid());
        }
        SystemProcessLauncher.destroy(handles);
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        handles.clear();
        SystemProcessLauncher.collectHandlers(handles, process.toHandle());
        log.info("Number of handlers after destroying: {}", handles.size());
        for (ProcessHandle handle : handles) {
            log.info("\tPID: {}", handle.pid());
        }
        numberOfAlive = getNumberOfAlive(handles);
        assertEquals(0, numberOfAlive);
    }

    private static int getNumberOfAlive(LinkedList<ProcessHandle> handles) {
        int count=0;
        for (ProcessHandle handle : handles) {
            if (handle.isAlive()) {
                count++;
            }
        }
        return count;
    }



}
