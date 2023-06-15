package org.mifos.phee.kafkastreamer.importer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentsIndexConfiguration {

    public List<String> variables = new ArrayList<String>();

    @Value("${reporting.fields.amount}")
    private Boolean amountVal;

    @Value("${reporting.fields.accountId}")
    private Boolean accountIdVal;

    @Value("${reporting.fields.errorCode}")
    private Boolean errorCodeVal;

    @Value("${reporting.fields.errorDescription}")
    private Boolean errorDescriptionVal;

    @Value("${reporting.fields.externalId}")
    private Boolean externalIdVal;

    @Value("${reporting.fields.initiator}")
    private Boolean initiatorVal;

    @Value("${reporting.fields.initiatorType}")
    private Boolean initiatorTypeVal;

    @Value("${reporting.fields.isNotificationsFailureEnabled}")
    private Boolean isNotificationsFailureEnabledVal;

    @Value("${reporting.fields.isNotificationsSuccessEnabled}")
    private Boolean isNotificationsSuccessEnabledVal;

    @Value("${reporting.fields.mpesaTransactionId}")
    private Boolean mpesaTransactionIdVal;

    @Value("${reporting.fields.mpesaTransactionStatusRetryCount}")
    private Boolean mpesaTransactionStatusRetryCountVal;

    @Value("${reporting.fields.originDate}")
    private Boolean originDateVal;

    @Value("${reporting.fields.partyLookupFailed}")
    private Boolean partyLookupFailedVal;

    @Value("${reporting.fields.phoneNumber}")
    private Boolean phoneNumberVal;

    @Value("${reporting.fields.processDefinitionKey}")
    private Boolean processDefinitionKeyVal;

    @Value("${reporting.fields.processInstanceKey}")
    private Boolean processInstanceKeyVal;

    @Value("${reporting.fields.scenario}")
    private Boolean scenarioVal;

    @Value("${reporting.fields.tenantId}")
    private Boolean tenantIdVal;

    @Value("${reporting.fields.timer}")
    private Boolean timerVal;

    @Value("${reporting.fields.timestamp}")
    private Boolean timestampVal;

    @Value("${reporting.fields.transactionFailed}")
    private Boolean transactionFailedVal;

    @Value("${reporting.fields.transactionId}")
    private Boolean transactionIdVal;

    @Value("${reporting.fields.transferCreateFailed}")
    private Boolean transferCreateFailedVal;

    @Value("${reporting.fields.transferSettlementFailed}")
    private Boolean transferSettlementFailedVal;

    @Value("${reporting.fields.transferResponseCREATE}")
    private Boolean transferResponseCREATEVal;

    @Value("${reporting.fields.currency}")
    private Boolean currencyVal;

    @Value("${reporting.fields.errorInformation}")
    private Boolean errorInformationVal;

    @Value("${reporting.fields.customData}")
    private Boolean customDataVal;

    @Value("${reporting.fields.confirmationReceived}")
    private Boolean confirmationReceivedVal;

    @Value("${reporting.fields.clientCorrelationId}")
    private Boolean clientCorrelationIdVal;

    @Value("${reporting.fields.ams}")
    private Boolean amsVal;

    public PaymentsIndexConfiguration() {}

    public List<String> getVariables() {
        Boolean amount = amountVal;
        Boolean accountId = accountIdVal;
        Boolean errorCode = errorCodeVal;
        Boolean errorDescription = errorDescriptionVal;
        Boolean externalId = externalIdVal;
        Boolean initiator = initiatorVal;
        Boolean initiatorType = initiatorTypeVal;
        Boolean isNotificationsFailureEnabled = isNotificationsFailureEnabledVal;
        Boolean isNotificationsSuccessEnabled = isNotificationsFailureEnabledVal;
        Boolean mpesaTransactionId = mpesaTransactionIdVal;
        Boolean mpesaTransactionStatusRetryCount = mpesaTransactionStatusRetryCountVal;
        Boolean originDate = originDateVal;
        Boolean partyLookupFailed = partyLookupFailedVal;
        Boolean phoneNumber = phoneNumberVal;
        Boolean processDefinitionKey = processDefinitionKeyVal;
        Boolean processInstanceKey = processInstanceKeyVal;
        Boolean scenario = scenarioVal;
        Boolean tenantId = tenantIdVal;
        Boolean timer = timerVal;
        Boolean timestamp = timestampVal;
        Boolean transactionFailed = transactionFailedVal;
        Boolean transactionId = transactionIdVal;
        Boolean transferCreateFailed = transferCreateFailedVal;
        Boolean transferSettlementFailed = transferSettlementFailedVal;
        Boolean transferResponseCREATE = transferResponseCREATEVal;
        Boolean currency = currencyVal;
        Boolean errorInformation = errorInformationVal;
        Boolean customData = customDataVal;
        Boolean confirmationReceived = confirmationReceivedVal;
        Boolean clientCorrelationId = clientCorrelationIdVal;
        Boolean ams = amsVal;

        if (amount) {
            variables.add("amount");
        }
        if (accountId) {
            variables.add("accountId");
        }
        if (errorCode) {
            variables.add("errorCode");
        }
        if (errorDescription) {
            variables.add("errorDescription");
        }
        if (externalId) {
            variables.add("externalId");
        }
        if (initiator) {
            variables.add("initiator");
        }
        if (initiatorType) {
            variables.add("initiatorType");
        }
        if (isNotificationsFailureEnabled) {
            variables.add("isNotificationsFailureEnabled");
        }
        if (isNotificationsSuccessEnabled) {
            variables.add("isNotificationsSuccessEnabled");
        }
        if (mpesaTransactionId) {
            variables.add("mpesaTransactionId");
        }
        if (mpesaTransactionStatusRetryCount) {
            variables.add("mpesaTransactionStatusRetryCount");
        }
        if (originDate) {
            variables.add("originDate");
        }
        if (partyLookupFailed) {
            variables.add("partyLookupFailed");
        }
        if (phoneNumber) {
            variables.add("phoneNumber");
        }
        if (processDefinitionKey) {
            variables.add("processDefinitionKey");
        }
        if (processInstanceKey) {
            variables.add("processInstanceKey");
        }
        if (scenario) {
            variables.add("scenario");
        }
        if (tenantId) {
            variables.add("tenantId");
        }
        if (timer) {
            variables.add("timer");
        }
        if (timestamp) {
            variables.add("timestamp");
        }
        if (transactionFailed) {
            variables.add("transactionFailed");
        }
        if (transactionId) {
            variables.add("transactionId");
        }
        if (transferCreateFailed) {
            variables.add("transferCreateFailed");
        }
        if (transferSettlementFailed) {
            variables.add("transferSettlementFailed");
        }
        if (transferResponseCREATE) {
            variables.add("transferResponse-CREATE");
        }
        if (currency) {
            variables.add("currency");
        }
        if (errorInformation) {
            variables.add("errorInformation");
        }
        if (customData) {
            variables.add("customData");
        }
        if (confirmationReceived) {
            variables.add("confirmationReceived");
        }
        if (clientCorrelationId) {
            variables.add("clientCorrelationId");
        }
        if (ams) {
            variables.add("ams");
        }
        return variables;
    }

}
