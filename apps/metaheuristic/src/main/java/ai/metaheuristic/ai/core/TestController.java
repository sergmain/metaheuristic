/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.commons.CommonConsts;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

        private static final List<String> FUNCTIONS = List.of("f-1", "f-2");
        private static final ReplicationData.AssetStateResponse ASSET_STATE_RESPONSE = new ReplicationData.AssetStateResponse();
        static {
            ASSET_STATE_RESPONSE.companies.add(new ReplicationData.CompanyShortAsset(1001L, 101L));
            ASSET_STATE_RESPONSE.companies.add(new ReplicationData.CompanyShortAsset(1002L, 102L));
            ASSET_STATE_RESPONSE.functions.addAll(FUNCTIONS);
            ASSET_STATE_RESPONSE.sourceCodeUids.addAll(List.of("source-code-1", "source-code-2"));
            ASSET_STATE_RESPONSE.usernames.add(new ReplicationData.AccountShortAsset("user-1", 301L));
            ASSET_STATE_RESPONSE.usernames.add(new ReplicationData.AccountShortAsset("user-2", 302L));

            ASSET_STATE_RESPONSE.addInfoMessage("info");
        }

        @PostMapping("/test/rest")
        @ResponseBody
        public String test1(@RequestBody String text) {
            return getString(text);
        }

        @GetMapping("/test/asset-with-error")
        @ResponseBody
        public ReplicationData.AssetStateResponse testAssetWithError() {
            ReplicationData.AssetStateResponse response = new ReplicationData.AssetStateResponse();
            response.addErrorMessage("asset-error");
            return response;
        }

        @GetMapping("/test/asset-with-info")
        @ResponseBody
        public ReplicationData.AssetStateResponse testAssetWithInfo() {
            ReplicationData.AssetStateResponse response = new ReplicationData.AssetStateResponse();
            response.addInfoMessage("asset-info");
            return response;
        }

        @GetMapping("/test/asset-with-info-and-error")
        @ResponseBody
        public ReplicationData.AssetStateResponse testAssetWithInfoAndError() {
            ReplicationData.AssetStateResponse response = new ReplicationData.AssetStateResponse();
            response.addInfoMessage("asset-info");
            response.addErrorMessage("asset-error");
            return response;
        }

        @GetMapping("/test/asset-ok-info")
        @ResponseBody
        public ReplicationData.AssetStateResponse testAssetOk() {
            return ASSET_STATE_RESPONSE;
        }

    }

    private static String getString(@RequestParam(name = "text", required = false, defaultValue = "") String text) {
        return CommonConsts.MULTI_LANG_STRING + "\n" + StringUtils.substring(text, 0, 20);
    }

}
