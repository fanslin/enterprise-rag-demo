# kind + GitHub Actions + Argo CD 部署指南

本文档用于第二阶段学习：把第一阶段的手动部署升级为 GitOps 风格的 CI/CD。

## 目标

最终链路：

```text
git push main
  -> GitHub Actions 运行测试并打包 jar
  -> GitHub Actions 构建 Docker 镜像
  -> 镜像推送到 GHCR
  -> GitHub Actions 更新 k8s/kind/deployment.yaml 里的镜像 tag
  -> Argo CD 发现 Git 变化并同步到 kind 集群
```

这条链路里，CI 和 CD 的职责是分开的：

- GitHub Actions 负责测试、打包、构建镜像、更新 GitOps manifest。
- Argo CD 负责持续比较 Git 与集群状态，并把集群同步到 Git 里的期望状态。

## 前置条件

```bash
docker --version
kubectl version --client
kind version
```

如果没有安装：

```bash
brew install kind
```

## 创建 kind 集群

```bash
kind create cluster --name enterprise-rag --config k8s/kind/cluster.yaml
kubectl cluster-info --context kind-enterprise-rag
kubectl get nodes
```

确认节点为 `Ready`。

## 本地手动验证 kind manifest

第一次 GitHub Actions 跑完前，`k8s/kind/deployment.yaml` 里的镜像还是 `bootstrap` 占位值，不能直接从 GHCR 拉到真实镜像。

如果你只是想先验证 YAML 结构：

```bash
kubectl apply -k k8s/kind --dry-run=client
```

如果你想在 kind 中手动跑本地镜像：

```bash
./mvnw package
docker build -t enterprise-rag-demo:kind .
kind load docker-image enterprise-rag-demo:kind --name enterprise-rag
kubectl apply -k k8s/kind
kubectl -n enterprise-rag set image deploy/enterprise-rag-demo enterprise-rag-demo=enterprise-rag-demo:kind
kubectl -n enterprise-rag rollout status deploy/enterprise-rag-demo
```

访问：

```bash
kubectl -n enterprise-rag port-forward svc/enterprise-rag-demo 8080:80
```

打开：

```text
http://localhost:8080
```

## GitHub Actions 配置

本仓库已新增：

```text
.github/workflows/ci-image.yml
```

它会在 Pull Request 上运行测试和打包，在 `main` 分支 push 时额外执行：

1. 构建镜像。
2. 推送到 `ghcr.io/fanslin/enterprise-rag-demo`。
3. 把 `k8s/kind/deployment.yaml` 中的镜像更新为 `sha-<commit-sha>`。
4. 提交一条 `chore: deploy ... [skip ci]` commit。

你需要在 GitHub 仓库设置里确认：

1. `Settings -> Actions -> General -> Workflow permissions`
2. 选择 `Read and write permissions`
3. 勾选允许 GitHub Actions 创建和批准 Pull Request 的选项可暂时不用开

如果 GHCR package 是私有的，kind 集群默认拉不到镜像。学习阶段建议先把 GHCR package 设为 Public。

## 安装 Argo CD

创建命名空间：

```bash
kubectl create namespace argocd
```

安装 Argo CD：

```bash
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server --timeout=180s
```

打开 Argo CD UI：

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

访问：

```text
https://localhost:8081
```

获取初始 admin 密码：

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

用户名：

```text
admin
```

## 创建 Argo CD Application

应用清单在：

```text
k8s/argocd/enterprise-rag-demo-application.yaml
```

应用它：

```bash
kubectl apply -f k8s/argocd/enterprise-rag-demo-application.yaml
kubectl -n argocd get applications
```

Argo CD 会监听：

```text
https://github.com/fanslin/enterprise-rag-demo.git
path: k8s/kind
targetRevision: main
```

如果仓库是私有仓库，需要先在 Argo CD 中配置仓库访问凭据。学习阶段最简单的做法是先使用公开仓库。

## 完整验证流程

1. 把当前改动提交并推到 GitHub。
2. 合并或推送到 `main`。
3. 打开 GitHub Actions，确认 `CI Image` 成功。
4. 确认 GHCR 中出现 `sha-<commit-sha>` 镜像。
5. 确认 GitHub Actions 自动提交了 `chore: deploy ...`。
6. 打开 Argo CD UI，观察应用同步状态变成 `Synced` 和 `Healthy`。
7. 检查 kind 中应用状态：

```bash
kubectl -n enterprise-rag get deploy,po,svc
kubectl -n enterprise-rag logs deploy/enterprise-rag-demo --tail=80
```

访问应用：

```bash
kubectl -n enterprise-rag port-forward svc/enterprise-rag-demo 8080:80
curl http://localhost:8080/api/health/ai
```

## 私有 GHCR 镜像拉取

如果你保持 GHCR package 私有，需要创建 image pull secret。

先创建 GitHub Personal Access Token，至少需要 `read:packages` 权限。

```bash
kubectl -n enterprise-rag create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=<你的 GitHub 用户名> \
  --docker-password=<你的 PAT> \
  --docker-email=<你的邮箱>
```

然后给 Deployment 增加：

```yaml
spec:
  template:
    spec:
      imagePullSecrets:
        - name: ghcr-credentials
```

学习阶段我建议先用公开 package，少一个认证变量，更容易看清 CI/CD 主链路。

## 常见问题

### Pod 变成 ImagePullBackOff

查看事件：

```bash
kubectl -n enterprise-rag describe pod -l app=enterprise-rag-demo
```

常见原因：

- GHCR package 是私有的，但集群没有 image pull secret。
- manifest 中的镜像 tag 还停留在 `bootstrap`。
- GitHub Actions 没有成功推送镜像。

### Argo CD 一直 OutOfSync

查看 Application：

```bash
kubectl -n argocd describe application enterprise-rag-demo
```

常见原因：

- Argo CD 无法访问 GitHub 仓库。
- `repoURL`、`targetRevision` 或 `path` 写错。
- 当前分支不是 `main`，但 Application 监听的是 `main`。

### GitHub Actions 无法提交 manifest

检查仓库设置：

```text
Settings -> Actions -> General -> Workflow permissions -> Read and write permissions
```

如果 main 分支有保护规则，需要允许 GitHub Actions bot 推送，或者改成通过 Pull Request 更新部署 manifest。
