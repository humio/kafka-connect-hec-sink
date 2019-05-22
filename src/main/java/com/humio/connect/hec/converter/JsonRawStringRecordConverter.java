package com.humio.connect.hec.converter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;

public class JsonRawStringRecordConverter implements RecordConverter {
    private Gson gson = new Gson();

    @Override
    public Record convert(SinkRecord record) {
        JsonElement el = gson.fromJson(
                (String) record.value(),
                JsonElement.class);

        return new Record(el, record.timestamp(), record.topic(), record.kafkaPartition());
    }
}
