# ACK 云上部署指南

本文档用于第三阶段学习：把 `enterprise-rag-demo` 从本地 kind 迁移到阿里云 ACK 托管集群。

## 目标

云上链路：

```text
GitHub Actions
  -> 构建多架构镜像并推送到 GHCR
  -> 更新 k8s/ack/deployment.yaml 中的镜像 tag
  -> Argo CD 监听 main 分支的 k8s/ack
  -> ACK 集群自动部署
  -> LoadBalancer Service 暴露公网访问入口
```

第一轮仍使用 `SPRING_PROFILES_ACTIVE=local`，不依赖真实 Groq 和智谱 API Key。先跑通云上 Kubernetes 和 GitOps，再接真实 Secret。

## 1. 连接 ACK 集群

在阿里云 ACK 控制台进入你的集群：

```text
集群信息 -> 连接信息 / kubeconfig
```

下载或复制公网访问 kubeconfig，并合并到本机 `~/.kube/config`，然后确认：

```bash
kubectl config get-contexts
kubectl config use-context <你的 ACK context>
kubectl get nodes
kubectl get pods -A
```

看到节点为 `Ready` 后继续。

建议给 ACK context 起一个容易识别的名字，例如：

```bash
kubectl config rename-context <原 context 名> ack-enterprise-rag
kubectl config use-context ack-enterprise-rag
```

## 2. 本地验证 ACK manifests

```bash
kubectl apply -k k8s/ack --dry-run=client
```

这一步只验证 YAML 结构，不会创建云资源。

## 3. 安装 Argo CD

创建命名空间：

```bash
kubectl create namespace argocd
```

安装 Argo CD：

```bash
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

等待核心组件就绪：

```bash
kubectl -n argocd rollout status deploy/argocd-server --timeout=300s
kubectl -n argocd rollout status deploy/argocd-repo-server --timeout=300s
kubectl -n argocd get pods
```

如果 `applicationsets.argoproj.io` CRD 缺失，补一次：

```bash
kubectl apply --server-side \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.4.2/manifests/crds/applicationset-crd.yaml
```

## 4. 打开 Argo CD UI

学习阶段先用端口转发：

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

访问：

```text
https://localhost:8081
```

获取初始密码：

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

用户名：

```text
admin
```

## 5. 创建云上 Application

确认当前 context 是 ACK：

```bash
kubectl config current-context
```

应用 ACK 版 Application：

```bash
kubectl apply -f k8s/argocd/enterprise-rag-demo-ack-application.yaml
kubectl -n argocd get application enterprise-rag-demo
```

Argo CD 会监听：

```text
repoURL: https://github.com/fanslin/enterprise-rag-demo.git
targetRevision: main
path: k8s/ack
```

## 6. 等待应用和公网入口

查看应用资源：

```bash
kubectl -n enterprise-rag get deploy,po,svc
kubectl -n enterprise-rag rollout status deploy/enterprise-rag-demo --timeout=180s
```

ACK 会为 `type: LoadBalancer` 的 Service 创建云负载均衡实例。等待 `EXTERNAL-IP` 出现：

```bash
kubectl -n enterprise-rag get svc enterprise-rag-demo -w
```

出现公网地址后访问：

```bash
curl http://<EXTERNAL-IP>/api/health/ai
```

预期返回：

```json
{"mode":"local","chatConfigured":true,"embeddingConfigured":true,"message":"本地 Mock 模式已启用，不会调用外部模型服务。"}
```

浏览器打开：

```text
http://<EXTERNAL-IP>/
```

## 7. 后续接真实模型 Secret

第一轮跑通后，再创建真实 API Key：

```bash
kubectl -n enterprise-rag create secret generic enterprise-rag-secrets \
  --from-literal=GROQ_API_KEY='你的 Groq API Key' \
  --from-literal=ZAI_API_KEY='你的智谱 API Key'
```

然后修改 `k8s/ack/deployment.yaml`：

1. 删除 `SPRING_PROFILES_ACTIVE=local`。
2. 增加：

```yaml
envFrom:
  - secretRef:
      name: enterprise-rag-secrets
```

注意不要把真实 Key 提交进 Git。

## 8. 成本清理

`type: LoadBalancer` 会创建云负载均衡资源，学习结束后及时清理：

```bash
kubectl delete -f k8s/argocd/enterprise-rag-demo-ack-application.yaml
kubectl delete namespace enterprise-rag
```

如果不继续使用 ACK 集群，也可以在阿里云控制台释放集群、节点池和负载均衡相关资源。
