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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Serge
 * Date: 2/12/2022
 * Time: 11:56 AM
 */
@RestController
@RequestMapping("/rest/v1/test")
@Profile("dispatcher")
public class SimpleRestController {

    @PostMapping("/null")
    public String uploadToNull(@Nullable MultipartFile file, @Nullable Long length) {
        if (file==null) {
            return "ERROR. file wasn't specified";
        }
        if (length==null) {
            return "ERROR. length wasn't specified";
        }
        if (file.getSize()!=length) {
            return "ERROR. file size: " + file.getSize() + ", expected: " + length;
        }
        return "OK";
    }

}
