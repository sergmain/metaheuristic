/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


public class Checksum {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public enum Type {
        MD5, SHA256;

        public String getChecksum(String data) throws IOException {
            return getChecksum( IOUtils.toInputStream(data, Charsets.UTF_8));
        }

        public String getChecksum(byte[] data) throws IOException {
            return getChecksum( new ByteArrayInputStream(data));
        }

        public String getChecksum(InputStream inputStream) throws IOException {
            switch (this) {
                case MD5:
                    return DigestUtils.md5Hex(inputStream);
                case SHA256:
                    return DigestUtils.sha256Hex(inputStream);
                default:
                    throw new IllegalStateException("Not implemented: " + this);
            }
        }
    }

    public Map<Type, String> checksums = new HashMap<>();

    public Checksum() {
    }

    public Checksum(Type type, String checksum) {
        this.checksums.put(type, checksum);
    }

    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("error", e);
        }
    }

    public static Checksum fromJson(String json) {
        try {
            Checksum checksum = mapper.readValue(json, Checksum.class);
            return checksum;
        } catch (IOException e) {
            throw new RuntimeException("error", e);
        }
    }

    @Override
    public String toString() {
        return "Checksum{" +
                "checksums=" + checksums +
                '}';
    }
}
