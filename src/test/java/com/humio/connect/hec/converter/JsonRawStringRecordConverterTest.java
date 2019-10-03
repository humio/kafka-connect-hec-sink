/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonRawStringRecordConverterTest {
    @Test
    void convert() {
        Gson gson = new Gson();

        JsonRawStringRecordConverter converter = new JsonRawStringRecordConverter();

        // test raw string value
        // NOTE: this test is slightly different than the embedded json test below, be aware when modifying.

        SinkRecord sinkRecord = ConverterTestUtils.makeSinkRecord("This is a string value.");
        Record record = converter.convert(sinkRecord);
        JsonObject obj = gson.fromJson(record.toJson(), JsonObject.class);

        assertEquals("This is a string value.", obj.get("event").getAsString());

        // test embedded json
        // NOTE: this test is slightly different than the string test above, be aware when modifying.

        SinkRecord sinkRecord1 = ConverterTestUtils.makeSinkRecord("{\"test\": \"value\"}");
        Record record1 = converter.convert(sinkRecord1);
        JsonObject obj1 = (JsonObject) record1.value;

        assertEquals("value", obj1.get("test").getAsString());
    }
}