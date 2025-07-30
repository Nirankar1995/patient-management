package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App app, final String id, final StackProps props) {
        super(app, id, props);
        // Create a VPC for LocalStack
        vpc = createVpc();

        DatabaseInstance authServiceDb =
                createDatabaseInstance("AuthServiceDb", "auth-service-db", "user");

        DatabaseInstance patientServiceDb =
                createDatabaseInstance("PatientServiceDb", "patient-service-db", "admin_user");

        CfnHealthCheck authServiceDbHealthCheck =
                createDbHealthCheck(authServiceDb, "AuthServiceDbHealthCheck");

        CfnHealthCheck patientServiceDbHealthCheck =
                createDbHealthCheck(patientServiceDb, "PatientServiceDbHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        ecsCluster = createEcsCluster();

        FargateService authService = createFargateService(
                "AuthService",
                "auth-service",
                List.of(4004),
                authServiceDb,
                "user",
                Map.of("JWT_SECRET", "715ef0f2c6b36b30bceffffc6e21268d596d49491634eeee972368dab0aec39d")
        );

        authService.getNode().addDependency(authServiceDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService(
                "BillingService",
                "billing-service",
                List.of(4001, 9001),
                null,
                null,
                null
        );

        FargateService analyticsService = createFargateService(
                "AnalyticsService",
                "analytics-service",
                List.of(4002),
                null,
                null,
                null
        );

        analyticsService.getNode().addDependency(mskCluster);

        FargateService patientService = createFargateService(
                "PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                "admin_user",
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                )
        );

        patientService.getNode().addDependency(patientServiceDbHealthCheck);
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(mskCluster);
        patientService.getNode().addDependency(billingService);

        createApiGatewayService();


    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // Default is all AZs in the region
                .build();
    }

    private DatabaseInstance createDatabaseInstance(String id, String dbName, String dbUsername) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret(dbUsername))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

    }

    private CfnHealthCheck createDbHealthCheck(DatabaseInstance dbInstance, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(dbInstance.getDbInstanceEndpointPort()))
                        .ipAddress(dbInstance.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster() {
        List<String> privateSubnets = vpc.getPrivateSubnets().stream()
                .map(ISubnet::getSubnetId).toList();
        int numberOfAzs = privateSubnets.size();
        // Ensure number of broker nodes is multiple of number of AZs
        int numberOfBrokerNodes = numberOfAzs * 2; // Using 2 brokers per AZ for high availability

        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.1")
                .numberOfBrokerNodes(numberOfBrokerNodes)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.large")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId).toList())
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private FargateService createFargateService(
            String id,
            String imageName,
            List<Integer> ports,
            DatabaseInstance db,
            String dbUsername,
            Map<String, String> additionalEnvVars) {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                    .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                    .streamPrefix(imageName)
                                .build()));

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511");

        if(additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }

        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", String.format("jdbc:postgresql://%s:%s/%s-db",
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName));
            envVars.put("SPRING_DATASOURCE_USERNAME", dbUsername);
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    private void createApiGatewayService(){
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this,  "ApiGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL", "http://host.docker.internal:4004"
                        ))
                        .portMappings(List.of(4003).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();

        taskDefinition.addContainer("ApiGatewayContainer", containerOptions);

        // Create the Application Load Balanced Fargate Service for API Gateway
        ApplicationLoadBalancedFargateService apiGatewayService =
                ApplicationLoadBalancedFargateService.Builder.create(this, "ApiGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .healthCheckGracePeriod(Duration.seconds(60))
                        .build();
    }

    public static void main(String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());
        StackProps stackProps = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
//                .env(software.amazon.awscdk.Environment.builder()
//                        .account("123456789012") // Replace with your AWS account ID
//                        .region("us-west-2") // Replace with your desired AWS region
//                        .build())
                .build();

        new LocalStack(app, "LocalStack", stackProps);
        app.synth();
        System.out.println("LocalStack CDK application synthesized successfully.");
    }
}
