# Jenkins Deployment Prerequisites

Run these commands on the Jenkins EC2 host before the first CD deployment if old
application workloads are still running in the existing `capstone` cluster.

This deletes application workloads only. It does not delete the EKS cluster,
worker nodes, Services, ConfigMaps, Secrets, or Kafka PVC data.

```bash
set -e

export AWS_REGION=ap-south-1
export EKS_CLUSTER_NAME=capstone
export K8S_NAMESPACE=default

aws eks update-kubeconfig \
  --region "$AWS_REGION" \
  --name "$EKS_CLUSTER_NAME"

kubectl config current-context
kubectl get nodes

kubectl -n "$K8S_NAMESPACE" delete deployment \
  auth-service \
  order-service \
  executor-service \
  frontend \
  --ignore-not-found

kubectl -n "$K8S_NAMESPACE" delete statefulset kafka --ignore-not-found

kubectl -n "$K8S_NAMESPACE" get pods
```

After this cleanup, run `JenkinsfileCD`. It applies the Kubernetes overlay,
sets each Deployment to the selected ECR `IMAGE_TAG`, and waits for the
rollouts.

## Important

Do not run `kubectl delete namespace`, `kubectl delete pvc`, or
`eksctl delete cluster` as part of this prerequisite. Those commands delete
shared infrastructure or persistent data.

This cleanup is normally needed only when an earlier rollout is stuck or when
old workloads are consuming RDS connections. A healthy CD rollout should update
Deployments without manual deletion.
