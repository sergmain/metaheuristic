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

package ai.metaheuristic.ai.processor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 10:59 PM
 */
public class ProcessorAndCoreData {

    public interface CommonUrl {}

    @Data
    @EqualsAndHashCode(of="url")
    public static class AssetServerUrl implements CommonUrl {
        public final String url;

        public AssetServerUrl(String url) {
            if (StringUtils.isBlank(url)) {
                throw new IllegalStateException("#819.020 assetUrl is null");
            }
            this.url = url;
        }
    }

    @Data
    @EqualsAndHashCode(of="url")
    public static class DispatcherServerUrl implements CommonUrl {
        public final String url;

        public DispatcherServerUrl(String url) {
            if (StringUtils.isBlank(url)) {
                throw new IllegalStateException("#819.040 dispatcherUrl is null");
            }
            this.url = url;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ServerUrls {
        public final DispatcherServerUrl dispatcherUrl;
        public final AssetServerUrl assetUrl;
    }

}
