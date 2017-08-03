package aiai.ai.launchpad.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 18:07
 */
public class DatasetChecksum {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public enum Type {
        MD5, SHA256;

        public String getChecksum(InputStream inputStream) throws IOException {
            switch(this) {
                case MD5:
                    return DigestUtils.md5Hex(inputStream);
                case SHA256:
                    return DigestUtils.sha256Hex(inputStream);
                default:
                    throw new IllegalStateException("Not implemented: " + this);
            }
        }
    }

    public static class Checksum {
        public Map<Type, String> checksums = new HashMap<>();

        @Override
        public String toString() {
            return "Checksum{" +
                    "checksums=" + checksums +
                    '}';
        }
    }

    public static String getChecksumAsJson(File file) {

        try {

            String s;
            Checksum checksum = new Checksum();
/*
            // actually not working. need more investigation
            String jsonAsString;
            PipedInputStream in = new PipedInputStream();
            PipedOutputStream pos = new PipedOutputStream(in);
            try (
                    InputStream inputStream = new FileInputStream(file);
                    TeeInputStream tis = new TeeInputStream(inputStream, pos, false)
            ) {
                checksum.checksums.put(Type.MD5, Type.MD5.getChecksum(tis));
                checksum.checksums.put(Type.SHA256, Type.SHA256.getChecksum(in));
            }
*/

            // C! don't use more that one type of checksum
/*
            try( InputStream inputStream = new FileInputStream(file)) {
                checksum.checksums.put(Type.MD5, Type.MD5.getChecksum(inputStream));
            }
*/
            try( InputStream inputStream = new FileInputStream(file)) {
                checksum.checksums.put(Type.SHA256, Type.SHA256.getChecksum(inputStream));
            }
            //noinspection UnnecessaryLocalVariable
            String jsonAsString = mapper.writeValueAsString(checksum);
            return jsonAsString;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("pom.xml");

        final String json = getChecksumAsJson(file);
        System.out.println("checksums: " + json);
        System.out.println("checksums len: " + json.length());

        Checksum checksum = mapper.readValue(json, Checksum.class);

        System.out.println(checksum);
    }
}
