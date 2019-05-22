package com.humio.connect.hec;

import java.io.File;
import java.util.List;
import java.util.Map;

public class TestUtils {
    public static final String DOCKER_COMPOSE_FILE = "src/test/resources/docker/docker-compose.yml";
    public static final String SINK_CONNECTOR_CONFIG = "src/test/resources/config/json_sink_connector.json";

    public static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) { return false; }
            }
        }
        // either file or an empty directory
        System.out.println("removing file or directory : " + dir.getName());
        return dir.delete();
    }

    public static String extractHostnameFromDockerCompose(Map compose, String serviceName) {
        return (String)((Map)((Map)compose.get("services")).get(serviceName)).get("hostname");
    }

    public static int extractHostPortFromDockerCompose(Map compose, String serviceName) {
        return Integer.parseInt(((String)((List)((Map)((Map)compose.get("services"))
                .get(serviceName)).get("ports")).get(0)).split(":")[1]);
    }

}
