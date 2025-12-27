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
import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.*;
import ai.metaheuristic.ww2003.document.persistence.CommonWriter;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.WW2003TagProducerMapperImpl;
import ai.metaheuristic.ww2003.document.tags.TagProducerMapper;
import ai.metaheuristic.ww2003.document.tags.ww2003.AbstractWW2003Tag;
import ai.metaheuristic.ww2003.document.tags.ww2003.DummyNode;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import ai.metaheuristic.ww2003.document.tags.xml.table.Tbl;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.io.Writer;

import static org.apache.commons.text.StringEscapeUtils.ESCAPE_XML10;

/**
 * @author Serge
 * Date: 6/5/2021
 * Time: 1:20 PM
 */
@Getter
public class WW2003WritersImpl implements CommonWriter {

    public static final TagProducerMapper TAG_PRODUCER_MAPPER = new WW2003TagProducerMapperImpl();

    public static WW2003WritersImpl INSTANCE = new WW2003WritersImpl();

    @SneakyThrows
    @Override
    public void write(Context context, CDNode node, Writer writer) {
        if (node instanceof Subject subject) {
            WW2003WritersImpl.writeSubject(subject, writer);
        }
        else if (node instanceof Text text) {
            WW2003WritersImpl.writeText(text, writer);
        }
        else if (node instanceof Title title) {
            WW2003WritersImpl.writeTitle(title, writer);
        }
        else if (node instanceof Para para) {
            this.writePara(context, para, writer);
        }
        else if (node instanceof BinData binData) {
            writeBinData(binData, writer);
        }
        else if (node instanceof Company company) {
            WW2003WritersImpl.writeCompany(company, writer);
        }
        else if (node instanceof WW2003Document ww2003Document) {
            this.writeWW2003Document(ww2003Document, writer);
        }
        else if (node instanceof Description description) {
            WW2003WritersImpl.writeDescription(description, writer);
        }
        else if (node instanceof Leaf leaf) {
            writeLeaf(leaf, writer);
        }
        else if (node instanceof AbstractWW2003Tag abstractWW2003Tag) {
            WW2003AbstractWW2003TagWriter.writeAbstractWW2003Tag(context, abstractWW2003Tag, writer, this);
        }
        else if (node instanceof Composite composite) {
            // Tbl here
            this.writeComposite(context, composite, writer);
        }
        else if (node instanceof TextContainer textContainer) {
            writeTextContainer(textContainer, writer);
        }
        else {
            throw new NotImplementedException("not implemented, class: " + node.getClass().getSimpleName());
        }
    }

    @SneakyThrows
    public static void writeAttrs(AbstractCDNode abstractCDNode, Writer writer) {
        if (abstractCDNode.attributes == null) {
            return;
        }
        for (Attr attr : abstractCDNode.attributes) {
            writer.write(' ');
            if (StringUtils.isNotEmpty(attr.getNameSpace())) {
                writer.write(attr.getNameSpace());
                writer.write(':');
            }
            writer.write(attr.getName());
            writer.write("=\"");
            ESCAPE_XML10.translate(attr.value, writer);
            writer.write('\"');
        }
    }

    public void writeComposite(Context context, CDNode cdNode, Writer writer) {
        writeCompositePrepare(context, cdNode, writer);
    }

    private void writeCompositePrepare(Context context, CDNode cdNode, Writer writer) {
        CDNode n = cdNode;
        if (cdNode instanceof Tbl tbl) {
            Tbl tblClone = tbl.clone();
            n = tblClone;
            tblClone.setParent(tbl.hasParent() ? tbl.getParent() : null);
        }
        writeCompositeIntern(context, n, writer);
    }

    @SneakyThrows
    private void writeCompositeIntern(Context context, CDNode cdNode, Writer writer) {
        final Context ctx = cdNode.hasParent() && cdNode.getParent() instanceof Tbl ? new Context(true) : context;

        if (cdNode instanceof XmlTag xmlTag) {
            String openTag = xmlTag.openTag();
            int len = openTag.length();
            writer.write(openTag, 0, len - 1);
            if (cdNode instanceof Composite composite) {
                WW2003WritersImpl.writeAttrs(composite, writer);
                if (composite.getNodes().size() == 0 && (composite.getProperties() == null || composite.getProperties().size() == 0)) {
                    writer.write("/>");
                }
                else {
                    writer.write(openTag.charAt(len - 1));
                    WW2003WriterUtils.printProperties(ctx, this, writer, composite, false);
                    composite.getNodes().forEach(n -> this.write(ctx, n, writer));
                    writer.write(composite.asXmlTag().closeTag());
                }
            }
        }
        else {
            if (cdNode instanceof Composite composite) {
                for (CDNode node : composite.getNodes()) {
                    ((CommonWriter) this).write(ctx, node, writer);
                }
            }
        }
    }

    @SneakyThrows
    public static void writeBinData(BinData binData, Writer writer) {
        writer.write("<w:binData");
        writeAttrs(binData, writer);
        writer.write(">");
        ESCAPE_XML10.translate(binData.getText(), writer);
        writer.write("</w:binData>");
    }

    @SneakyThrows
    public static void writeCompany(Company company, Writer writer) {
        writer.write("\n<o:Company>");
        ESCAPE_XML10.translate(company.getText(), writer);
        writer.write("</o:Company>");
    }

    @SneakyThrows
    public static void writeSubject(Subject subject, Writer writer) {
        writer.write("\n<o:Subject>");
        ESCAPE_XML10.translate(subject.getText(), writer);
        writer.write("</o:Subject>");
    }

    @SneakyThrows
    public void writeWW2003Document(WW2003Document ww2003Document, Writer writer) {
        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?><?mso-application progid=\"Word.Document\"?>");
        String openTag = ww2003Document.asXmlTag().openTag();
        int len = openTag.length();
        writer.write(openTag.substring(0, len - 1));
        writeAttrs(ww2003Document, writer);
        writer.write(openTag.substring(len - 1));
        Context context = new Context();
        for (CDNode node : ww2003Document.getNodes()) {
            write(context, node, writer);
        }
        writer.write(ww2003Document.asXmlTag().closeTag());
    }

    @SneakyThrows
    public static void writeDescription(Description description, Writer writer) {
        writer.write("\n<o:Description>");
        ESCAPE_XML10.translate(description.getText(), writer);
        writer.write("</o:Description>");
    }

    @SneakyThrows
    public static void writeLeaf(Leaf leaf, Writer writer) {
        String tag = leaf.tag();
        if (!S.b(tag)) {
            int len = tag.length();
            writer.write(tag.substring(0, len - 2));
            writeAttrs(leaf, writer);
            writer.write(tag.substring(len - 2));
        }
    }

    private static void addDarkRedColor(DummyNode dummyNode) {
        dummyNode.addPropertyElement(RProp.class, WW2003PropertyUtils.createDarkRedColor());
    }

    @SneakyThrows
    public void writePara(Context context, Para para, Writer writer)  {
        if (para.hasPrev() || para.hasParent()) {
            writer.write(para.openTag());
        }
        boolean insideSvr = false;
        Integer firstLine = CDNodeUtils.getRealIndent(para);
        WW2003WriterUtils.printPPropProperties(context, this, writer, para, firstLine, insideSvr ? Enums.ShadowColorScheme.vst : Enums.ShadowColorScheme.normal);
        WW2003WriterUtils.printRPropProperties(context, this, writer, para, false);

        para.streamNodes().forEach(node -> write(context, node, writer));
        if (para.hasNext() || para.hasParent()) {
            writer.write(para.closeTag());
        }
    }

    @SneakyThrows
    public static void writeTextContainer(TextContainer textContainer, Writer writer) {
        if (!(textContainer instanceof XmlTag xmlTag)) {
            throw new IllegalStateException("Only implementation of XmlTag is supported rn");
        }
        writer.write("\n<");
        if (xmlTag.getNameSpace()!=null) {
            writer.write(xmlTag.getNameSpace());
            writer.write(':');
        }
        writer.write(xmlTag.getTagName());
        if (xmlTag instanceof AbstractCDNode cdNode) {
            writeAttrs(cdNode, writer);
        }
        writer.write('>');
        if (xmlTag instanceof RawText) {
            writer.write('\n');
            writer.write(textContainer.getText());
        }
        else {
            ESCAPE_XML10.translate(textContainer.getText(), writer);
        }
        writer.write("</");
        if (xmlTag.getNameSpace()!=null) {
            writer.write(xmlTag.getNameSpace());
            writer.write(':');
        }
        writer.write(xmlTag.getTagName());
        writer.write('>');
    }

    @SneakyThrows
    public static void writeText(Text text, Writer writer) {
        writeTextContainer(text, writer);
    }

    @SneakyThrows
    public static void writeTitle(Title title, Writer writer) {
        writer.write("\n<o:Title>");
        ESCAPE_XML10.translate(title.getText(), writer);
        writer.write("</o:Title>");
    }

}
