class ProductSelectionAgentError(RuntimeError):
    """选品 Agent 无法安全完成任务时使用的统一异常。"""

    def __init__(self, error_type: str, retryable: bool) -> None:
        super().__init__(error_type)
        self.error_type = error_type
        self.retryable = retryable
