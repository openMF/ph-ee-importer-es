package org.mifos.phee.kafkastreamer.importer;

import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Component
public class BulkRequestCollector {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${importer.elasticsearch.bulk-size}")
    private Integer bulkSize;

    private BulkRequest bulkRequest = new BulkRequest();

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    public static <T> T withReadLock(ReadWriteLock lock, Supplier<T> supplier) {
        lock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T> T withWriteLock(ReadWriteLock lock, Supplier<T> supplier) {
        lock.writeLock().lock();
        try {
            return supplier.get();
        } finally {
            lock.writeLock().unlock();
        }
    }


    public void add(IndexRequest indexRequest) {
        withReadLock(readWriteLock, () -> bulkRequest.add(indexRequest));
    }

    public void add(UpdateRequest updateRequest) {
        withReadLock(readWriteLock, () -> bulkRequest.add(updateRequest));
    }

    private BulkResponse exportBulk(RestHighLevelClient client) throws IOException {
//        try (Histogram.Timer timer = metrics.measureFlushDuration()) { .. }
        return withWriteLock(readWriteLock, () -> {
            try {
                return client.bulk(bulkRequest, org.opensearch.client.RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public int flush(RestHighLevelClient client) {
        boolean success;
        int bulkSize = bulkRequest.numberOfActions();
        if (bulkSize > 0) {
            try {
//                metrics.recordBulkSize(bulkSize);
                BulkResponse responses = exportBulk(client);
                success = checkBulkResponses(responses);
            } catch (IOException e) {
                throw new RuntimeException("Failed to flush bulk", e);
            }

            if (success) { // all records where flushed, create new bulk request, otherwise retry next time
                bulkRequest = new BulkRequest();
            }
        }

        return bulkSize;
    }

    public boolean shouldFlush() {
        return bulkRequest.numberOfActions() >= bulkSize;
    }

    private boolean checkBulkResponses(BulkResponse responses) {
        for (BulkItemResponse response : responses) {
            if (response.isFailed()) {
                logger.warn("Failed to flush at least one bulk request {}", response.getFailureMessage());
                return false;
            }
        }

        return true;
    }
}
