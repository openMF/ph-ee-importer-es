package org.mifos.phee.kafkastreamer.importer.service;

import org.springframework.stereotype.Service;


public interface MaskingService {

    /**
     * Takes raw data from kafka stream and mask the relevant information.
     * @param rawData data from kafka stream
     * @return the masked data
     */
    String mask(String rawData) throws Exception;

}
