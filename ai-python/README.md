# Python 服务

该服务在第一周仅提供健康检查。后续将在不持有业务数据库的前提下，接入商品来源并承载 Agent、LLM 与 RAG 能力。

```powershell
uv sync
uv run uvicorn ecommerce_agent_ai.main:app --app-dir src --host 0.0.0.0 --port 8005 --reload
```
