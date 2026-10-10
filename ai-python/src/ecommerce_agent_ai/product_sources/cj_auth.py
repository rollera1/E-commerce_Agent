from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from threading import Lock
from typing import Any

from ecommerce_agent_ai.product_sources.cj_error import CjApiError
from ecommerce_agent_ai.product_sources.cj_transport import CjTransport

REFRESH_MARGIN = timedelta(minutes=1)


@dataclass(frozen=True)
class CjTokens:
    """CJ 访问令牌和刷新令牌及其过期时间。"""

    access_token: str
    access_expires_at: datetime
    refresh_token: str
    refresh_expires_at: datetime


class CjTokenProvider:
    """线程安全地获取、缓存和刷新 CJ 访问令牌。"""

    def __init__(self, api_key: str, transport: CjTransport) -> None:
        if not api_key.strip():
            raise ValueError("CJ API key must not be blank")
        self._api_key = api_key
        self._transport = transport
        self._tokens: CjTokens | None = None
        self._lock = Lock()

    def access_token(self) -> str:
        with self._lock:
            now = datetime.now(UTC)
            if self._tokens is None:
                self._tokens = self._request(
                    "/authentication/getAccessToken", {"apiKey": self._api_key}
                )
            if self._tokens.access_expires_at > now + REFRESH_MARGIN:
                return self._tokens.access_token
            if self._tokens.refresh_expires_at > now + REFRESH_MARGIN:
                self._tokens = self._request(
                    "/authentication/refreshAccessToken",
                    {"refreshToken": self._tokens.refresh_token},
                )
            else:
                self._tokens = self._request(
                    "/authentication/getAccessToken", {"apiKey": self._api_key}
                )
            return self._tokens.access_token

    def _request(self, path: str, body: dict[str, str]) -> CjTokens:
        data = self._transport.request("POST", path, json=body)
        try:
            return CjTokens(
                access_token=str(data["accessToken"]),
                access_expires_at=_parse_time(data["accessTokenExpiryDate"]),
                refresh_token=str(data["refreshToken"]),
                refresh_expires_at=_parse_time(data["refreshTokenExpiryDate"]),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise CjApiError(0, None, False) from error


def _parse_time(value: Any) -> datetime:
    parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)
