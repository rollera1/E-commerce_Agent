class CjApiError(RuntimeError):
    """CJ 接口调用或响应解析失败。"""

    def __init__(self, code: int, request_id: str | None, retryable: bool) -> None:
        super().__init__(
            f"CJ API request failed with code {code}; requestId={request_id or 'unknown'}"
        )
        self.code = code
        self.request_id = request_id
        self.retryable = retryable
