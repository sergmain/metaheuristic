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

package ai.metaheuristic.ww2003.utils;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.ww2003.exception.ChangeEncodingException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.lang.Nullable;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class XmlUtils {

    private static final String ENCODING_ATTR = "encoding";
    public static final String DEFAULT_ENCODING_UTF_8 = "utf-8";

    public enum ConvertStatus {CONVERTED, ALREADY_IN_UTF8, ERROR}

    @Data
    public static class ConvertingResult {
        public String doc;
        public InputStream inputStream;
        public ConvertStatus status;
        public String error;

        ConvertingResult(String doc, ConvertStatus status) {
            this.doc = doc;
            this.status = status;
        }

        ConvertingResult(InputStream inputStream, ConvertStatus status) {
            this.inputStream = inputStream;
            this.status = status;
        }

        ConvertingResult(ConvertStatus status) {
            this.status = status;
        }

        ConvertingResult(ConvertStatus status, String error) {
            this.status = status;
            this.error = error;
        }
    }

    private final static ConvertingResult ALREADY_IN_UTF8 = new ConvertingResult(ConvertStatus.ALREADY_IN_UTF8);

    public static void validate(String xml) {
        validate(xml, true);
    }

    public static void validate(String xml, boolean addRootElement) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            DefaultHandler handler = new DefaultHandler();
            SAXParser parser = factory.newSAXParser();
            parser.parse(IOUtils.toInputStream(addRootElement ? "<root>\n"+xml+"\n</root>" : xml, StandardCharsets.UTF_8), handler);
        }
        catch (SAXException | IOException | ParserConfigurationException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    public static ConvertingResult convertXmlToUtf8(InputStream is, boolean isMemory) {
        InputStream inputStream;
        if (!is.markSupported()) {
            inputStream = new BufferedInputStream(new BOMInputStream(is));
        } else {
            inputStream = new BOMInputStream(is);
        }
        inputStream.mark(10000);
        try {
            byte[] bytes = new byte[2];
            int count = inputStream.read(bytes);
            if (count < 2) {
                return new ConvertingResult(ConvertStatus.ERROR, "File is too short and can't be valid xml file");
            }
            if (bytes[0] != '<' || bytes[1] != '?') {
                return new ConvertingResult(ConvertStatus.ERROR, "File isn't in xml format");
            }

            // method getHeader() returns header in lower case
            String header = getHeader(inputStream);
            String encodingAttr = getEncodingAttr(header);

            switch(encodingAttr) {
                case DEFAULT_ENCODING_UTF_8:
                    return ALREADY_IN_UTF8;
                default:
                    return new ConvertingResult(ConvertStatus.ERROR,"encoding "+encodingAttr+" isn't supported, header: " + header);
            }
        }
        catch (ChangeEncodingException e) {
            return new ConvertingResult(ConvertStatus.ERROR, e.getMessage());
        }
        catch (Throwable th) {
            log.error("Error", th);
            String message = th.getMessage();
            if (S.b(message)) {
                message = "Exception message is blank, class: " + th.getClass().getName();
            }
            return new ConvertingResult(ConvertStatus.ERROR, message);
        }
    }

    public static String getEncodingAttr(@Nullable String header) {
        if (S.b(header)) {
            return DEFAULT_ENCODING_UTF_8;
        }
        String s = header.toLowerCase();
        if (!s.contains(ENCODING_ATTR)) {
            return DEFAULT_ENCODING_UTF_8;
        }
        String step1 = s.substring(s.indexOf(ENCODING_ATTR) + ENCODING_ATTR.length() ).strip();
        if (step1.charAt(0)!='=') {
            throw new ChangeEncodingException("Bad format of header: " + header);
        }
        String step2 = step1.substring(1).strip();
        if (step2.charAt(0)!='\"') {
            throw new ChangeEncodingException("Bad format of header: " + header);
        }
        //noinspection UnnecessaryLocalVariable
        String encoding = step2.substring(1, step2.indexOf("\"",2)).strip();
        return encoding;
    }

    private static String getHeader(InputStream bis) throws IOException {
        byte[] bytes;
        int count;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bytes = new byte[100];
        while ((count = bis.read(bytes)) != -1) {
            baos.write(bytes, 0, count);
            boolean isFound = false;
            for (byte aByte : bytes) {
                if (aByte == '>') {
                    isFound = true;
                    break;
                }
            }
            if (isFound) {
                break;
            }
        }
        String header = baos.toString().toLowerCase();
        // An input stream doesn't contain '<?' because we've already read it before
        // to determine is that file in xml format or not
        header = "<?" + header.substring(0, header.indexOf('>')+1);
        return header;
    }
}
