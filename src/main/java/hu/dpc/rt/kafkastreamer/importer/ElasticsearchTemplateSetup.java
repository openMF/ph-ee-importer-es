/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package hu.dpc.rt.kafkastreamer.importer;

import io.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.zeebe.protocol.record.ValueType;
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

            if (indexConfiguration.deployment) {
                createValueIndexTemplate(ValueType.DEPLOYMENT);
            }
            if (indexConfiguration.error) {
                createValueIndexTemplate(ValueType.ERROR);
            }
            if (indexConfiguration.incident) {
                createValueIndexTemplate(ValueType.INCIDENT);
            }
            if (indexConfiguration.job) {
                createValueIndexTemplate(ValueType.JOB);
            }
            if (indexConfiguration.jobBatch) {
                createValueIndexTemplate(ValueType.JOB_BATCH);
            }
            if (indexConfiguration.message) {
                createValueIndexTemplate(ValueType.MESSAGE);
            }
            if (indexConfiguration.messageSubscription) {
                createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION);
            }
            if (indexConfiguration.variable) {
                createValueIndexTemplate(ValueType.VARIABLE);
            }
            if (indexConfiguration.variableDocument) {
                createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT);
            }
            if (indexConfiguration.workflowInstance) {
                createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE);
            }
            if (indexConfiguration.workflowInstanceCreation) {
                createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_CREATION);
            }
            if (indexConfiguration.workflowInstanceSubscription) {
                createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
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

    private void createValueIndexTemplate(final ValueType valueType) {
        if (!client.putIndexTemplate(valueType)) {
            logger.warn("Put index template for value type {} was not acknowledged", valueType);
        }
    }
}
