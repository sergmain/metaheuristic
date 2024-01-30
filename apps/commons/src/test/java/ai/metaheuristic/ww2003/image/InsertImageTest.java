/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ww2003.CreateWW2003Document;
import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.WW2003DocumentUtils;
import ai.metaheuristic.ww2003.document.WW2003Parser;
import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 5/30/2023
 * Time: 11:16 PM
 */
public class InsertImageTest {

    @SuppressWarnings("unused")
    @Test
    public void test_insertImage() throws IOException {

        WW2003Document document = CreateWW2003Document.createWW2003Document();
        Sect sect = document.findBody().flatMap(body -> body.findFirst(Sect.class)).orElseThrow(()->new DocumentProcessingException("048.140 a13"));


        final Run run = Run.t("This is an orange");
        Para p = new Para(run);
        p.setShadow(true);
        WW2003PropertyUtils.addVanishRProp(run);
        WW2003PropertyUtils.addVanishRProp(p);

        sect.add(p);
        sect.add(new Para(Run.t("This is an orange")));

        byte[] bytes = IOUtils.resourceToByteArray("/image/orange.png");
        String base64 = Base64.encodeBase64String(bytes);

        final Para para = ImageConverterUtils.getParaForImage(base64, 1);
        sect.add(para);

        Path path = Files.createTempFile(SystemUtils.getJavaIoTmpDir().toPath(), "ww2003-", ".xml");
        System.out.println("path: " + path.toAbsolutePath());
        WW2003DocumentUtils.writeWW2003Document(path, document);

        try (InputStream is = Files.newInputStream(path)) {
            WW2003Document doc1 = WW2003Parser.parse(is);
        }
    }

}
