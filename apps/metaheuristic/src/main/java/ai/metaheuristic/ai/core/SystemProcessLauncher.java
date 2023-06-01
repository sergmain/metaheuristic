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
package ai.metaheuristic.ai.core;

import ai.metaheuristic.ai.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.commons.dispatcher_schedule.ExtendedTimePeriod;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class SystemProcessLauncher {

    private static final String TIMEOUT_MESSAGE = """
            ===============================================================
            After %d seconds of timeout this process has been destroyed.
            ===============================================================
            """;

    @ToString
    public static class ExecResult {
        @Nullable
        public final File functionDir;
        @Nullable
        public final FunctionApiData.SystemExecResult systemExecResult;
        public final boolean ok;
        public final String error;

        public ExecResult(@Nullable FunctionApiData.SystemExecResult systemExecResult, boolean ok, String error) {
            this(null, systemExecResult, ok, error);
        }

        public ExecResult(@Nullable File functionDir, @Nullable FunctionApiData.SystemExecResult systemExecResult, boolean ok, String error) {
            this.functionDir = functionDir;
            this.systemExecResult = systemExecResult;
            this.ok = ok;
            this.error = error;
        }
    }

    public static ExecResult execCmd(List<String> commands, long timeout, int taskConsoleOutputMaxLines) {
        Path gitTemp = DirUtils.createMhTempPath("command-exec-");
        if (gitTemp==null) {
            return new ExecResult(null, false, "#027.017 Error: can't create temporary directory");
        }
        Path consoleLogFile;
        try {
            consoleLogFile = Files.createTempFile(gitTemp, "console-", ".log");
            FunctionApiData.SystemExecResult systemExecResult = execCommand(
                    commands, new File("."), consoleLogFile, timeout, "command-exec", null,
                    taskConsoleOutputMaxLines);
            log.info("systemExecResult: {}" , systemExecResult);
            return new ExecResult(systemExecResult, systemExecResult.isOk, systemExecResult.console);
        } catch (InterruptedException | IOException e) {
            log.error("#027.020 Error", e);
            return new ExecResult(null, false, "#027.020 Error: " + e.getMessage());
        }
        finally {
            DirUtils.deletePathAsync(gitTemp);
        }
    }

    public static class StreamHolder {
        public InputStream is;
    }

    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, File execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines) throws IOException, InterruptedException {
        return execCommand(cmd, execDir, consoleLogFile, timeoutBeforeTerminate, functionCode, schedule, taskConsoleOutputMaxLines, List.of());
    }

    @SuppressWarnings({"WeakerAccess", "BusyWait"})
    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, File execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines, List<Supplier<Boolean>> outerInterrupters) throws IOException, InterruptedException {
        log.info("Exec info:");
        log.info("\tcmd: {}", cmd);
        log.info("\ttaskDir: {}", execDir.getPath());
        log.info("\ttaskDir abs: {}", execDir.getAbsolutePath());
        log.info("\tconsoleLogFile abs: {}", consoleLogFile.normalize());
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
        try (final OutputStream fos = Files.newOutputStream(consoleLogFile); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
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

            if (timeout.get()>0 || !outerInterrupters.isEmpty()) {
                final List<Supplier<Boolean>> interrupters = new ArrayList<>();
                if (!outerInterrupters.isEmpty()) {
                    interrupters.addAll(outerInterrupters);
                }
                if (timeout.get()>0) {
                    final long terminateAt = System.currentTimeMillis() + timeout.get();
                    interrupters.add( ()-> System.currentTimeMillis() > terminateAt );
                }
                interrupters.add( () -> {
                    if (schedule!=null && schedule.policy==ExtendedTimePeriod.SchedulePolicy.strict && schedule.isCurrentTimeInactive()) {
                        isInactivePeriod.set(true);
                        return true;
                    }
                    return false;
                });

                timeoutThread = new Thread(() -> {
                    try {
                        while (!isRun.get()) {
                            log.info("thread #" + Thread.currentThread().getId() + " is waiting for reader thread, time - " + new Date());
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                        }
                        log.info("thread #" + Thread.currentThread().getId() + ", time before sleep - " + new Date());
                        while (true) {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                            if (interrupters.stream().anyMatch(Supplier::get)) {
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
                    try {
                        streamHolder.is.close();
                    }
                    catch (IOException e) {
                        //
                    }
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
        log.debug("'\tdestroyed with timeout or for other reason: {}", isTerminated.get());
        log.debug("'\tcmd: {}", cmd);
        log.debug("'\texecDir: {}", execDir.getAbsolutePath());
        String console = readLastLines(taskConsoleOutputMaxLines, consoleLogFile) + '\n' + timeoutMessage;

        log.debug("'\tconsole output:\n{}", console);
        if (isTerminated.get() && exitCode==0) {
            log.error("!!! FATAL ERROR need to re-write a code");
        }
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

    private static String readLastLines(int maxSize, Path consoleLogFile) throws IOException {
        LinkedList<String> lines = new LinkedList<>();
        String inputLine;
        try (BufferedReader in = Files.newBufferedReader(consoleLogFile)) {
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
