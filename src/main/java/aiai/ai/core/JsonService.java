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
