package com.pm.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    @Override
    public void createBillingAmount(BillingRequest request, StreamObserver<BillingResponse> responseObserver) {
        log.info("Billing Request received - {}", request);
        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId("12345")
                .setStatus("SUCCESS")
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
