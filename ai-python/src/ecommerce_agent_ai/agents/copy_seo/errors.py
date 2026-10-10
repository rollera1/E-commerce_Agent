from ecommerce_agent_ai.agents.copy_seo.models import AgentTrace


class CopySeoAgentError(RuntimeError):
    def __init__(
        self,
        error_type: str,
        retryable: bool,
        *,
        trace: AgentTrace | None = None,
    ) -> None:
        super().__init__(f"Copy/SEO Agent failed: {error_type}")
        self.error_type = error_type
        self.retryable = retryable
        self.trace = trace
