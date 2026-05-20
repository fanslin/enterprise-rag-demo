# minikube 部署指南

本文档用于第一阶段学习：把 `enterprise-rag-demo` 容器化，并部署到本机 minikube 集群。

## 目标

- 用 Docker 构建 Spring Boot 镜像。
- 用 Kubernetes `Deployment` 管理应用 Pod。
- 用 Kubernetes `Service` 暴露应用。
- 用 `kubectl logs`、`kubectl describe`、`kubectl port-forward` 排查和访问服务。

默认部署使用 `SPRING_PROFILES_ACTIVE=local`，也就是本地 Mock AI 模式，不需要配置 Groq 和智谱 API Key。

配置拆分为两类：

- `k8s/minikube/configmap.yaml`：非敏感配置，例如模型名、Base URL、RAG 参数、当前 profile。
- `enterprise-rag-secrets` Secret：敏感配置，例如 `GROQ_API_KEY` 和 `ZAI_API_KEY`。默认可选，不创建也能用 local profile 跑通。

## 前置条件

```bash
docker --version
kubectl version --client
minikube version
```

启动集群：

```bash
minikube start --driver=docker
kubectl get nodes
kubectl get pods -A
```

看到 `minikube` 节点为 `Ready`，系统 Pod 为 `Running` 后继续。

## 构建镜像

先在本机打包 jar：

```bash
./mvnw package
```

让当前终端的 Docker 命令连接到 minikube 内部 Docker：

```bash
eval $(minikube docker-env)
```

构建应用镜像：

```bash
docker build -t enterprise-rag-demo:local .
```

确认镜像已在 minikube 的 Docker 环境里：

```bash
docker images enterprise-rag-demo
```

## 部署应用

```bash
kubectl apply -k k8s/minikube
```

查看资源：

```bash
kubectl -n enterprise-rag get all
kubectl -n enterprise-rag get pods
```

等待 Pod 变成 `Running` 且 `READY` 为 `1/1`。

查看 ConfigMap：

```bash
kubectl -n enterprise-rag get configmap enterprise-rag-config -o yaml
```

确认 Pod 已从 ConfigMap 读取配置：

```bash
kubectl -n enterprise-rag exec deploy/enterprise-rag-demo -- printenv SPRING_PROFILES_ACTIVE APP_RAG_TOP_K GROQ_CHAT_MODEL
```

## 访问应用

使用端口转发：

```bash
kubectl -n enterprise-rag port-forward svc/enterprise-rag-demo 8080:80
```

打开：

```text
http://localhost:8080
```

也可以检查 AI 健康状态：

```bash
curl http://localhost:8080/api/health/ai
```

local profile 下应看到返回内容里包含 `local`。

## 常用排查命令

查看 Pod：

```bash
kubectl -n enterprise-rag get pods -o wide
```

查看日志：

```bash
kubectl -n enterprise-rag logs deploy/enterprise-rag-demo
```

查看 Deployment 事件：

```bash
kubectl -n enterprise-rag describe deploy enterprise-rag-demo
```

查看 Pod 事件：

```bash
kubectl -n enterprise-rag describe pod <pod-name>
```

重新部署：

```bash
docker build -t enterprise-rag-demo:local .
kubectl -n enterprise-rag rollout restart deploy/enterprise-rag-demo
kubectl -n enterprise-rag rollout status deploy/enterprise-rag-demo
```

## 练习 Secret

第一阶段建议先用 local profile。你可以先创建 dummy Secret 来理解注入机制，不要提交真实 Key 到 Git。

创建测试 Secret：

```bash
kubectl -n enterprise-rag create secret generic enterprise-rag-secrets \
  --from-literal=GROQ_API_KEY='dummy-groq-key' \
  --from-literal=ZAI_API_KEY='dummy-zai-key' \
  --dry-run=client -o yaml | kubectl apply -f -
```

Secret 创建或更新后，重启 Pod 让环境变量重新注入：

```bash
kubectl -n enterprise-rag rollout restart deploy/enterprise-rag-demo
kubectl -n enterprise-rag rollout status deploy/enterprise-rag-demo
```

不要把真实 Secret 值打印到终端。可以只检查变量是否存在：

```bash
kubectl -n enterprise-rag exec deploy/enterprise-rag-demo -- sh -c 'test -n "$GROQ_API_KEY" && echo GROQ_API_KEY_SET; test -n "$ZAI_API_KEY" && echo ZAI_API_KEY_SET'
```

查看 Secret 对象：

```bash
kubectl -n enterprise-rag get secret enterprise-rag-secrets
```

`k8s/minikube/secret.remote.example.yaml` 只是示例文件，里面只能放占位符，不要填真实 Key 后提交。

## 使用真实模型服务

要调用真实模型服务时，先用真实值更新 Secret：

```bash
kubectl -n enterprise-rag create secret generic enterprise-rag-secrets \
  --from-literal=GROQ_API_KEY='你的 Groq API Key' \
  --from-literal=ZAI_API_KEY='你的智谱 API Key' \
  --dry-run=client -o yaml | kubectl apply -f -
```

然后修改 `k8s/minikube/configmap.yaml`：

1. 删除 `SPRING_PROFILES_ACTIVE=local`。
2. 或把它改成空字符串：`SPRING_PROFILES_ACTIVE: ""`。

重新应用：

```bash
kubectl apply -k k8s/minikube
kubectl -n enterprise-rag rollout status deploy/enterprise-rag-demo
```

## 清理

只删除本应用：

```bash
kubectl delete namespace enterprise-rag
```

删除整个 minikube 集群：

```bash
minikube delete
```
