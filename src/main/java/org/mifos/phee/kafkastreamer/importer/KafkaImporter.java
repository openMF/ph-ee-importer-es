package org.mifos.phee.kafkastreamer.importer;

import org.json.JSONObject;
import org.mifos.phee.kafkastreamer.importer.service.MaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaImporter {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${importer.masking.enable}")
    private boolean maskingEnable;

    @Autowired
    private MaskingService maskingService;

    @KafkaListener(topics = "${importer.kafka.topic}")
    public void listen(String rawData) throws Exception {
        if (maskingEnable) {
            logger.debug("Before: {}", rawData);
            rawData = maskingService.mask(rawData);
            logger.debug("After: {}", rawData);
        }

        JSONObject data = new JSONObject(rawData);
        logger.trace("from kafka: {}", data.toString(2));

        elasticsearchClient.index(data);

        if (elasticsearchClient.shouldFlush()) {
            int flushed = elasticsearchClient.flush();
            logger.info("flushed {} records to ES", flushed);
        }
    }
}
