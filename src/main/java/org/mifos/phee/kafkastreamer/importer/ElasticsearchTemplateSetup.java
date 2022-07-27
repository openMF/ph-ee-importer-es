/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package org.mifos.phee.kafkastreamer.importer;

import io.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
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
        IndexConfiguration indexConfiguration = new IndexConfiguration();

        if (indexConfiguration.createTemplate) {
            createRootIndexTemplate();

            if (indexConfiguration.incident) {
                createValueIndexTemplate(ExtendedValueType.INCIDENT);
            }
            if (indexConfiguration.variable) {
                createValueIndexTemplate(ExtendedValueType.VARIABLE);
            }
            if (indexConfiguration.workflowInstance) {
                createValueIndexTemplate(ExtendedValueType.WORKFLOW_INSTANCE);
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

    private void createValueIndexTemplate(final ExtendedValueType extendedValueType) {
        if (!client.putIndexTemplate(extendedValueType)) {
            logger.warn("Put index template for value type {} was not acknowledged", extendedValueType);
        }
    }
}
