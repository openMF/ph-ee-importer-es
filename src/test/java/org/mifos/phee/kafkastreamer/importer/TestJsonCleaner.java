package org.mifos.phee.kafkastreamer.importer;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJsonCleaner {

    @Test
    public void test() {
        var input = new JSONObject()
                .put("first", "second")
                .put("third", "NOT_PROVIDED")
                .put("nested", new JSONObject()
                        .put("boo", "NOT_PROVIDED"));

        assertTrue(input.toString().contains("NOT_PROVIDED"));
        JSONObject result = new JsonCleaner().sanitize(input);
        assertFalse(result.toString().contains("NOT_PROVIDED"));
    }

}