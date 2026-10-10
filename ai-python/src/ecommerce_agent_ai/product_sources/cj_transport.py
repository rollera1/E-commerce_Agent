import time
from threading import Lock
from typing import Any

import httpx

from ecommerce_agent_ai.product_sources.cj_error import CjApiError


class CjTransport:
    """封装 CJ HTTP 请求、响应协议和错误分类。"""

    def __init__(self, client: httpx.Client, base_url: str, *, min_interval: float = 0) -> None:
        self._client = client
        self._base_url = base_url.rstrip("/")
        self._min_interval = min_interval
        self._last_request = 0.0
        self._lock = Lock()

    def request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        try:
            with self._lock:
                delay = self._min_interval - (time.monotonic() - self._last_request)
                if delay > 0:
                    time.sleep(delay)
                self._last_request = time.monotonic()
                response = self._client.request(method, f"{self._base_url}{path}", **kwargs)
        except httpx.RequestError as error:
            raise CjApiError(-1, None, True) from error
        return self._response_data(response)

    @staticmethod
    def _response_data(response: httpx.Response) -> dict[str, Any]:
        try:
            payload = response.json()
        except ValueError as error:
            raise CjApiError(response.status_code, None, response.status_code >= 500) from error
        code = int(payload.get("code", response.status_code))
        succeeded = response.is_success and code == 200 and payload.get("result", True) is not False
        if not succeeded:
            retryable = response.status_code == 429 or code in {
                429,
                1600000,
                1600200,
                1600201,
                1600301,
            }
            raise CjApiError(code, payload.get("requestId"), retryable)
        data = payload.get("data")
        if not isinstance(data, dict):
            raise CjApiError(code, payload.get("requestId"), False)
        return data
