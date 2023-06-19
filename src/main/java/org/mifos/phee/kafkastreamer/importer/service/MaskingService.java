package org.mifos.phee.kafkastreamer.importer.service;

public interface MaskingService {

    /**
     * Takes raw data from kafka stream and mask the relevant information.
     *
     * @param rawData
     *            data from kafka stream
     * @return the masked data
     */
    String mask(String rawData) throws Exception;

}
