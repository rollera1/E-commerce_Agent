# 基于多 Agent 的电商自动化运营系统

当前代码处于第一批次工程骨架阶段。目标是建立 Java、Python、React 与 MySQL 的最小可运行环境，并验证 Java 能通过 REST 调用 Python。Shopify 和 CJdropshipping 将从后续批次开始接入。

## 目录

```text
backend-java/  Spring Boot 业务后端和 MySQL 数据所有权
ai-python/     FastAPI 商品来源与未来 AI 能力服务
frontend/      React 商家操作页面
deploy/        Ubuntu 服务器上的数据库基础设施
scripts/       Windows 本地启动与验收脚本
```

## 固定开发版本

- JDK 25（本项目默认路径 `D:\jdks\jdk25`，不修改系统全局 JDK）
- Spring Boot 4.1.1、Maven 3.9+
- Python 3.12、uv
- Node.js 24、npm 11
- React 19.2.8、TypeScript 7.0.2、Vite 8.2.2
- Ubuntu 服务器 Docker、MySQL 8.4

## 首次启动

建议直接用 VS Code 打开本目录，即打开 `项目` 文件夹，而不是它的上级目录。项目级配置会让 Java 扩展使用 `D:\jdks\jdk25`，不会影响其他项目使用 JDK 17。

### 1. 准备环境变量

复制 `.env.example` 为 `.env`。第一周的本地默认值可以直接使用；`.env` 已被 Git 忽略。

```powershell
Copy-Item .env.example .env
```

### 2. 打开数据库 SSH 隧道

MySQL 已在 Ubuntu 服务器的独立容器中运行。打开一个 PowerShell：

```powershell
.\scripts\start-database.cmd
```

保持该窗口开启。脚本通过本机 SSH 配置中的 `cporder` 别名，把服务器的
`127.0.0.1:3308` 映射为本机 `127.0.0.1:3307`。数据库端口没有暴露到公网，
也不会占用服务器上其他项目使用的 `3306` 和 `3307`。

服务器数据库的一次性部署配置保存在 `deploy/compose.yaml`。当前资源为：

- 容器：`ecommerce-agent-mysql`
- 数据卷：`ecommerce-agent-mysql-data`
- 镜像：`mysql:8.4`
- 监听：仅服务器 `127.0.0.1:3308`

### 3. 启动 Python

打开另一个 PowerShell：

```powershell
.\scripts\start-python.cmd
```

验证地址：

- 健康检查：<http://localhost:8005/health>
- Swagger UI：<http://localhost:8005/docs>

### 4. 启动 Java

打开另一个 PowerShell：

```powershell
.\scripts\start-backend.cmd
```

这个脚本只在自己的进程内设置 `JAVA_HOME=D:\jdks\jdk25`。关闭窗口后环境变量失效，系统默认 JDK 17 不受影响。

验证地址：

- Java 健康检查：<http://localhost:8085/api/health>
- Java 调用 Python：<http://localhost:8085/api/health/python>

也可以运行自动验收：

```powershell
.\scripts\check-services.ps1
```

### 5. 启动前端

打开另一个 PowerShell：

```powershell
.\scripts\start-frontend.cmd
```

浏览器访问 <http://localhost:5175>，点击“检查服务”。开发服务器会把 `/api` 请求代理到 Java 的 `8085` 端口。

## 测试与构建

Java：

```powershell
cd backend-java
$env:JAVA_HOME = "D:\jdks\jdk25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test
```

Python：

```powershell
cd ai-python
uv sync
uv run ruff check .
uv run pytest
```

前端：

```powershell
cd frontend
npm.cmd install
npm.cmd run build
```

## 第一批次接口

| 方法 | 地址 | 作用 |
|---|---|---|
| `GET` | `/api/health` | Java 服务自身健康检查 |
| `GET` | `/api/health/python` | Java 调用 Python 并返回下游状态 |
| `GET` | `http://localhost:8005/health` | Python 服务自身健康检查 |

第一批次不需要 Shopify、CJ 或 LLM 密钥，只使用电脑上现有的服务器 SSH 配置。任何真实业务密钥都只能写入本地 `.env`，不得提交到 Git。
