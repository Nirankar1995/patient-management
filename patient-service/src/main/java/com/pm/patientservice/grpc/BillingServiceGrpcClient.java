package com.pm.patientservice.grpc;


import billing.BillingResponse;
import billing.BillingServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BillingServiceGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

    public BillingServiceGrpcClient(
            @Value("${billing.service.address:billing-service}") String billingServiceAddress,
            @Value("${billing.service.grpc.port:9001}") int billingServicePort
    ) {
        log.info("connecting to billing service GRPC service at {}:{}", billingServiceAddress, billingServicePort);
        this.blockingStub = BillingServiceGrpc.newBlockingStub(
                io.grpc.ManagedChannelBuilder.forAddress(billingServiceAddress, billingServicePort)
                        .usePlaintext()
                        .build()
        );
    }

    public BillingResponse createBillingAmount(String patientId, String name, String email) {
        log.info("Creating billing amount for patientId: {}", patientId);
        billing.BillingRequest request = billing.BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        BillingResponse response = blockingStub.createBillingAmount(request);
        log.info("Received billing response: {}", response);
        return response;
    }
}
