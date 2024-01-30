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

package ai.metaheuristic.status_file;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ErrorUtils;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.standalone.StatusFileUtils;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Sergio Lissner
 * Date: 9/18/2023
 * Time: 4:47 AM
 */
@RestController
public class SimpleRestController {

    @Nullable
    private Path metaheuristicStatusFilePath = null;
    @Nullable
    private String error = null;

    @SneakyThrows
    @PostConstruct
    public void inti() {

        try {
            Path userHomePath = Path.of(System.getProperty("user.home"));

            Path metaheuristicPath = userHomePath.resolve(CommonConsts.METAHEURISTIC_USERHOME_PATH);
            Files.createDirectories(metaheuristicPath);

            Path electronStatusPath = metaheuristicPath.resolve("status");
            Files.createDirectories(electronStatusPath);

            metaheuristicStatusFilePath = electronStatusPath.resolve("mh-none.status");
        } catch (IOException e) {
            String trace = ErrorUtils.getStackTrace(e, 20, null);
            StatusFileUtils.DispatcherStatus ds = new StatusFileUtils.DispatcherStatus("unknown", "error", trace);
            String json = JsonUtils.getMapper().writeValueAsString(ds);
            error = json;
        }
    }

    @SneakyThrows
    @GetMapping("/status")
    @ResponseBody
    public String status() {
        if (!S.b(error)) {
            return error;
        }

        String status = Files.readString(Objects.requireNonNull(metaheuristicStatusFilePath), StandardCharsets.UTF_8);
        return status;
    }
}
