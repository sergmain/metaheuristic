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
package ai.metaheuristic.commons.system;

import ai.metaheuristic.commons.exceptions.ScheduleInactivePeriodException;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.commons.dispatcher_schedule.ExtendedTimePeriod;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
public class SystemProcessLauncher {

    private static final String TIMEOUT_MESSAGE = """
            ===============================================================
            After %d seconds of timeout this process has been destroyed.
            ===============================================================
            """;

    @ToString
    public static class ExecResult {
        @Nullable
        public final Path functionDir;
        public final FunctionApiData.@Nullable SystemExecResult systemExecResult;
        public final boolean ok;
        @Nullable
        public final String error;

        public ExecResult(FunctionApiData.@Nullable SystemExecResult systemExecResult, boolean ok, String error) {
            this(null, systemExecResult, ok, error);
        }

        public ExecResult(@Nullable Path functionDir, FunctionApiData.@Nullable SystemExecResult systemExecResult, boolean ok, @Nullable String error) {
            this.functionDir = functionDir;
            this.systemExecResult = systemExecResult;
            this.ok = ok;
            this.error = error;
        }
    }

    public static ExecResult execCmd(List<String> commands, long timeout, int taskConsoleOutputMaxLines) {
        Path gitTemp = DirUtils.createMhTempPath("command-exec-");
        if (gitTemp==null) {
            return new ExecResult(null, false, "027.017 Error: can't create temporary directory");
        }
        Path consoleLogFile;
        try {
            consoleLogFile = Files.createTempFile(gitTemp, "console-", ".log");
            FunctionApiData.SystemExecResult systemExecResult = execCommand(
                    commands, Path.of("."), consoleLogFile, timeout, "command-exec", null,
                    taskConsoleOutputMaxLines);
            log.info("systemExecResult: {}" , systemExecResult);
            return new ExecResult(systemExecResult, systemExecResult.isOk, systemExecResult.console);
        } catch (InterruptedException | IOException e) {
            log.error("027.020 Error", e);
            return new ExecResult(null, false, "027.020 Error: " + e.getMessage());
        }
        finally {
            DirUtils.deletePathAsync(gitTemp);
        }
    }

    public static class StreamHolder {
        public InputStream is;
    }

    /**
     * Optional out-of-band handoff hook. When passed to {@link #execCommand},
     * the launcher starts the process and concurrently runs {@code handoff}
     * on a virtual thread. The Function discovers HOW to participate in the
     * handoff (e.g. the loopback port + the per-launch check-code) by reading
     * its params file — the launcher itself does not mutate {@code cmd},
     * which is a hard contract (cmd's last positional arg is the params-file
     * path).
     *
     * <p>This module ({@code commons}) is deliberately ignorant of the
     * secret payload — it knows only the {@link Runnable} to invoke. Single
     * ownership of secret bytes stays on the caller in the upper module.
     *
     * <p>{@code debugPort} is for diagnostic logging only; the launcher does
     * NOT use it to mutate the command line.
     */
    public record SecretHandoff(int debugPort, Runnable handoff) {}


    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, Path execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines) throws IOException, InterruptedException {
        return execCommand(cmd, execDir, consoleLogFile, timeoutBeforeTerminate, functionCode, schedule, taskConsoleOutputMaxLines, List.of(), null, null);
    }

    /**
     * Stage 6 overload. When {@code secretHandoff != null}, appends
     * {@code --mh-secret-port=N} to {@code cmd} and concurrently runs the
     * loopback handoff with the spawned process. Caller owns and zeroes
     * {@code secretHandoff.keyBytes()}.
     */
    public static FunctionApiData.SystemExecResult execCommandWithSecret(
            List<String> cmd, Path execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines,
            List<Supplier<Boolean>> outerInterrupters, @Nullable Path inputPath,
            @Nullable SecretHandoff secretHandoff) throws IOException, InterruptedException {
        return execCommand(cmd, execDir, consoleLogFile, timeoutBeforeTerminate, functionCode, schedule,
                taskConsoleOutputMaxLines, outerInterrupters, inputPath, secretHandoff);
    }

    @SuppressWarnings({"WeakerAccess", "BusyWait"})
    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, Path execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines,
            List<Supplier<Boolean>> outerInterrupters, @Nullable Path inputPath) throws IOException, InterruptedException {
        return execCommand(cmd, execDir, consoleLogFile, timeoutBeforeTerminate, functionCode, schedule,
                taskConsoleOutputMaxLines, outerInterrupters, inputPath, null);
    }

    @SuppressWarnings({"WeakerAccess", "BusyWait"})
    public static FunctionApiData.SystemExecResult execCommand(
            List<String> cmd, Path execDir, Path consoleLogFile, @Nullable Long timeoutBeforeTerminate, String functionCode,
            @Nullable final DispatcherSchedule schedule, int taskConsoleOutputMaxLines,
            List<Supplier<Boolean>> outerInterrupters, @Nullable Path inputPath,
            @Nullable SecretHandoff secretHandoff) throws IOException, InterruptedException {
        log.info("Exec info:");
        log.info("\tcmd: {}", cmd);
        log.info("\ttaskDir: {}", execDir.toAbsolutePath());
        log.info("\ttaskDir abs: {}", execDir.toAbsolutePath());
        log.info("\tconsoleLogFile abs: {}", consoleLogFile.normalize());
        log.info("\tfunctionCode: {}", functionCode);
        log.info("\ttimeoutBeforeTerminate (seconds): {}", timeoutBeforeTerminate);
        log.info("\tschedule: {}", schedule);

        final AtomicLong timeout = new AtomicLong(0);
        if (timeoutBeforeTerminate!=null && timeoutBeforeTerminate!=0) {
            timeout.set( TimeUnit.SECONDS.toMillis(timeoutBeforeTerminate) );
        }
        log.info("\ttimeout (milliseconds): {}", timeout.get() );


        // Stage 6: the secret port + check-code are delivered to the Function
        // via TaskFileParamsYaml (the params file the Function already reads
        // at startup) — NOT via cmdline. The hidden contract for cmd is that
        // the last positional argument is the params-file path; adding flags
        // anywhere in cmd would break Functions that count arguments
        // positionally. cmd is passed through unchanged.
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(execDir.toFile());
        pb.redirectErrorStream(true);
        if (inputPath!=null) {
            pb.redirectInput(inputPath.toFile());
        }
        final Process process = pb.start();

        // Stage 6: run the secret handoff concurrently with the spawned
        // process. The handoff blocks on accept() until the Function connects;
        // we keep it on a vthread so the stdout reader (started below) can
        // run in parallel and avoid any chance of deadlock if a chatty
        // Function writes stdout before reading the secret.
        final java.util.concurrent.atomic.AtomicReference<Throwable> handoffErr = new java.util.concurrent.atomic.AtomicReference<>();
        Thread handoffThread = null;
        if (secretHandoff != null) {
            final SecretHandoff sh = secretHandoff;
            handoffThread = Thread.ofVirtual().start(() -> {
                try {
                    sh.handoff().run();
                } catch (Throwable t) {
                    handoffErr.set(t);
                    // Tear down the process — the secret never reached it
                    // (or worse, was sent to a wrong peer). Either way the
                    // spawned process is in an invalid state.
                    try {
                        process.destroyForcibly();
                    } catch (Throwable ignored) {
                        // best effort
                    }
                }
            });
        }

        Thread timeoutThread = null;
        final StreamHolder streamHolder = new StreamHolder();
        int exitCode;

        final StringBuilder timeoutMessage = new StringBuilder();
        final AtomicBoolean isTerminated = new AtomicBoolean(false);
        final AtomicBoolean isInactivePeriod = new AtomicBoolean(false);
        try (final OutputStream fos = Files.newOutputStream(consoleLogFile); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            final AtomicBoolean isRun = new AtomicBoolean(false);
            final AtomicBoolean isDone = new AtomicBoolean(false);
            final Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    log.info("thread #{}, start receiving stream from external process", Thread.currentThread().threadId());
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

                timeoutThread = Thread.ofVirtual().start(() -> {
                    try {
                        while (!isRun.get()) {
                            log.info("thread #{} is waiting for reader thread, time - {}", Thread.currentThread().threadId(), new Date());
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                        }
                        log.info("thread #{}, time before sleep - {}", Thread.currentThread().threadId(), new Date());
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
                        log.info("thread #{}, time before destroy - {}", Thread.currentThread().threadId(), new Date());

                        final LinkedList<ProcessHandle> handles = new LinkedList<>();
                        collectHandlers(handles, process.toHandle());
                        log.info("Processes to destroy");
                        for (ProcessHandle handle : handles) {
                            log.info("\t{}", handle);
                        }
                        destroy(handles);
                        timeoutMessage.append(String.format(TIMEOUT_MESSAGE, timeoutBeforeTerminate));
                        isTerminated.set(true);
                        log.info("thread #{}, time after destroy - {}",  Thread.currentThread().threadId(), new Date());
                    } catch (InterruptedException e) {
                        // this is a normal operation so it'll be debug level
                        log.debug("thread #{}, current thread was interrupted", Thread.currentThread().threadId());
                    }
                });
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
        log.debug("'\texecDir: {}", execDir.toAbsolutePath());
        String console = readLastLines(taskConsoleOutputMaxLines, consoleLogFile) + '\n' + timeoutMessage;

        log.debug("'\tconsole output:\n{}", console);
        if (isTerminated.get() && exitCode==0) {
            log.error("!!! FATAL ERROR need to re-write a code");
        }

        // Stage 6: surface a handoff failure as a non-zero SystemExecResult.
        // If the handoff threw (e.g. SecurityException on check-code mismatch
        // or SocketTimeoutException), the process was already destroyForcibly'd
        // by the vthread. Surface the cause to the caller.
        if (handoffThread != null) {
            try {
                handoffThread.join(2_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Throwable t = handoffErr.get();
            if (t != null) {
                String msg = "Stage 6 secret handoff failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
                log.warn(msg);
                return new FunctionApiData.SystemExecResult(functionCode, false, -993, msg + "\n" + console);
            }
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
                log.warn("Can't destroy process {}, error: {}", h.pid(), th.getMessage());
            }
        }
    }

    private static String readLastLines(int maxSize, Path consoleLogFile) throws IOException {
        LinkedList<String> lines = new LinkedList<>();
        String inputLine;
        try (InputStream is = Files.newInputStream(consoleLogFile); InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {
            while ((inputLine = br.readLine()) != null) {
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
