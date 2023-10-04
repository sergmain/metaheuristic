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

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class MetaheuristicStatus {

    public static String APP_UUID = CommonConsts.APP_UUID_NONE;
    public static Path metaheuristicStatusFilePath = null;

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

        Path metaheuristicPath = userHomePath.resolve(CommonConsts.METAHEURISTIC_USERHOME_PATH);
        Files.createDirectories(metaheuristicPath);

        Path electronStatusPath = metaheuristicPath.resolve("status");
        Files.createDirectories(electronStatusPath);

        metaheuristicStatusFilePath = electronStatusPath.resolve("mh-" + APP_UUID + ".status");

        //noinspection unused
        int i=0;
    }

}
