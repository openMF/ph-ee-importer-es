package hu.dpc.rt.kafkastreamer.importer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaImporter {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ElasticClient elasticClient;


    @KafkaListener(topics = "${importer.kafka.topic}")
    public void listen(String rawData) {
        JSONObject data = new JSONObject(rawData);
        logger.trace("from kafka: {}", data.toString(2));

        elasticClient.index(data);

        if (elasticClient.shouldFlush()) {
            int flushed = elasticClient.flush();
            logger.info("flushed {} records to ES", flushed);
        }
    }

}

