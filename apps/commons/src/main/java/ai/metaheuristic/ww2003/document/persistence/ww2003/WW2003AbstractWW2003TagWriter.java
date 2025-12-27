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

package ai.metaheuristic.ww2003.document.persistence.ww2003;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.ww2003.document.CDNodeUtils;
import ai.metaheuristic.ww2003.document.persistence.CommonWriter;
import ai.metaheuristic.ww2003.document.tags.WW2003TagProducerMapperImpl;
import ai.metaheuristic.ww2003.document.tags.TagProducer;
import ai.metaheuristic.ww2003.document.tags.Vanishable;
import ai.metaheuristic.ww2003.document.tags.ww2003.AbstractWW2003Tag;

import java.io.IOException;
import java.io.Writer;

import static org.apache.commons.text.StringEscapeUtils.ESCAPE_XML10;

/**
 * @author Serge
 * Date: 5/9/2022
 * Time: 1:05 PM
 */
public class WW2003AbstractWW2003TagWriter {

    public static boolean needOpenPara(AbstractWW2003Tag tag) {
        return WW2003WriterUtils.needOpenPara(tag);
    }

    public static void writeAbstractWW2003Tag(CommonWriter.Context context, AbstractWW2003Tag abstractWW2003Tag, Writer writer, WW2003WritersImpl ww2003Writers) throws IOException {
        WW2003TagProducerMapperImpl tagProducerMapper = new WW2003TagProducerMapperImpl();
        TagProducer tagProducer = tagProducerMapper.map(abstractWW2003Tag.getClass().getName());

        if (needOpenPara(abstractWW2003Tag)) {
            writer.write("<w:p>");
            Integer firstLine = CDNodeUtils.getRealIndent(abstractWW2003Tag);
            WW2003WriterUtils.printPPropProperties(context, ww2003Writers, writer, abstractWW2003Tag, firstLine);
        }
        writer.write("<w:r>");
        WW2003WriterUtils.printRPropProperties(context, ww2003Writers, writer, abstractWW2003Tag, true);
        writer.write("\n<w:t>");
        final String openTag = tagProducer.openTag.apply(abstractWW2003Tag);
        if (openTag != null) {
            ESCAPE_XML10.translate(openTag, writer);
        }
        writer.write("</w:t></w:r>");
        abstractWW2003Tag.streamNodes().forEach(node1 -> ww2003Writers.write(context, node1, writer));
        String consCloseTag = tagProducer.closeTag.apply(abstractWW2003Tag);
        if (!S.b(consCloseTag)) {
            writer.write("<w:r>");
            WW2003WriterUtils.printRPropProperties(context, ww2003Writers, writer, abstractWW2003Tag, abstractWW2003Tag instanceof Vanishable);
            writer.write("\n<w:t>");
            ESCAPE_XML10.translate(consCloseTag, writer);
            writer.write("</w:t></w:r>");
        }
        if (WW2003WriterUtils.needClosePara(abstractWW2003Tag)) {
            writer.write("</w:p>");
        }
    }
}
