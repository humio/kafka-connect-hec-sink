/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.google.gson.*;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;

public class JsonRawStringRecordConverter implements RecordConverter {
    private Gson gson = new Gson();

    @Override
    public Record convert(SinkRecord record) {
        JsonElement el;

        // try JSON encoding first, otherwise pass as primitive JSON string
        try {
            el = gson.fromJson(
                    (String) record.value(),
                    JsonElement.class);
        } catch (JsonSyntaxException ex) {
            el = new JsonPrimitive((String) record.value());
        }

        return new Record(el, record.timestamp(), record.topic(), record.kafkaPartition());
    }
}
