package aiai.ai.utils;

import org.apache.http.client.fluent.Request;

import java.util.HashMap;
import java.util.Map;

public class RestUtils {

    public static void putNoCacheHeaders(Map<String, String> map) {
        map.put("cache-control", "no-cache");
        map.put("expires", "Tue, 01 Jan 1980 1:00:00 GMT");
        map.put("pragma", "no-cache");
    }

    public static void addHeaders(Request request) {
        Map<String, String> map = new HashMap<>();
        putNoCacheHeaders(map);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
    }
}
