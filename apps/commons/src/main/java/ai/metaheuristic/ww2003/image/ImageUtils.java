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

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * @author Serge
 * Date: 11/8/2019
 * Time: 7:07 PM
 */
@Slf4j
public class ImageUtils {

    public static ImageData.ImageFullInfo getImageInfo(File workingDir, String imageMagicExec, File inputFile) throws IOException, InterruptedException {
        File consoleLogFile = File.createTempFile("image-converter-console-", ".log", workingDir);

        ImageData.ImageFullInfo[] fullInfo = new ImageData.ImageFullInfo[]{new ImageData.ImageFullInfo()};
        return fullInfo[0];
    }

    public static ImageEnums.Type getType(InputStream is) throws IOException {
        ImageReader reader = getImageReader(is);
        return reader!=null ? ImageEnums.Type.to(reader.getFormatName()) :  ImageEnums.Type.UNKNOWN;
    }

    @Nullable
    public static String getTypeAsStr(InputStream is) throws IOException {
        ImageReader reader = getImageReader(is);
        return reader!=null ? reader.getFormatName() : null;
    }

    @Nullable
    public static ImageReader getImageReader(InputStream is) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(is);

        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

        ImageReader reader=null;
        if (imageReaders.hasNext()) {
            reader = imageReaders.next();
        }
        return reader;
    }

}
