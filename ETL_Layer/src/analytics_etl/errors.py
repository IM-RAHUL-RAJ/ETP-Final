
from __future__ import annotations


class FauxnanceError(Exception):
    pass


class RateLimitError(FauxnanceError):
   
    def __init__(self, retry_after_seconds: int | None):
        self.retry_after_seconds = retry_after_seconds

        if retry_after_seconds is None:
            message = (
                "Fauxnance rate limit reached (HTTP 429); "
                "Retry-After was not provided."
            )
        else:
            message = (
                "Fauxnance rate limit reached (HTTP 429); "
                f"Retry-After={retry_after_seconds} seconds."
            )

        super().__init__(message)


class SymbolRequestError(FauxnanceError):
    
    def __init__(self, symbol: str, status_code: int, body: str):
        self.symbol = symbol
        self.status_code = status_code
        self.body = body

        message_preview = body[:200]

        super().__init__(
            f"[{symbol}] request rejected with HTTP "
            f"{status_code}: {message_preview}"
        )


class TransientNetworkError(FauxnanceError):

    def __init__(self, symbol: str, attempts: int, last_error: Exception):
        self.symbol = symbol
        self.attempts = attempts
        self.last_error = last_error

        super().__init__(
            f"[{symbol}] network request failed after "
            f"{attempts} attempts: {last_error}"
        )


class MalformedPayloadError(FauxnanceError):

    def __init__(self, symbol: str, reason: str, row: dict | None = None):
        self.symbol = symbol
        self.reason = reason
        self.row = row

        super().__init__(f"[{symbol}] malformed payload: {reason}")
