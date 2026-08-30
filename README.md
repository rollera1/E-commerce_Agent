# 基于多 Agent 的电商自动化运营系统

当前代码已进入第二批次：在第一周 Java、Python、React 与服务器 MySQL 工程骨架上，增加 Shopify 开发店铺授权、GraphQL 店铺信息查询和连接状态页面。CJdropshipping 将在后续批次接入。

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

复制 `.env.example` 为 `.env`。`.env` 已被 Git 忽略，真实 Shopify Client Secret 只能写在这个本地文件中。

```powershell
Copy-Item .env.example .env
```

第二周连接 Shopify 前，填写以下字段：

```dotenv
SHOPIFY_ENABLED=true
SHOPIFY_SHOP_DOMAIN=你的开发店铺.myshopify.com
SHOPIFY_API_VERSION=2026-07
SHOPIFY_CLIENT_ID=Dev-Dashboard-中的-Client-ID
SHOPIFY_CLIENT_SECRET=Dev-Dashboard-中的-Client-Secret
```

当前实现使用 Shopify 的 Client Credentials Grant，适用于应用和 Development Store 同在一个 Dev Dashboard 组织的开发场景。应用需要先在 Dev Dashboard 中选择 `write_products,read_orders` 权限并安装到该开发店铺。如果不在同一组织，先不要启用上述配置，需要改用嵌入式应用的 Token Exchange 授权流程。

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
脚本也会读取项目根目录的 `.env`，变量同样只在该进程内生效。

验证地址：

- Java 健康检查：<http://localhost:8085/api/health>
- Java 调用 Python：<http://localhost:8085/api/health/python>
- Shopify 连接状态：<http://localhost:8085/api/shopify/connection>

也可以运行自动验收：

```powershell
.\scripts\check-services.ps1
```

第二周完成真实 Shopify 配置后，可以使用严格模式，额外要求店铺状态必须为 `CONNECTED`：

```powershell
.\scripts\check-services.ps1 -RequireShopify
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

## 当前接口

| 方法 | 地址 | 作用 |
|---|---|---|
| `GET` | `/api/health` | Java 服务自身健康检查 |
| `GET` | `/api/health/database` | Java 对 MySQL 执行真实连接检查 |
| `GET` | `/api/health/python` | Java 调用 Python 并返回下游状态 |
| `GET` | `http://localhost:8005/health` | Python 服务自身健康检查 |
| `GET` | `/api/shopify/connection` | 查看 Shopify 是否配置及已保存的店铺元数据 |
| `POST` | `/api/shopify/connection` | 申请短期令牌，执行真实 GraphQL 查询并保存店铺连接 |

Shopify 访问令牌只缓存在 Java 进程内并在过期前刷新，不返回前端、不写入数据库。数据库仅保存店铺 ID、域名、名称、币种和验证时间。任何真实业务密钥都只能写入本地 `.env`，不得提交到 Git。

## 第二批次验收结果

- Dev Dashboard 组织：`HuaShun Tech`
- Development Store：`ecommerce-agent-dev.myshopify.com`
- 应用：`Ecommerce Agent MVP`
- 有效版本：`v0.1.0-shopify-connect`
- 权限：`write_products,read_orders`
- 真实 GraphQL 查询：通过
- 重复连接：数据库保持 1 条店铺记录
- Java 自动化测试：25 项通过
- Java、Python、服务器 MySQL 与 Shopify 严格验收：通过
