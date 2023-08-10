package org.mifos.phee.kafkastreamer.importer.service;

import static org.apache.commons.text.StringEscapeUtils.unescapeJava;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mifos.phee.kafkastreamer.importer.KafkaVariables;
import org.mifos.phee.kafkastreamer.importer.utils.AesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MaskingServiceImpl implements MaskingService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("#{'${importer.masking.fields}'.split(',')}")
    private List<String> fieldsToBeMasked;

    @Value("${importer.masking.key}")
    private String encryptionKey;

    @Override
    public String mask(String rawData) throws Exception {
        log.debug("Inside mask");
        JSONObject data = new JSONObject(rawData);
        String valueType = data.getString(KafkaVariables.VALUE_TYPE);
        if (!valueType.equalsIgnoreCase(KafkaVariables.VALUE_TYPE_VARIABLE)) {
            // do nothing when record value type is [KafkaVariables.VALUE_TYPE_VARIABLE]
            log.debug("Do nothing");
            return rawData;
        }

        JSONObject value = data.getJSONObject(KafkaVariables.VALUE);
        String name = value.getString(KafkaVariables.NAME);
        log.debug("NAME: {}, VALUE: {}", name, value);
        if (name.equalsIgnoreCase(KafkaVariables.CHANNEL_REQUEST)) {
            log.debug("Inside CHANNEL_REQUEST condition");
            String valueStringifiedJsonString = value.getString(KafkaVariables.VALUE);
            JSONObject channelRequest = getJsonObjectFromStringifiedJson(valueStringifiedJsonString);

            List<String>fieldsRequiredMasking=new ArrayList<>();
            fieldsRequiredMasking.add(KafkaVariables.PAYER);
            fieldsRequiredMasking.add(KafkaVariables.PAYEE);
            if(AesUtil.checkForMaskingFields(channelRequest,fieldsRequiredMasking)){
                return rawData;
            }
            String payerPartyIdentifier = "", payeePartyIdentifier = "";

            if (channelRequest.has("payer")) {
                Object payerValue = channelRequest.get("payer");
                Object payeeValue = channelRequest.get("payee");

                if (payerValue instanceof JSONArray) {
                    JSONArray payerArray = (JSONArray) payerValue;
                    JSONArray payeeArray = (JSONArray) payeeValue;

                    JSONObject payerObject = payerArray.getJSONObject(0);
                    payerPartyIdentifier = payerObject.getString(KafkaVariables.PARTY_ID_IDENTIFIER);
                    JSONObject payeeObject = payeeArray.getJSONObject(0);
                    payeePartyIdentifier = payeeObject.getString(KafkaVariables.PARTY_ID_IDENTIFIER);

                    payerPartyIdentifier = encryptData(payerPartyIdentifier);
                    payeePartyIdentifier = encryptData(payeePartyIdentifier);

                    payerObject.put(KafkaVariables.PARTY_ID_IDENTIFIER, payerPartyIdentifier);
                    payeeObject.put(KafkaVariables.PARTY_ID_IDENTIFIER, payeePartyIdentifier);
                } else if (payerValue instanceof JSONObject) {
                    JSONObject payerObject = (JSONObject) payerValue;
                    JSONObject payeeObject = (JSONObject) payeeValue;
                    payerPartyIdentifier = payerObject.getJSONObject(KafkaVariables.PARTY_ID_INFO)
                            .getString(KafkaVariables.PARTY_IDENTIFIER);
                    payeePartyIdentifier = payeeObject.getJSONObject(KafkaVariables.PARTY_ID_INFO)
                            .getString(KafkaVariables.PARTY_IDENTIFIER);
                    payerPartyIdentifier = encryptData(payerPartyIdentifier);
                    payeePartyIdentifier = encryptData(payeePartyIdentifier);

                    channelRequest.getJSONObject(KafkaVariables.PAYER).getJSONObject(KafkaVariables.PARTY_ID_INFO)
                            .put(KafkaVariables.PARTY_IDENTIFIER, payerPartyIdentifier);
                    channelRequest.getJSONObject(KafkaVariables.PAYEE).getJSONObject(KafkaVariables.PARTY_ID_INFO)
                            .put(KafkaVariables.PARTY_IDENTIFIER, payeePartyIdentifier);
                }

            }
            value.put(KafkaVariables.VALUE, channelRequest.toString());
        } else if (name.equalsIgnoreCase(KafkaVariables.CHANNEL_GSMA_REQUEST)) {
            log.debug("Inside CHANNEL_GSMA_REQUEST condition");
            String valueStringifiedJsonString = value.getString(KafkaVariables.VALUE);
            JSONObject channelGsmaRequest = getJsonObjectFromStringifiedJson(valueStringifiedJsonString);

            List<String>fieldsRequiredMasking=new ArrayList<>();
            fieldsRequiredMasking.add(KafkaVariables.DEBIT_PARTY);
            fieldsRequiredMasking.add(KafkaVariables.CREDIT_PARTY);

            if(AesUtil.checkForMaskingFields(channelGsmaRequest,fieldsRequiredMasking)){
                return rawData;
            }

            String debitIdentifier = channelGsmaRequest.getJSONArray(KafkaVariables.DEBIT_PARTY).getJSONObject(0)
                    .getString(KafkaVariables.VALUE);
            String creditIdentifier = channelGsmaRequest.getJSONArray(KafkaVariables.CREDIT_PARTY).getJSONObject(0)
                    .getString(KafkaVariables.VALUE);

            debitIdentifier = encryptData(debitIdentifier);
            creditIdentifier = encryptData(creditIdentifier);

            channelGsmaRequest.getJSONArray(KafkaVariables.DEBIT_PARTY).getJSONObject(0).put(KafkaVariables.VALUE, debitIdentifier);
            channelGsmaRequest.getJSONArray(KafkaVariables.CREDIT_PARTY).getJSONObject(0).put(KafkaVariables.VALUE, creditIdentifier);

            value.put(KafkaVariables.VALUE, channelGsmaRequest.toString());
        } else {
            log.debug("Inside ELSE condition");
            for (String field : fieldsToBeMasked) {
                log.debug("Field: {}", field);
                if (name.equalsIgnoreCase(field)) {
                    log.debug("Field matched: {}", name);
                    String fieldValue = value.getString(KafkaVariables.VALUE);
                    fieldValue = encryptData(fieldValue);
                    value.put(KafkaVariables.VALUE, fieldValue);
                }
            }
        }

        data.put(KafkaVariables.VALUE, value);
        return data.toString();
    }

    // un-stringifies and returns the [JSONObject] instance from string
    private JSONObject getJsonObjectFromStringifiedJson(String stringifiedJson) {
        String s = unescapeJava(stringifiedJson);
        s = s.substring(1, s.length() - 1);
        return new JSONObject(s);
    }

    // encrypts the data based on the length of the data passed
    private String encryptData(String data) throws Exception {
        String masked = "";
        String unmasked = "";
        if (data.isEmpty()) {
            // nothing to mask
            return data;
        } else if (data.length() == 1) {
            // mask everything
            masked = AesUtil.encrypt(data, encryptionKey);
        } else if (data.length() == 2) {
            // leave last digit and mask first
            unmasked = data.substring(data.length() - 1);
            masked = AesUtil.encrypt(data.substring(0, data.length() - 1), encryptionKey);
        } else {
            // leave last 2 digit and mask rest of it
            unmasked = data.substring(data.length() - 2);
            masked = AesUtil.encrypt(data.substring(0, data.length() - 2), encryptionKey);
        }
        return new StringBuilder().append("{").append(masked).append("}").append(unmasked).toString();
    }
}
