package com.pm.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    /**
     * This method consumes events from the Kafka topic.
     * It expects the event data to be in byte array format, typically serialized using Protobuf.
     *
     * @param eventData The byte array containing the serialized event data.
     */
    @KafkaListener(topics = "patient", groupId = "analytics-service-group")
    public void consumeEvent(byte[] eventData) {
        // Deserialize the event data
        // For example, if using Protobuf:
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(eventData);
            log.info("Received Patient event: [PatientId: {}, PatientName: {}, PatientEmail: {}]",
                    patientEvent.getPatientId(), patientEvent.getName(), patientEvent.getEmail());
        } catch (InvalidProtocolBufferException e) {
            log.error("Error while parsing Patient event: {}", e.getMessage());
        }

        // Additional processing logic can be added here
    }
}
