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

import ai.metaheuristic.commons.CommonConsts;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * @author Serge
 * Date: 1/20/2020
 * Time: 3:34 PM
 */
public class TestController {

    @Controller
    @Profile("dispatcher")
    public static class Simple {

        @GetMapping("/test/simple")
        @ResponseBody
        public String test(@RequestParam(name = "text", required = false, defaultValue = "") String text) {
            return getString(text);
        }

        @GetMapping("/test/simple-with-id/id-{id}")
        @ResponseBody
        public String test(@PathVariable Long id, Long value) {
            return getString(id.toString() + " " + value);
        }
    }

    @RestController
    @Profile("dispatcher")
    public static class Rest {

        @PostMapping("/test/rest")
        @ResponseBody
        public String test1(@RequestBody String text) {
            return getString(text);
        }

    }

    private static String getString(@RequestParam(name = "text", required = false, defaultValue = "") String text) {
        return CommonConsts.MULTI_LANG_STRING + "\n" + StringUtils.substring(text, 0, 20);
    }

}
