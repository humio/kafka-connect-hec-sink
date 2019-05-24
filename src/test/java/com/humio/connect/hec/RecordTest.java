/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordTest {

    @Test
    void toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("field", "value");

        long ts = System.currentTimeMillis();
        Record record = new Record(obj, ts, "test-topic", 0);
        record.toJson();

        assertAll(
                () -> assertEquals("value", record.message.getAsJsonObject("event").get("field").getAsString()),
                () -> assertEquals(0, record.partition),
                () -> assertEquals("test-topic", record.topic),
                () -> assertEquals(ts/1000, record.ts)
        );
    }
}