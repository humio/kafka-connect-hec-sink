package com.humio.connect.hec.client;

import com.humio.connect.hec.Record;

import java.io.IOException;
import java.util.Collection;

public interface HECClient {
    void bulkSend(Collection<Record> records) throws IOException;
    void close() throws IOException;
}
