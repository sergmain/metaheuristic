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

import ai.metaheuristic.ww2003.document.tags.xml.*;
import lombok.Data;
import javax.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 11/20/2019
 * Time: 10:50 AM
 */
@SuppressWarnings("DuplicatedCode")
public class ImageConverterUtils {

    private static boolean replacePictureSize(int dpi, ImageData.ImageProcessingData d) {
        Map<String, String> newAttr = new HashMap<>(d.imageStyle.rawStyleAttrs);

        double resizeFactor = 72.0/dpi;

        newAttr.put("width", String.format(Locale.US,"%.2fpt", resizeFactor * d.imageParams.size.width));
        newAttr.put("height", String.format(Locale.US,"%.2fpt", resizeFactor * d.imageParams.size.height));

        // example - width:152.25pt;height:47.25pt;visibility:visible;mso-wrap-style:square
        String style = newAttr.entrySet().stream().map(e-> ""+e.getKey()+':'+e.getValue()).collect(Collectors.joining(";"));
        d.style.setNodeValue( style );
        return true;
    }

    private static boolean replaceExtToPng(Node n) {
        String attr = n.getNodeValue();
        int idx = attr.lastIndexOf('.');
        if (idx==-1) {
            n.setNodeValue( attr + ".png");
        }
        else {
            n.setNodeValue(attr.substring(0, idx) + ".png");
        }
        return true;
    }

    private static void persistDocument(Document xmlDocument, OutputStream outputSteam) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        DOMSource source = new DOMSource(xmlDocument);
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
//        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        transformer.transform(source, new StreamResult(outputSteam));
    }

    @Nullable
    public static Node getAttrNode(Node n, String attrName) {
        NamedNodeMap attributes = n.getAttributes();
        if (attributes==null) {
            return null;
        }
        return attributes.getNamedItem(attrName);
    }

    @Nullable
    public static String getAttr(Node n, String attrName) {
        NamedNodeMap attributes = n.getAttributes();
        if (attributes==null) {
            return null;
        }
        Node attr = attributes.getNamedItem(attrName);
        return attr!=null ? attr.getNodeValue() : null;
    }

    @Nullable
    private static Node findChildNode(Node n, String name) {
        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            Node nc = n.getChildNodes().item(i);
            if (name.equals(nc.getNodeName())) {
                return nc;
            }
        }
        return null;
    }

    @Data
    public static class ConvertImageResult {
        public final String base64;
        public final Path resultImageFile;
    }

    public static Para getParaForImage(String base64, int binaryIndex) {
    /*
    <w:pict>
        <v:shape id="_x0_0_0_0" style=";visibility:visible;mso-wrap-style:square">
            <v:imagedata src="wordml://Image1" o:title="Image1"/>
        </v:shape>
        <w:binData w:name="wordml://Image1">
                    .. base64 is here ..
        </w:binData>
    </w:pict>

        <w:pict>
            <v:shape id="Image 1" o:spid="_x0000_i1030" type="#_x0000_t75" style="width:152.25pt;height:47.25pt;visibility:visible;mso-wrap-style:square">
                <v:imagedata src="wordml://08000002.wmz" o:title=""/>
            </v:shape>
            <w:binData w:name="wordml://08000002.wmz" xml:space="preserve">
                .. base64 is here ..
            </w:binData>
        </w:pict>
    */
        BinData binData = new BinData();
        binData.addAttribute(Attr.get("w", "name", "wordml://Image"+binaryIndex));
        binData.text.append(base64);

        ai.metaheuristic.ww2003.document.tags.xml.ImageData imageData = new ai.metaheuristic.ww2003.document.tags.xml.ImageData();
        imageData.addAttribute(Attr.get(null, "src", "wordml://Image"+binaryIndex));
        Shape shape = new Shape(imageData);

        Pict pict = new Pict(shape, binData);
        pict.addAttribute(Attr.get(null, "id", "Image "+binaryIndex));
        pict.addAttribute(Attr.get(null, "style", "visibility:visible;mso-wrap-style:square"));

        final Para para = new Para(new Run(pict));
        return para;
    }
}
