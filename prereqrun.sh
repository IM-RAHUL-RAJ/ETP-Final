#!/usr/bin/env bash
set -euo pipefail

# Stop application workloads before Jenkins CD deploys a new image.
# This uses the current kubectl context and the default namespace.
# It does not delete the cluster, services, secrets, PVCs, or Kafka data.

kubectl delete deployment \
  auth-service \
  order-service \
  executor-service \
  frontend \
  --ignore-not-found

kubectl delete statefulset kafka --ignore-not-found

kubectl get pods
