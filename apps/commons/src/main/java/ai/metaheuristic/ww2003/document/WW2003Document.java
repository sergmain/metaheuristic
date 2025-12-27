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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.document.tags.xml.*;

import java.io.Closeable;
import java.util.*;

public class WW2003Document extends Composite implements XmlTag, Finalizable {

    static class DocProcessing implements Closeable {
        final Deque<CDNode> stack = new LinkedList<>();

        private DocProcessing(WW2003Document ww2003Document) {
            stack.addLast(ww2003Document);
        }

        @Override
        public void close() {
            stack.clear();
        }
    }

    final DocProcessing processing;

    public WW2003Document(CDNode... nodes) {
        super(nodes);
        ThreadLocalUtils.getInnerStyles().initStyles(getNodes());
        processing = new DocProcessing(this);
    }

    public WW2003Document() {
        ThreadLocalUtils.getInnerStyles().initStyles(getNodes());
        processing = new DocProcessing(this);
    }

    @Override
    public String getNameSpace() {
        return "w";
    }

    @Override
    public String getTagName() {
        return "wordDocument";
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void destroy() {
        super.destroy();
        if (processing != null) {
            processing.close();
        }
    }


    public static List<Attr> createDefaultDocAttrs() {
        ArrayList<Attr> attrs = new ArrayList<>();
        attrs.add(Attr.get("w", "macrosPresent", "no"));
        attrs.add(Attr.get("w", "embeddedObjPresent", "no"));
        attrs.add(Attr.get("w", "ocxPresent", "no"));
        attrs.add(Attr.get("xml", "space", "preserve"));
        attrs.add(Attr.get("xmlns", "w", "http://schemas.microsoft.com/office/word/2003/wordml"));
        attrs.add(Attr.get("xmlns", "v", "urn:schemas-microsoft-com:vml"));
        attrs.add(Attr.get("xmlns", "w10", "urn:schemas-microsoft-com:office:word"));
        attrs.add(Attr.get("xmlns", "sl", "http://schemas.microsoft.com/schemaLibrary/2003/core"));
        attrs.add(Attr.get("xmlns", "aml", "http://schemas.microsoft.com/aml/2001/core"));
        attrs.add(Attr.get("xmlns", "wx", "http://schemas.microsoft.com/office/word/2003/auxHint"));
        attrs.add(Attr.get("xmlns", "o", "urn:schemas-microsoft-com:office:office"));
        attrs.add(Attr.get("xmlns", "dt", "uuid:C2F41010-65B3-11d1-A29F-00AA00C14882"));
        return attrs;
    }

    public static DocumentProperties createDefaultDocumentProperties() {
        DocumentProperties documentProperties = new DocumentProperties();
        UnIdentifiedTextNode author = new UnIdentifiedTextNode("o", "Author");
        author.setText("ConsultantPlus");
        documentProperties.add(author);
        return documentProperties;
    }

    public static Fonts createDefaultFonts() {
        UnIdentifiedNode defaultFonts = new UnIdentifiedNode("w", "defaultFonts");
        defaultFonts.addAttribute(Attr.get("w", "ascii", "Courier New"));
        defaultFonts.addAttribute(Attr.get("w", "h-ansi", "Courier New"));
        defaultFonts.addAttribute(Attr.get("w", "cs", "Courier New"));
        return new Fonts(defaultFonts);
    }

    public static DocPr createDefaultDocPr() {
        DocPr docPr = new DocPr();
        UnIdentifiedNode view = new UnIdentifiedNode("w", "view");
        view.addAttribute(Attr.get("w", "val", "normal"));
        docPr.add(view);
        UnIdentifiedNode zoom = new UnIdentifiedNode("w", "zoom");
        zoom.addAttribute(Attr.get("w", "percent", "100"));
        docPr.add(zoom);
        docPr.add(new UnIdentifiedNode("w", "doNotEmbedSystemFonts"));
        UnIdentifiedNode attachedTemplate = new UnIdentifiedNode("w", "attachedTemplate");
        attachedTemplate.addAttribute(Attr.get("w", "val", ""));
        docPr.add(attachedTemplate);
        UnIdentifiedNode defaultTabStop = new UnIdentifiedNode("w", "defaultTabStop");
        defaultTabStop.addAttribute(Attr.get("w", "val", "720"));
        docPr.add(defaultTabStop);
        UnIdentifiedNode validateAgainstSchema = new UnIdentifiedNode("w", "validateAgainstSchema");
        validateAgainstSchema.addAttribute(Attr.get("w", "val", "off"));
        docPr.add(validateAgainstSchema);
        docPr.add(new UnIdentifiedNode("w", "saveInvalidXML"));
        docPr.add(new UnIdentifiedNode("w", "ignoreMixedContent"));
        UnIdentifiedNode alwaysShowPlaceholderText = new UnIdentifiedNode("w", "alwaysShowPlaceholderText");
        alwaysShowPlaceholderText.addAttribute(Attr.get("w", "val", "off"));
        docPr.add(alwaysShowPlaceholderText);
        UnIdentifiedNode compat = new UnIdentifiedNode("w", "compat");
        compat.add(new UnIdentifiedNode("w", "dontAllowFieldEndSelect"));
        docPr.add(compat);
        return docPr;
    }

    public static SectProp createDefaultSectProp() {
        SectProp sectProp = new SectProp();
        UnIdentifiedNode pgSz = new UnIdentifiedNode("w", "pgSz");
        pgSz.addAttribute(Attr.get("w", "w", "31678"));
        pgSz.addAttribute(Attr.get("w", "h", "16840"));
        sectProp.add(pgSz);
        UnIdentifiedNode pgMar = new UnIdentifiedNode("w", "pgMar");
        pgMar.addAttribute(Attr.get("w", "top", "1417"));
        pgMar.addAttribute(Attr.get("w", "right", "1701"));
        pgMar.addAttribute(Attr.get("w", "bottom", "1417"));
        pgMar.addAttribute(Attr.get("w", "left", "851"));
        sectProp.add(pgMar);
        return sectProp;
    }

    public Optional<Body> findBody() {
        for (CDNode cdNode : getNodes()) {
            if (cdNode instanceof Body body) {
                return Optional.of(body);
            }
        }
        return Optional.empty();
    }}
