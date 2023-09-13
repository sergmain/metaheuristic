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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.utils.ErrorUtils;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

@SpringBootApplication
@Slf4j
public class MetaheuristicStatus {

    public static String APP_UUID = Consts.APP_UUID_NONE;
    public static Path metaheuristicStatusFilePath;
    private static final ReentrantLock LOCK = new ReentrantLock();

    @SneakyThrows
    static void initAppStatus(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(Consts.UUID_ARG)) {
                String uuid = arg.substring(Consts.UUID_ARG.length());
                if (!S.b(uuid)) {
                    APP_UUID = uuid;
                }
            }
        }
        Path userHomePath = Path.of(System.getProperty("user.home"));

        Path metaheuristicPath = userHomePath.resolve(Consts.METAHEURISTIC_USERHOME_PATH);
        Files.createDirectories(metaheuristicPath);

        Path electronStatusPath = metaheuristicPath.resolve("status");
        Files.createDirectories(electronStatusPath);

        metaheuristicStatusFilePath = electronStatusPath.resolve("mh-" + APP_UUID + ".status");
        if (Files.exists(metaheuristicStatusFilePath)) {
            try {
                Files.write(metaheuristicStatusFilePath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            } catch (Throwable th) {
                //;
            }
        }

        //noinspection unused
        int i=0;
    }

    @SneakyThrows
    public static void appendStart(String stage) {
        appendStatus(new DispatcherData.DispatcherStatus(stage, "start", null));
    }

    @SneakyThrows
    public static void appendDone(String stage) {
        appendStatus(new DispatcherData.DispatcherStatus(stage, "done", null));
    }

    @SneakyThrows
    public static void appendError(Throwable th) {
        String error = ErrorUtils.getStackTrace(th, 20, null);
        appendStatus(new DispatcherData.DispatcherStatus("unknown", "error", error));
    }

    @SneakyThrows
    public static void appendStatus(DispatcherData.DispatcherStatus status) {

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
