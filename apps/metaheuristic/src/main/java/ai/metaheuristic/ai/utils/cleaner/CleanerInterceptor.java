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

package ai.metaheuristic.ai.utils.cleaner;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 5:17 PM
 */
@Slf4j
public class CleanerInterceptor implements AsyncHandlerInterceptor {

    @SuppressWarnings("unchecked")
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        List<Path> toClean = (List<Path>) request.getAttribute(Consts.RESOURCES_TO_CLEAN);
        if (toClean!=null && !toClean.isEmpty()) {
            DirUtils.deletePaths(toClean);
        }
    }

}
