/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.resource;

import ai.metaheuristic.ai.Consts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 5:17 PM
 */
@Slf4j
public class ResourceCleanerInterceptor extends HandlerInterceptorAdapter {

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
        List<File> toClean = (List<File>) request.getAttribute(Consts.RESOURCES_TO_CLEAN);
        if (toClean!=null) {
            try {
                for (File file : toClean) {
                    if (file.isFile()) {
                        file.delete();
                    }
                    else {
                        FileUtils.deleteDirectory(file);
                    }
                }
            } catch (IOException e) {
                log.error("Error while cleaning resourses", e);
            }
        }
    }
}
