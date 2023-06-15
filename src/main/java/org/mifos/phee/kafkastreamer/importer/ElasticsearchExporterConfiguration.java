/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package org.mifos.phee.kafkastreamer.importer;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchExporterConfiguration {

    // elasticsearch http url
    public String url = "http://localhost:9200";

    public final IndexConfiguration index = new IndexConfiguration();
    public final BulkConfiguration bulk = new BulkConfiguration();
    private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();

    public boolean hasAuthenticationPresent() {
        return getAuthentication().isPresent();
    }

    public AuthenticationConfiguration getAuthentication() {
        return authentication;
    }

    @Override
    public String toString() {
        return "ElasticsearchExporterConfiguration{" + "url='" + url + '\'' + ", index=" + index + ", bulk=" + bulk + '}';
    }

    public boolean shouldIndexRecord(final Record<?> record) {
        return shouldIndexRecordType(record.getRecordType()) && shouldIndexValueType(record.getValueType());
    }

    public boolean shouldIndexValueType(final ValueType valueType) {
        switch (valueType) {
            case DEPLOYMENT:
                return index.deployment;
            case ERROR:
                return index.error;
            case INCIDENT:
                return index.incident;
            case JOB:
                return index.job;
            case JOB_BATCH:
                return index.jobBatch;
            case MESSAGE:
                return index.message;
            case MESSAGE_SUBSCRIPTION:
                return index.messageSubscription;
            case VARIABLE:
                return index.variable;
            case VARIABLE_DOCUMENT:
                return index.variableDocument;
            case WORKFLOW_INSTANCE:
                return index.workflowInstance;
            case WORKFLOW_INSTANCE_CREATION:
                return index.workflowInstanceCreation;
            case WORKFLOW_INSTANCE_SUBSCRIPTION:
                return index.workflowInstanceSubscription;
            default:
                return false;
        }
    }

    public boolean shouldIndexRecordType(final RecordType recordType) {
        switch (recordType) {
            case EVENT:
                return index.event;
            case COMMAND:
                return index.command;
            case COMMAND_REJECTION:
                return index.rejection;
            default:
                return false;
        }
    }

    public static class IndexConfiguration {

        // prefix for index and templates
        public String prefix = "zeebe-record";

        // update index template on startup
        public boolean createTemplate = true;

        // record types to export
        public boolean command = false;
        public boolean event = true;
        public boolean rejection = false;

        // value types to export
        public boolean deployment = true;
        public boolean error = false;
        public boolean incident = true;
        public boolean job = false;
        public boolean jobBatch = false;
        public boolean message = false;
        public boolean messageSubscription = false;
        public boolean variable = true;
        public boolean variableDocument = false;
        public boolean workflowInstance = true;
        public boolean workflowInstanceCreation = false;
        public boolean workflowInstanceSubscription = false;

        // size limits
        public int ignoreVariablesAbove = 8191;

        @Override
        public String toString() {
            return "IndexConfiguration{" + "indexPrefix='" + prefix + '\'' + ", createTemplate=" + createTemplate + ", command=" + command
                    + ", event=" + event + ", rejection=" + rejection + ", error=" + error + ", deployment=" + deployment + ", incident="
                    + incident + ", job=" + job + ", message=" + message + ", messageSubscription=" + messageSubscription + ", variable="
                    + variable + ", variableDocument=" + variableDocument + ", workflowInstance=" + workflowInstance
                    + ", workflowInstanceCreation=" + workflowInstanceCreation + ", workflowInstanceSubscription="
                    + workflowInstanceSubscription + ", ignoreVariablesAbove=" + ignoreVariablesAbove + '}';
        }
    }

    public static class BulkConfiguration {

        // delay before forced flush
        public int delay = 5;
        // bulk size before flush
        public int size = 1_000;
        // memory limit of the bulk in bytes before flush
        public int memoryLimit = 10 * 1024 * 1024;

        @Override
        public String toString() {
            return "BulkConfiguration{" + "delay=" + delay + ", size=" + size + ", memoryLimit=" + memoryLimit + '}';
        }
    }

    public static class AuthenticationConfiguration {

        private String username;
        private String password;

        public boolean isPresent() {
            return (username != null && !username.isEmpty()) && (password != null && !password.isEmpty());
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(final String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        @Override
        public String toString() {
            // we don't want to expose this information
            return "AuthenticationConfiguration{Confidential information}";
        }
    }
}
