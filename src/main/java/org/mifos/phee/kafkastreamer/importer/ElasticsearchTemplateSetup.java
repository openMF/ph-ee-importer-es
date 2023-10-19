package org.mifos.phee.kafkastreamer.importer;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ElasticsearchTemplateSetup {
    public static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${importer.elasticsearch.index-prefix}")
    private String indexPrefix;

    @Autowired
    private ElasticsearchClient client;

    @PostConstruct
    public void setup() {
        createIndexTemplates();
    }

    private void createIndexTemplates() {
        ElasticsearchExporterConfiguration.IndexConfiguration indexConfiguration = new ElasticsearchExporterConfiguration.IndexConfiguration();

        if (indexConfiguration.createTemplate) {
            createRootIndexTemplate();

            if (indexConfiguration.incident) {
                createValueIndexTemplate(ValueType.INCIDENT);
            }
            if (indexConfiguration.variable) {
                createValueIndexTemplate(ValueType.VARIABLE);
            }
        }
    }

    private void createRootIndexTemplate() {
        final String templateName = indexPrefix;
        final String aliasName = indexPrefix;
        final String filename = ZEEBE_RECORD_TEMPLATE_JSON;
        if (!client.putIndexTemplate(templateName, aliasName, filename)) {
            logger.warn("Put index template {} from file {} was not acknowledged", templateName, filename);
        }
    }

    private void createValueIndexTemplate(ValueType valueType) {
        if (!client.putIndexTemplate(valueType)) {
            logger.warn("Put index template for value type {} was not acknowledged", valueType);
        }
    }
}
