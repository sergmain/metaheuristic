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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.document.exceptions.DocumentParseException;
import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import ai.metaheuristic.ww2003.document.tags.xml.*;
import org.apache.commons.lang3.StringUtils;
import javax.annotation.Nullable;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class DocumentParser {

    private final static XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    public static WW2003Document parse(InputStream inputStream) {
        return parse(inputStream, false);
    }

    public static WW2003Document parse(Reader reader) {
        return parse(reader, false);
    }

    public static WW2003Document parse(Reader reader, boolean cardOnly) {
        XMLEventReader eventReader = createXmlEventReader(reader);
        return parse(eventReader, cardOnly);
    }

    public static WW2003Document parse(InputStream inputStream, boolean cardOnly) {
        XMLEventReader eventReader = createXmlEventReader(inputStream);
        WW2003Document ww2003Document = parse(eventReader, cardOnly);
        ThreadLocalUtils.getInnerStyles().initStyles(ww2003Document.getNodes());
        return ww2003Document;
    }

    private static WW2003Document parse(XMLEventReader eventReader, boolean cardOnly) {
        WW2003Document ww2003Document = new WW2003Document();
        WW2003Document.DocProcessing processing = ww2003Document.processing;
        EventIterator<WW2003Document.DocProcessing, XMLEventReader, Consumer<XMLEvent>> eventIterator;
        if (cardOnly) {
            eventIterator = DocumentParser::iterateEventsCardOnly;
        } else {
            eventIterator = DocumentParser::iterateEvents;
        }
        eventIterator.accept(processing, eventReader, event -> {
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    StartElement startElement = event.asStartElement();
                    List<Attr> attributes = getAttributes(startElement.getAttributes());
                    if ("wordDocument".equals(startElement.getName().getLocalPart())) {
                        List<Attr> namespaceAttributes = getAttributes(startElement.getNamespaces());
                        if (namespaceAttributes != null) {
                            if (attributes == null) {
                                attributes = namespaceAttributes;
                            } else {
                                attributes.addAll(namespaceAttributes);
                            }
                        }
                        processWordDocumentTag(processing, attributes);
                    } else {
                        processStartElement(ww2003Document, startElement.getName(), attributes);
                    }
                    break;
                case XMLStreamConstants.CHARACTERS:
                    String characters = event.asCharacters().getData();
                    CDNode lastNode = processing.stack.getLast();
                    if (lastNode == null) {
                        throw new DocumentParseException("201.020 error while adding chars: " + characters);
                    }
                    if (lastNode instanceof BinData binData) {
                        binData.concat(characters);
                    }
                    else {
                        if (lastNode instanceof TextContainer textContainer) {
                            textContainer.concat(characters);
                        }
                        else if (lastNode instanceof UnIdentifiedNode unIdentifiedNode) {
                            UnIdentifiedTextNode textNode = new UnIdentifiedTextNode(unIdentifiedNode.nameSpace, unIdentifiedNode.tagName);
                            textNode.concat(characters);
                            unIdentifiedNode.getParent().replace(unIdentifiedNode, textNode);
                            processing.stack.pollLast();
                            processing.stack.addLast(textNode);
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    processEndElement(ww2003Document.processing, event.asEndElement().getName());
                    break;
            }
        });
        ww2003Document.processing.close();
        return ww2003Document;
    }

    private static void processWordDocumentTag(WW2003Document.DocProcessing processing, @Nullable List<Attr> attributes) {
        CDNode lastNode = processing.stack.peekLast();
        if (lastNode == null || !lastNode.instanceOfWW2003Document()) {
            throw new DocumentParseException("201.040 document wasn't created");
        }
        ((AbstractCDNode) lastNode).attributes = attributes;
    }

    @Nullable
    private static List<Attr> getAttributes(Iterator<? extends Attribute> attributes) {
        List<Attr> attrs = new ArrayList<>();
        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();
            attrs.add(Attr.get(attribute.getName().getPrefix(), attribute.getName().getLocalPart(),
                    attribute.getValue()));
        }
        if (attrs.isEmpty()) {
            return null;
        }
        Collections.sort(attrs);
        return attrs;
    }

    private static void processStartElement(WW2003Document ww2003Document, QName qName, @Nullable List<Attr> attributes) {
        WW2003Document.DocProcessing processing = ww2003Document.processing;
        String tagName = qName.getLocalPart();
        CDNode lastFinalNode;
        if ((lastFinalNode = processing.stack.peekLast()) == null || !lastFinalNode.instanceOfComposite()) {
            throw new DocumentParseException("201.060 Can't add a new tag " + tagName);
        }

        CDNode newNode = XmlTagFactory.createNode(qName.getPrefix(), qName.getLocalPart());
        ((AbstractCDNode) newNode).attributes = attributes;

        if (newNode.instanceOfProperty()) {
            lastFinalNode.asComposite().setProperty(newNode.asProperty());
        } else {
            lastFinalNode.asComposite().add(newNode);
        }

        processing.stack.addLast(newNode);
    }

    private static void processEndElement(WW2003Document.DocProcessing processing, QName qName) {

        CDNode lastNode = processing.stack.peekLast();
        if (lastNode != null) {
            if (!lastNode.instanceOfXmlTag()) {
                throw new DocumentProcessingException("201.080 Bad instance of class " + lastNode.getClass().getSimpleName());
            }
            XmlTag xmlTag = lastNode.asXmlTag();
            if (xmlTag.getTagName().equalsIgnoreCase(qName.getLocalPart())) {
                String nameSpace = xmlTag.getNameSpace();
                if ((StringUtils.isBlank(nameSpace) && StringUtils.isBlank(qName.getPrefix())) || (nameSpace != null && nameSpace.equalsIgnoreCase(qName.getPrefix()))) {
                    processing.stack.pollLast();
                }
            }
        }
    }

    @FunctionalInterface
    interface EventIterator<P, E, C> {
        void accept(P p, E e, C c);
    }

    private static void iterateEvents(WW2003Document.DocProcessing processing, XMLEventReader eventReader, Consumer<XMLEvent> consumer) {
        try {
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                consumer.accept(event);
            }
        } catch (XMLStreamException e) {
            throw new DocumentParseException("201.100 "+e.getMessage());
        }
    }

    private static void iterateEventsCardOnly(WW2003Document.DocProcessing processing, XMLEventReader eventReader, Consumer<XMLEvent> consumer) {
        try {
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                consumer.accept(event);
            }
        } catch (XMLStreamException e) {
            throw new DocumentParseException("201.120 "+e.getMessage());
        }
    }

    private static XMLEventReader createXmlEventReader(InputStream inputStream) {
        try {
            return XML_FACTORY.createXMLEventReader(inputStream);
        } catch (XMLStreamException e) {
            throw new DocumentParseException("201.140 "+e.getMessage());
        }
    }

    private static XMLEventReader createXmlEventReader(Reader reader) {
        try {
            return XML_FACTORY.createXMLEventReader(reader);
        } catch (XMLStreamException e) {
            throw new DocumentParseException("201.160 "+e.getMessage());
        }
    }

}