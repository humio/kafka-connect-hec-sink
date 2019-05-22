package com.humio.connect.hec.service;

import com.humio.connect.hec.Record;

import java.io.IOException;
import java.util.Collection;

public interface HECService {
    void process(Collection<Record> records);
    void closeClient() throws IOException;
    void flushWait() throws InterruptedException;
}
