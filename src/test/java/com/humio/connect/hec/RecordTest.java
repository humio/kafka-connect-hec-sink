/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import com.google.gson.JsonObject;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordTest {

    @Test
    void toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("field", "value");
        long ts = System.currentTimeMillis();

        SinkRecord sinkRecord = new SinkRecord(
                "test-topic",
                0,
                null,
                "message-key",
                null,
                null,
                0,
                ts, TimestampType.CREATE_TIME,
                null);
        Record record = new Record(sinkRecord, obj);
        record.toJson();

        assertAll(
                () -> assertEquals("value", record.message.getAsJsonObject("event").get("field").getAsString()),
                () -> assertEquals(0, record.partition),
                () -> assertEquals("test-topic", record.topic),
                () -> assertEquals(ts/1000, record.ts),
                () -> assertEquals(0, record.kafkaOffset)
        );
    }
}