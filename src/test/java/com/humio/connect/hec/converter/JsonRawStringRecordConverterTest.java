/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.google.gson.JsonObject;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonRawStringRecordConverterTest {
    @Test
    void convert() {
        JsonRawStringRecordConverter converter = new JsonRawStringRecordConverter();

        SinkRecord sinkRecord = ConverterTestUtils.makeSinkRecord("{\"test\": \"value\"}");
        Record record = converter.convert(sinkRecord);
        JsonObject obj = (JsonObject) record.value;

        assertEquals("value", obj.get("test").getAsString());
    }
}