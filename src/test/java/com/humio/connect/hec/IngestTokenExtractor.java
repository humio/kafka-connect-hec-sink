package com.humio.connect.hec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class IngestTokenExtractor {
    private static String getIngestToken(String HUMIO_INDEX) throws IOException {
        System.err.println("extracting " + HUMIO_INDEX + " ingest key from global data snapshot...");
        String s = new String(Files.readAllBytes(Paths.get("humio-data/global-data-snapshot.json")));
        JsonObject data = new JsonParser().parse(s).getAsJsonObject();
        JsonObject dataspaces = data.getAsJsonObject("dataspaces");
        Set<Map.Entry<String, JsonElement>> entries = dataspaces.entrySet();
        for(Map.Entry<String, JsonElement> entry : entries) {
            String key = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();
            if (key.startsWith(HUMIO_INDEX) && key.indexOf("LocalHostRoot") == -1) {
                System.err.println("located " + HUMIO_INDEX + " key: " + key);
                JsonObject sandbox = dataspaces.getAsJsonObject(key);
                JsonObject entity = sandbox.getAsJsonObject("entity");
                JsonArray tokens = entity.getAsJsonArray("ingestTokens");
                JsonObject token = tokens.get(0).getAsJsonObject();
                return token.get("token").getAsString();
            }
        }
        throw new RuntimeException("cannot extract " + HUMIO_INDEX + " key from humio-data/global-data-snapshot.json");
    }

    public static void main(String args[]) throws Exception {
        final String repo;
        if (args.length < 1) {
            repo = "sandbox";
            System.err.println("defaulting to humio index (repo) " + repo);
        } else {
            repo = args[0];
        }
        System.out.println(getIngestToken(repo));
    }

}
