package org.mifos.phee.kafkastreamer.importer;

public enum ExtendedValueType {
        JOB((short)0),

        DEPLOYMENT((short)4),

        WORKFLOW_INSTANCE((short)5),

        INCIDENT((short)6),

        PROCESS((short)7),

        PROCESS_INSTANCE((short)8),

        MESSAGE((short)10),

        MESSAGE_SUBSCRIPTION((short)11),

        WORKFLOW_INSTANCE_SUBSCRIPTION((short)12),

        JOB_BATCH((short)14),

        TIMER((short)15),

        MESSAGE_START_EVENT_SUBSCRIPTION((short)16),

        VARIABLE((short)17),

        VARIABLE_DOCUMENT((short)18),

        WORKFLOW_INSTANCE_CREATION((short)19),

        ERROR((short)20),

        WORKFLOW_INSTANCE_RESULT((short)21),

        /**
         * To be used to represent a not known value from a later version.
         */
        SBE_UNKNOWN((short)255),

        /**
         * To be used to represent not present or null.
         */
        NULL_VAL((short)255);

        private final short value;

        ExtendedValueType(final short value)
        {
            this.value = value;
        }

        public short value()
        {
            return value;
        }

        public static ExtendedValueType get(final short value)
        {
            switch (value)
            {
                case 0: return JOB;
                case 4: return DEPLOYMENT;
                case 5: return WORKFLOW_INSTANCE;
                case 6: return INCIDENT;
                case 7: return PROCESS;
                case 8: return PROCESS_INSTANCE;
                case 10: return MESSAGE;
                case 11: return MESSAGE_SUBSCRIPTION;
                case 12: return WORKFLOW_INSTANCE_SUBSCRIPTION;
                case 14: return JOB_BATCH;
                case 15: return TIMER;
                case 16: return MESSAGE_START_EVENT_SUBSCRIPTION;
                case 17: return VARIABLE;
                case 18: return VARIABLE_DOCUMENT;
                case 19: return WORKFLOW_INSTANCE_CREATION;
                case 20: return ERROR;
                case 21: return WORKFLOW_INSTANCE_RESULT;
                case 255: return NULL_VAL;
            }

            return SBE_UNKNOWN;
        }
    }

