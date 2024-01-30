/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.standalone;

import ai.metaheuristic.commons.utils.ErrorUtils;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Sergio Lissner
 * Date: 9/18/2023
 * Time: 5:59 AM
 */
public class StatusFileUtils {

    public record DispatcherStatus(String stage, String status, @Nullable String error) {}
    private static final ReentrantLock LOCK = new ReentrantLock();

    @SneakyThrows
    public static void appendStart(Path metaheuristicStatusFilePath, String stage) {
        appendStatus(metaheuristicStatusFilePath, new DispatcherStatus(stage, "start", null));
    }

    @SneakyThrows
    public static void appendDone(Path metaheuristicStatusFilePath, String stage) {
        appendStatus(metaheuristicStatusFilePath, new DispatcherStatus(stage, "done", null));
    }

    @SneakyThrows
    public static void appendError(Path metaheuristicStatusFilePath, Throwable th) {
        String error = ErrorUtils.getStackTrace(th, 20, null);
        appendStatus(metaheuristicStatusFilePath, new DispatcherStatus("unknown", "error", error));
    }

    @SneakyThrows
    public static void appendStatus(Path metaheuristicStatusFilePath, DispatcherStatus status) {

        LOCK.lock();
        try {
            String json = JsonUtils.getMapper().writeValueAsString(status);
            try (BufferedWriter writer = Files.newBufferedWriter(metaheuristicStatusFilePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.SYNC)) {
                writer.write(json);
                writer.newLine();
            }
        }
        finally {
            LOCK.unlock();
        }
    }
}
