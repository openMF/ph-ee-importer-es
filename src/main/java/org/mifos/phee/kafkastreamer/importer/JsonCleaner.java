package org.mifos.phee.kafkastreamer.importer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class JsonCleaner {

    public JSONObject sanitize(JSONObject jsonObject) {
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                sanitize((JSONObject) value);
            } else if (value instanceof JSONArray) {
                sanitize((JSONArray) value);
            } else if ("NOT_PROVIDED".equals(value)) {
                jsonObject.put(key, JSONObject.NULL);
            }
        }
        return jsonObject;
    }

    private void sanitize(JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject) {
                sanitize((JSONObject) item);
            } else if (item instanceof JSONArray) {
                sanitize((JSONArray) item);
            } else if ("NOT_PROVIDED".equals(item)) {
                array.put(i, JSONObject.NULL);
            }
        }
    }
}
