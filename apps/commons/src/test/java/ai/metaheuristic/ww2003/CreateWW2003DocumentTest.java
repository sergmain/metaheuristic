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

package ai.metaheuristic.ww2003;

import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.WW2003DocumentUtils;
import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.xml.Para;
import ai.metaheuristic.ww2003.document.tags.xml.Run;
import ai.metaheuristic.ww2003.document.tags.xml.Sect;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 5/27/2023
 * Time: 10:40 PM
 */
public class CreateWW2003DocumentTest {

    @Test
    public void test_() throws IOException {

        WW2003Document document = CreateWW2003Document.createWW2003Document();
        Sect sect = document.findBody().flatMap(body -> body.findFirst(Sect.class)).orElseThrow(()->new DocumentProcessingException("048.140 a13"));


        final Run run = Run.t("Shadowed text");
        Para p = new Para(run);
        p.setShadow(true);
        WW2003PropertyUtils.addVanishRProp(run);
        WW2003PropertyUtils.addVanishRProp(p);

        sect.add(p);
        sect.add(new Para(Run.t("Test text")));

        Path path = Files.createTempFile(SystemUtils.getJavaIoTmpDir().toPath(), "ww2003-", ".xml");
        System.out.println("path: " + path.toAbsolutePath());
        WW2003DocumentUtils.writeWW2003Document(path, document);

    }
}
