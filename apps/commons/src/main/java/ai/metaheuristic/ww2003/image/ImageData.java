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

package ai.metaheuristic.ww2003.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.w3c.dom.Node;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/21/2019
 * Time: 5:55 PM
 */
public class ImageData {

    public enum Dimension { WIDTH, HEIGHT, AS_IS }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Size {
        public int width, height;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintSize {
        public double x, y;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageInfo {
        public int depth;
        public Size geometry;
        public PrintSize printSize;
        public ImageEnums.Units units = ImageEnums.Units.Unknown;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageFullInfo {
        public ImageInfo image;
    }

    @Data
    public static class ImageStyle {
        public Size size;
        public final Map<String, String> rawStyleAttrs = new HashMap<>();
    }

    @Data
    public static class ByteArray {
        public final byte[] bytes;
    }

    /**
     * @author Serge
     * Date: 11/8/2019
     * Time: 6:23 PM
     */
    @Data
    public static class ImageBinaryData {

        public final boolean gzip;
        public final String base64;
        public final byte[] bytes;

        public ImageBinaryData(boolean gzip, @Nonnull String base64, @Nonnull byte[] bytes) {
            this.gzip = gzip;
            this.base64 = base64;
            this.bytes = bytes;
        }
    }

    /**
     * @author Serge
     * Date: 11/19/2019
     * Time: 11:41 PM
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ImageParams {

        public Size size;
        public Dimension dimension = Dimension.AS_IS;
        public boolean gray;
        public int depth = 4;
    }

    @Data
    @ToString(exclude = {"binData", "attrImageData", "attrBinData", "imageBinaryData", "style"})
    public static class ImageProcessingData {
        public final ImageParams imageParams = new ImageParams();
        public final ImageBinaryData imageBinaryData;
        public final ImageData.ImageStyle imageStyle;
        public final Node binData;
        public final Node attrImageData;
        public final Node attrBinData;
        public final String filename;
        public final String ext;
        public final Node style;
        public final boolean vector;

        public ImageData.ImageFullInfo fullInfo;
    }


}
