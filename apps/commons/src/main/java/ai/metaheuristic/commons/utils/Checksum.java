/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class Checksum {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public static String getChecksum(EnumsApi.HashAlgo type, String data)  {
        return getChecksum(type, IOUtils.toInputStream(data, StandardCharsets.UTF_8));
    }

    @SneakyThrows
    public static String getChecksum(EnumsApi.HashAlgo type, InputStream inputStream) {
        switch (type) {
            case MD5:
                return DigestUtils.md5Hex(inputStream);
            case SHA256:
                return DigestUtils.sha256Hex(inputStream);
            case SHA256WithSignature:
                throw new IllegalStateException("Shouldn't be created here. Use external methods");
            default:
                throw new IllegalStateException("Checksum for " + type +"  isn't supported yet");
        }
    }

    public Map<EnumsApi.HashAlgo, String> checksums = new HashMap<>();

    public Checksum() {
    }

    public Checksum(EnumsApi.HashAlgo type, String checksum) {
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
            throw new RuntimeException("error in json:\n"+json, e);
        }
    }

    @Override
    public String toString() {
        return "Checksum{" +
                "checksums=" + checksums +
                '}';
    }

    public static void main(String[] args) {
        String c = "{\"checksums\":{\"SHA256\":\"34f55188ece53401987db632429bf5f96758be391a0812d29baad2cb874da974\"}}";


    }
}
