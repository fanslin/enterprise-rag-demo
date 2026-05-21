# Kubernetes / Argo CD 日常操作与排障清单

本文档用于记录本项目从本地集群到 ACK 云上集群的常用操作。目标不是背命令，而是在出问题时快速判断问题发生在：

```text
GitHub Actions -> 镜像仓库 -> Argo CD -> Kubernetes Deployment -> Pod -> Service/SLB
```

## 0. 常用 Context

查看当前 kubeconfig 中有哪些集群上下文：

```bash
kubectl config get-contexts
```

切换到本地 kind：

```bash
kubectl config use-context kind-enterprise-rag
```

切换到云上 ACK：

```bash
kubectl config use-context ack-enterprise-rag
```

后续命令也可以显式指定 context，避免误操作：

```bash
kubectl --context ack-enterprise-rag get nodes
```

## 1. 查看集群基础状态

查看节点：

```bash
kubectl --context ack-enterprise-rag get nodes -o wide
```

查看所有命名空间的核心资源：

```bash
kubectl --context ack-enterprise-rag get ns
kubectl --context ack-enterprise-rag get pods -A
```

查看业务命名空间：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get deploy,rs,pod,svc -o wide
```

重点看：

- `Deployment READY` 是否为 `1/1`
- `Pod STATUS` 是否为 `Running`
- `Service EXTERNAL-IP` 是否已有公网 IP
- `IMAGES` 是否为预期的 ACR 镜像 tag

## 2. 查看 GitHub Actions

查看最近的 workflow：

```bash
gh run list --branch main --limit 5
```

跟踪某一次运行：

```bash
gh run watch <run-id> --exit-status
```

查看最近 deploy commit：

```bash
git fetch origin main
git log --oneline origin/main -8
```

正常链路里，业务代码合入 `main` 后，应该看到两类提交：

```text
<业务提交或 PR merge>
chore: deploy <sha> [skip ci]
```

如果没有 `chore: deploy ...`，优先查 GitHub Actions 是否失败。

## 3. 查看 Argo CD

查看 Application 状态：

```bash
kubectl --context ack-enterprise-rag -n argocd get application enterprise-rag-demo -o wide
```

查看完整状态和错误：

```bash
kubectl --context ack-enterprise-rag -n argocd get application enterprise-rag-demo -o yaml
```

手动触发 hard refresh：

```bash
kubectl --context ack-enterprise-rag -n argocd annotate application enterprise-rag-demo \
  argocd.argoproj.io/refresh=hard --overwrite
```

查看 Argo repo-server 日志：

```bash
kubectl --context ack-enterprise-rag -n argocd logs deploy/argocd-repo-server --since=10m --tail=200
```

查看 Argo application-controller 日志：

```bash
kubectl --context ack-enterprise-rag -n argocd logs deploy/argocd-application-controller --since=10m --tail=200
```

常见状态含义：

- `Synced + Healthy`：Git 与集群一致，业务资源健康。
- `OutOfSync`：Git 中期望状态和集群实际状态不一致，等自动同步或手动 Sync。
- `Progressing`：资源已开始更新，但 Pod 还没 Ready。
- `ComparisonError`：Argo 还没能成功生成目标状态，常见原因是 GitHub 超时、仓库权限、manifest 语法错误。

## 4. 访问 Argo CD UI

学习阶段建议用 port-forward：

```bash
kubectl --context ack-enterprise-rag -n argocd port-forward svc/argocd-server 8081:443
```

浏览器访问：

```text
https://localhost:8081
```

获取初始密码：

```bash
kubectl --context ack-enterprise-rag -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

用户名：

```text
admin
```

## 5. 查看业务应用

查看部署状态：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get deploy enterprise-rag-demo -o wide
```

查看 Pod：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get pod -l app=enterprise-rag-demo -o wide
```

查看日志：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag logs deploy/enterprise-rag-demo --tail=100
```

持续看日志：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag logs deploy/enterprise-rag-demo -f
```

查看最近事件：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get events --sort-by=.lastTimestamp | tail -80
```

查看 Deployment 详情：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag describe deploy enterprise-rag-demo
```

查看 Pod 详情：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag describe pod -l app=enterprise-rag-demo
```

## 6. 访问业务应用

查看公网 Service：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get svc enterprise-rag-demo
```

如果 `EXTERNAL-IP` 已存在，可以访问：

```text
http://<EXTERNAL-IP>/
```

健康检查：

```bash
curl http://<EXTERNAL-IP>/api/health/ai
```

如果公网访问异常，先确认：

- `Service TYPE` 是否为 `LoadBalancer`
- `EXTERNAL-IP` 是否已分配
- Pod 是否 `Running`
- Readiness 是否通过
- ACK 安全组和 SLB 监听是否允许 80 端口访问

## 7. 镜像仓库与拉取密钥

当前 ACK 使用 ACR 个人版镜像：

```text
crpi-y9knyaepfs2bdt4q.cn-hangzhou.personal.cr.aliyuncs.com/fanslin-namespace/cn.fanslin:<tag>
```

查看 ACK 中是否已有 ACR 拉取密钥：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get secret acr-pull-secret
```

创建拉取密钥：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag create secret docker-registry acr-pull-secret \
  --docker-server=crpi-y9knyaepfs2bdt4q.cn-hangzhou.personal.cr.aliyuncs.com \
  --docker-username=<你的 ACR 用户名> \
  --docker-password=<你的 ACR 密码>
```

如果密码变更，先删除再重建：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag delete secret acr-pull-secret
```

常见镜像拉取错误：

- `ImagePullBackOff`：镜像拉取失败后的退避状态。
- `ErrImagePull`：镜像第一次拉取失败。
- `insufficient_scope: authorization failed`：通常是 ACR 拉取密钥不存在、密码错误、命名空间/仓库权限不足。
- `repository does not exist`：镜像地址、仓库名或 tag 写错。

## 8. 常见问题定位路径

### GitHub Actions 失败

先看：

```bash
gh run list --branch main --limit 5
gh run view <run-id> --log-failed
```

常见原因：

- 测试失败。
- ACR/GHCR 登录失败。
- GitHub Actions 没有写权限，无法提交 `chore: deploy ...`。
- Docker build 失败。

### Argo 显示 ComparisonError

先看 Application 条件：

```bash
kubectl --context ack-enterprise-rag -n argocd get application enterprise-rag-demo -o yaml
```

如果看到：

```text
failed to list refs ... github.com ... Client.Timeout exceeded
```

表示 ACK 中的 Argo repo-server 访问 GitHub 超时。业务 Pod 可能仍然健康，只是 Argo 暂时无法读取最新 Git 状态。

可先手动刷新：

```bash
kubectl --context ack-enterprise-rag -n argocd annotate application enterprise-rag-demo \
  argocd.argoproj.io/refresh=hard --overwrite
```

如果持续失败，考虑：

- 给 ACK 节点所在 VPC 配 NAT Gateway + SNAT。
- 给节点配置稳定公网出口。
- 将 GitOps 仓库镜像到阿里云 Codeup 或 Gitee。

### Pod 一直 Pending

看事件：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get events --sort-by=.lastTimestamp | tail -80
```

常见原因：

- 节点资源不足：`Insufficient cpu` / `Insufficient memory`
- 节点 Pod 数量已满：`Too many pods`
- 节点不可调度或污点未容忍

### Pod Running 但 Service 访问失败

检查：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag get pod -o wide
kubectl --context ack-enterprise-rag -n enterprise-rag get svc enterprise-rag-demo -o yaml
kubectl --context ack-enterprise-rag -n enterprise-rag describe svc enterprise-rag-demo
```

重点看：

- Service selector 是否匹配 Pod label。
- `targetPort` 是否指向容器的 `8080`。
- `EXTERNAL-IP` 是否存在。
- 云上安全组是否放行。

### Pod CrashLoopBackOff

看日志和上一次退出日志：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag logs deploy/enterprise-rag-demo --tail=100
kubectl --context ack-enterprise-rag -n enterprise-rag logs deploy/enterprise-rag-demo --previous --tail=100
```

再看容器退出原因：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag describe pod -l app=enterprise-rag-demo
```

常见原因：

- 环境变量缺失或值错误。
- 应用启动失败。
- 健康检查路径错误。
- 内存限制过低导致 OOMKilled。

## 9. 回滚

查看 Deployment rollout 历史：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag rollout history deploy/enterprise-rag-demo
```

临时回滚到上一版：

```bash
kubectl --context ack-enterprise-rag -n enterprise-rag rollout undo deploy/enterprise-rag-demo
```

注意：这是直接改集群实际状态。因为 Argo CD 以 Git 为准，如果 Git 里仍是新版本，Argo 后续可能会自动同步回来。

GitOps 更推荐的回滚方式是 revert 对应的 deploy commit：

```bash
git revert <chore-deploy-commit>
git push origin main
```

然后让 Argo CD 同步 Git 中的旧镜像 tag。

## 10. 本地 kind 快速命令

查看 kind 应用：

```bash
kubectl --context kind-enterprise-rag -n enterprise-rag get deploy,pod,svc -o wide
```

本地访问：

```bash
kubectl --context kind-enterprise-rag -n enterprise-rag port-forward svc/enterprise-rag-demo 8080:80
```

如果 port-forward 因 Pod 滚动更新断开，重新执行即可。`kubectl port-forward` 绑定的是某个后端 Pod，Pod 被替换后连接会中断。

## 11. 本地 minikube 快速命令

查看 minikube 状态：

```bash
minikube status
kubectl --context minikube get nodes
```

查看应用：

```bash
kubectl --context minikube -n enterprise-rag get deploy,pod,svc -o wide
```

本地访问：

```bash
kubectl --context minikube -n enterprise-rag port-forward svc/enterprise-rag-demo 8080:80
```

