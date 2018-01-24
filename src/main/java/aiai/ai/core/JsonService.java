/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.core;

import aiai.ai.comm.ExchangeData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class JsonService {

    private ObjectMapper mapper;

    public JsonService() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ExchangeData getExchangeData(String json) {
        try {
            //noinspection UnnecessaryLocalVariable
            ExchangeData data = mapper.readValue(json, ExchangeData.class);
            return data;
        } catch (IOException e) {
            return null;
        }
    }
}
