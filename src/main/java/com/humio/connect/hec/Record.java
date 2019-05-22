package com.humio.connect.hec;

import com.google.gson.*;

public class Record {
    public final JsonElement value;
    public final JsonObject message;

    public final String topic;
    public final int partition;
    public final long ts;

    private static Gson gson;

    static {
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                .create();
    }

    public Record(JsonElement value, long ts, String topic, int partition) {
        this.value = value;
        this.ts = ts / 1000;
        this.topic = topic;
        this.partition = partition;
        message = new JsonObject();
    }

    public String toJson() {
        message.add("event", value);
        return gson.toJson(message);
    }
}
