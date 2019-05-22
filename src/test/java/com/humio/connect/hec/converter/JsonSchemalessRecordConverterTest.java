package com.humio.connect.hec.converter;

import com.google.gson.JsonObject;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSchemalessRecordConverterTest {

    @Test
    void convert() {
        JsonSchemalessRecordConverter converter = new JsonSchemalessRecordConverter();

        Map<String, Object> objMap = new HashMap<String, Object>();
        objMap.put("test", "value");

        SinkRecord sinkRecord = ConverterTestUtils.makeSinkRecord(objMap);
        Record record = converter.convert(sinkRecord);
        JsonObject obj = (JsonObject) record.value;

        assertEquals(objMap.get("test"), obj.get("test").getAsString());
    }

    // TODO: exercise all json field types
}