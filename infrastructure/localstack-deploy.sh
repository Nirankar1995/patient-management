#!/bin/bash

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_SESSION_TOKEN=test

set -e # Stops the script if any command fails

aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
    --stack-name patient-management

aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file "./cdk.out/localstack.template.json"

aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" --output text