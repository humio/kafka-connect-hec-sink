/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.client;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import com.evanlennick.retry4j.CallExecutorBuilder;
import com.evanlennick.retry4j.Status;
import com.evanlennick.retry4j.config.RetryConfig;
import com.evanlennick.retry4j.config.RetryConfigBuilder;
import com.evanlennick.retry4j.exception.RetriesExhaustedException;
import com.evanlennick.retry4j.exception.UnexpectedException;
import com.humio.connect.hec.Metrics;
import com.humio.connect.hec.Record;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.codahale.metrics.MetricRegistry.name;

public class HECClientImpl implements HECClient {
    private static Logger log = LogManager.getLogger(HECClientImpl.class);
    private class RetryException extends Exception {}

    private String endpoint;
    private String ingestToken;
    private int hecRetryMax;
    private final RetryConfig retryConfig;

    private final Counter postedRecords =
            Metrics.metrics.counter(name(this.getClass(), "posted-records"));
    private final Counter failedHecRequests =
            Metrics.metrics.counter(name(this.getClass(), "failed-hec-requests"));
    private final Timer hecHttpRequests =
            Metrics.metrics.timer(name(this.getClass(), "hec-http-requests"));
    private final Histogram postedBatchSizes =
            Metrics.metrics.histogram(name(this.getClass(), "posted-batch-sizes"));

    public HECClientImpl(String endpoint, String ingestToken, int hecRetryMax, int hecRetryDelay) {
        this.endpoint = endpoint;
        this.ingestToken = ingestToken;
        this.hecRetryMax = hecRetryMax;

        retryConfig = new RetryConfigBuilder()
                .retryOnSpecificExceptions(RetryException.class)
                .withMaxNumberOfTries(hecRetryMax)
                .withDelayBetweenTries(hecRetryDelay, ChronoUnit.SECONDS)
                .withExponentialBackoff()
                .build();
    }

    public void bulkSend(final Collection<Record> records) {
        if (records.size() == 0) return;
        final StringEntity postBody;

        try {
            postBody = new StringEntity(records.stream().map(Record::toJson).collect(Collectors.joining("\n")));
        } catch (UnsupportedEncodingException e) {
            throw new ConnectException(e);
        }

        Callable<CloseableHttpResponse> callable = () -> {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Authorization", "Bearer " + ingestToken);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setEntity(postBody);

            CloseableHttpResponse response;
            final Timer.Context context = hecHttpRequests.time();
            try {
                response = client.execute(httpPost);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    postedRecords.inc(records.size());
                    postedBatchSizes.update(records.size());
                } else {
                    log.warn("HEC endpoint response status code = " + statusCode + ", retrying...");
                    failedHecRequests.inc();
                    throw new RetryException();
                }
            } catch (IOException e) {
                throw new ConnectException(e);
            } finally {
                context.stop();
            }
            return response;
        };

        try {
            Status<CloseableHttpResponse> status = new CallExecutorBuilder()
                    .config(retryConfig)
                    .build()
                    .execute(callable);
            CloseableHttpResponse response = status.getResult();
            try {
                response.close();
            } catch (IOException e) {
                throw new ConnectException(e);
            }
        } catch(RetriesExhaustedException ree) {
            throw new ConnectException("maximum HEC endpoint call retries (" + hecRetryMax + ") exhausted.");
        } catch(UnexpectedException ue) {
            throw new ConnectException(ue);
        }
    }

    public void close() throws IOException {
        log.debug("hec: close()");
    }
}
