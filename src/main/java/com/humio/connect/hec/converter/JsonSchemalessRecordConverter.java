package com.humio.connect.hec.converter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.sink.SinkRecord;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class JsonSchemalessRecordConverter implements RecordConverter {
    private JsonConverter converter;
    private Gson gson = new Gson();

    public JsonSchemalessRecordConverter() {
        converter = new JsonConverter();
        converter.configure(Collections.singletonMap("schemas.enable", "false"), false);
    }

    @Override
    public Record convert(SinkRecord record) {
        String payload = new String(converter.fromConnectData(
                record.topic(),
                record.valueSchema(),
                record.value()),
                StandardCharsets.UTF_8);

        JsonElement el = gson.fromJson(
                payload,
                JsonElement.class);

        return new Record(el, record.timestamp(), record.topic(), record.kafkaPartition());
    }
}
