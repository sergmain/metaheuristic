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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Serge
 * Date: 12/29/2020
 * Time: 10:59 PM
 */
public class ProcessorAndCoreData {

    public interface CommonUrl {
        String getUrl();
    }

    @Data
    @EqualsAndHashCode(of="url")
    public static class AssetManagerUrl implements CommonUrl {
        public final String url;

        public AssetManagerUrl(String url) {
            if (S.b(url)) {
                throw new IllegalStateException("#819.020 assetManagerUrl is null");
            }
            this.url = url;
        }
    }

    @Data
    @EqualsAndHashCode(of="url")
    public static class DispatcherUrl implements CommonUrl {
        public final String url;

        public DispatcherUrl(String url) {
            if (S.b(url)) {
                throw new IllegalStateException("#819.040 dispatcherUrl is null");
            }
            this.url = url;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ServerUrls {
        public final DispatcherUrl dispatcherUrl;
        public final AssetManagerUrl assetManagerUrl;
    }

}
