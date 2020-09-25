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
package ai.metaheuristic.ai.core;

import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.api.data.FunctionApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.*;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class SystemProcessLauncher {

    private static final String TIMEOUT_MESSAGE =
            "===============================================================\n" +
            "After %d seconds of timeout this process has been destroyed.\n" +
            "===============================================================\n";

    public static class StreamHolder {
        public InputStream is;
    }

    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, File execDir, File consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines) throws IOException, InterruptedException {
        log.info("Exec info:");
        log.info("\tcmd: {}", cmd);
        log.info("\ttaskDir: {}", execDir.getPath());
        log.info("\ttaskDir abs: {}", execDir.getAbsolutePath());
        log.info("\tconsoleLogFile abs: {}", consoleLogFile.getAbsolutePath());
        log.info("\tfunctionCode: {}", functionCode);
        log.info("\ttimeoutBeforeTerminate (seconds): {}", timeoutBeforeTerminate);
        log.info("\tschedule: {}", schedule);

        final AtomicLong timeout = new AtomicLong(0);
        if (timeoutBeforeTerminate!=null && timeoutBeforeTerminate!=0) {
            timeout.set( TimeUnit.SECONDS.toMillis(timeoutBeforeTerminate) );
        }
        log.info("\ttimeout (milliseconds): {}", timeout.get() );


        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(execDir);
        pb.redirectErrorStream(true);
        final Process process = pb.start();

        Thread timeoutThread = null;
        final StreamHolder streamHolder = new StreamHolder();
        int exitCode;

        final StringBuilder timeoutMessage = new StringBuilder();
        final AtomicBoolean isTerminated = new AtomicBoolean(false);
        final AtomicBoolean isInactivePeriod = new AtomicBoolean(false);
        try (final FileOutputStream fos = new FileOutputStream(consoleLogFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            final AtomicBoolean isRun = new AtomicBoolean(false);
            final AtomicBoolean isDone = new AtomicBoolean(false);
            final Thread reader = new Thread(() -> {
                try {
                    log.info("thread #" + Thread.currentThread().getId() + ", start receiving stream from external process");
                    streamHolder.is = process.getInputStream();
                    int c;
                    isRun.set(true);
                    while ((c = streamHolder.is.read()) != -1) {
                        bos.write(c);
                    }
                }
                catch (IOException e) {
                    log.error("Error collect data from output stream", e);
                }
                finally {
                    isDone.set(true);
                }
            });
            reader.start();

            if (timeout.get()>0) {
                final long terminateAt = System.currentTimeMillis() + timeout.get();
                timeoutThread = new Thread(() -> {
                    try {
                        while (!isRun.get()) {
                            log.info("thread #" + Thread.currentThread().getId() + " is waiting for reader thread, time - " + new Date());
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                        }
                        log.info("thread #" + Thread.currentThread().getId() + ", time before sleep - " + new Date());
                        while (true) {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                            if (System.currentTimeMillis() > terminateAt) {
                                break;
                            }
                            // case when SchedulePolicy is strict
                            if (schedule!=null && schedule.isCurrentTimeInactive()) {
                                isInactivePeriod.set(true);
                                break;
                            }
                            // normal termination of the reader thread. We don't need to terminate any application
                            if (isDone.get()) {
                                log.info("A reader thread stopped the execution in a normal way");
                                return;
                            }
                        }
                        log.info("thread #" + Thread.currentThread().getId() + ", time before destroy - " + new Date());

                        final LinkedList<ProcessHandle> handles = new LinkedList<>();
                        collectHandlers(handles, process.toHandle());
                        log.info("Processes to destroy");
                        for (ProcessHandle handle : handles) {
                            log.info("\t{}", handle);
                        }
                        destroy(handles);
                        timeoutMessage.append(String.format(TIMEOUT_MESSAGE, timeoutBeforeTerminate));
                        isTerminated.set(true);
                        log.info("thread #" + Thread.currentThread().getId() + ", time after destroy - " + new Date());
                    } catch (InterruptedException e) {
                        // this is a normal operation so it'll be debug level
                        log.debug("thread #" + Thread.currentThread().getId() + ", current thread was interrupted");
                    }
                });
                timeoutThread.start();
            }

            exitCode = process.waitFor();
            reader.join();
        }
        finally {
            try {
                if (streamHolder.is!=null) {
                    streamHolder.is.close();
                }
            }
            catch(Throwable th) {
                log.warn("Error with closing InputStream", th);
            }
            try {
                if (timeoutThread!=null && timeoutThread.isAlive()) {
                    timeoutThread.interrupt();
                }
            }
            catch(Throwable th) {
                log.warn("Error with interrupting InputStream", th);
            }
        }
        if (isInactivePeriod.get()) {
            throw new ScheduleInactivePeriodException();
        }

        log.info("Any errors of execution? {}", (exitCode == 0 ? "No" : "Yes"));
        log.debug("'\texitCode: {}", exitCode);
        log.debug("'\tdestroyed with timeout: {}", isTerminated.get());
        log.debug("'\tcmd: {}", cmd);
        log.debug("'\texecDir: {}", execDir.getAbsolutePath());
        String console = readLastLines(taskConsoleOutputMaxLines, consoleLogFile) + '\n' + timeoutMessage;

        log.debug("'\tconsole output:\n{}", console);
        return new FunctionApiData.SystemExecResult(functionCode, exitCode==0, exitCode, console);
    }

    public static void collectHandlers(List<ProcessHandle> handles, ProcessHandle handle) {
        handle.children().forEach((child) -> collectHandlers(handles, child));
        handles.add(handle);
    }

    public static void destroy(LinkedList<ProcessHandle> handles) {
        ProcessHandle h;
        while ((h=handles.pollLast())!=null) {
            try {
                log.info("\tstart destroying task with PID #{}, isAlive: {}", h.pid(), h.isAlive());
                boolean status = h.destroyForcibly();
                log.info("\t\tstatus of destroying: {}",status);
            } catch (Throwable th) {
                log.warn("Can't destroy process {}", h.toString());
            }
        }
    }

    private static String readLastLines(int maxSize, File consoleLogFile) throws IOException {
        LinkedList<String> lines = new LinkedList<>();
        String inputLine;
        try(FileReader fileReader = new FileReader(consoleLogFile); BufferedReader in = new BufferedReader(fileReader) ) {
            while ((inputLine = in.readLine()) != null) {
                inputLine = inputLine.trim();
                if (lines.size()==maxSize) {
                    lines.removeFirst();
                }
                lines.add(inputLine);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }


}
