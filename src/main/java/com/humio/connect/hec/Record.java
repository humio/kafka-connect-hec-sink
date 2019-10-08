/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import com.google.gson.*;
import org.apache.kafka.connect.sink.SinkRecord;

public class Record {
    public final JsonElement value;
    public final JsonObject message;

    public final String topic;
    public final int partition;
    public final long ts;
    public final long kafkaOffset;

    public final SinkRecord sinkRecord;

    private static Gson gson;

    static {
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                .create();
    }

    public Record(SinkRecord sinkRecord, JsonElement value) {
        this.value = value;
        this.ts = sinkRecord.timestamp() / 1000;
        this.topic = sinkRecord.topic();
        this.partition = sinkRecord.kafkaPartition();
        this.kafkaOffset = sinkRecord.kafkaOffset();
        this.sinkRecord = sinkRecord;
        message = new JsonObject();
    }

    public String toJson() {
        message.add("event", value);
        return gson.toJson(message);
    }
}
