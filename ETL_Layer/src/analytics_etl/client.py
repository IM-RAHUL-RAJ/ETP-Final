#Fauxnance HTTP client : Reads the env variable and connects with Fauxnance API 


from __future__ import annotations
from dotenv import load_dotenv

import logging
import os
import time

import requests

from .errors import (
    RateLimitError,
    SymbolRequestError,
    TransientNetworkError,
)

load_dotenv()

logger = logging.getLogger(__name__)

API_KEY_ENV_VAR = "FAUXNANCE_API_KEY"

BASE_URL = os.environ.get(
    "FAUXNANCE_BASE_URL"
)

MAX_ATTEMPTS = 3
BACKOFF_BASE_SECONDS = 1.0


def _api_key() -> str:
    key = os.environ.get(API_KEY_ENV_VAR)

    if not key:
        raise RuntimeError(
            f"{API_KEY_ENV_VAR} is not set in .env."
        )

    return key


def _backoff_seconds(attempt: int) -> float:
    return BACKOFF_BASE_SECONDS * (2 ** (attempt - 1))


class FauxnanceClient:

    def __init__(self, session: requests.Session | None = None):
        self.session = session or requests.Session()

    def health(self) -> bool:
        # GET /health endpoint. 
        response = self.session.get(f"{BASE_URL}/health", timeout=10)
        return response.status_code == 200

    def usage(self) -> dict:
        #GET /usage endpoint. 
        response = self.session.get(
            f"{BASE_URL}/usage",
            headers={"X-Api-Key": _api_key()},
            timeout=10,
        )
        response.raise_for_status()
        return response.json()

    # candles endpoint
    def get_candles(
        self,
        symbol: str,
        from_date: str | None = None,
        to_date: str | None = None,
    ) -> dict:
        headers = {"X-Api-Key": _api_key()}
        params: dict[str, str] = {"interval": "1d"}
        if from_date is not None:
            params["from"] = from_date
        if to_date is not None:
            params["to"] = to_date

        url = f"{BASE_URL}/candles/{symbol}"

        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                response = self.session.get(
                    url, headers=headers, params=params, timeout=15,
                )
            except (requests.ConnectionError, requests.Timeout) as exc:
                if attempt == MAX_ATTEMPTS:
                    logger.error(
                        "NETWORK_ERROR symbol=%s attempts=%d giving_up=true",
                        symbol, attempt,
                    )
                    raise TransientNetworkError(
                        symbol=symbol, attempts=attempt, last_error=exc,
                    ) from exc

                delay = _backoff_seconds(attempt)
                logger.warning(
                    "NETWORK_ERROR symbol=%s attempt=%d retrying_in=%.1fs",
                    symbol, attempt, delay,
                )
                time.sleep(delay)
                continue

            # HTTP 429 - RATE LIMIT
           
            if response.status_code == 429:
                retry_after = self._retry_after_seconds(response)
                logger.error(
                    "RATE_LIMIT symbol=%s retry_after=%s pipeline_stopped=true",
                    symbol, retry_after if retry_after is not None else "unknown",
                )
                raise RateLimitError(retry_after)

            
            # OTHER HTTP 4xx - fail this symbol, keep going with the rest
            
            if 400 <= response.status_code < 500:
                body = response.text.strip()
                logger.error(
                    "SYMBOL_ERROR symbol=%s status=%d message=%s",
                    symbol, response.status_code, body[:200],
                )
                raise SymbolRequestError(
                    symbol=symbol, status_code=response.status_code, body=body,
                )

            
            # HTTP 503 - server temporarily unavailable, retry
            
            if response.status_code == 503:
                if attempt == MAX_ATTEMPTS:
                    logger.error(
                        "SERVER_ERROR symbol=%s status=503 attempts=%d giving_up=true",
                        symbol, attempt,
                    )
                    raise TransientNetworkError(
                        symbol=symbol, attempts=attempt,
                        last_error=RuntimeError("HTTP 503 Service Unavailable"),
                    )

                retry_after = self._retry_after_seconds(response)
                delay = retry_after if retry_after is not None else _backoff_seconds(attempt)
                logger.warning(
                    "SERVER_ERROR symbol=%s status=503 attempt=%d retrying_in=%.1fs",
                    symbol, attempt, delay,
                )
                time.sleep(delay)
                continue

            
            # OTHER HTTP 5xx 
            
            if 500 <= response.status_code < 600:
                if attempt == MAX_ATTEMPTS:
                    logger.error(
                        "SERVER_ERROR symbol=%s status=%d attempts=%d giving_up=true",
                        symbol, response.status_code, attempt,
                    )
                    raise TransientNetworkError(
                        symbol=symbol, attempts=attempt,
                        last_error=RuntimeError(f"HTTP {response.status_code}"),
                    )

                delay = _backoff_seconds(attempt)
                logger.warning(
                    "SERVER_ERROR symbol=%s status=%d attempt=%d retrying_in=%.1fs",
                    symbol, response.status_code, attempt, delay,
                )
                time.sleep(delay)
                continue

            
            # HTTP 202 - data not ready yet, retry
            
            if response.status_code == 202:
                if attempt == MAX_ATTEMPTS:
                    logger.error(
                        "DATA_NOT_READY symbol=%s attempts=%d giving_up=true",
                        symbol, attempt,
                    )
                    raise TransientNetworkError(
                        symbol=symbol, attempts=attempt,
                        last_error=RuntimeError("HTTP 202: market data is not ready"),
                    )

                retry_after = self._retry_after_seconds(response)
                delay = retry_after if retry_after is not None else _backoff_seconds(attempt)
                logger.warning(
                    "DATA_NOT_READY symbol=%s attempt=%d retrying_in=%.1fs",
                    symbol, attempt, delay,
                )
                time.sleep(delay)
                continue

        
            # HTTP 200 - SUCCESS. 
            
            if response.status_code == 200:
                logger.info("FETCH_SUCCESS symbol=%s", symbol)
                return response.json()

          
            # ANY OTHER STATUS
            
            body = response.text.strip()
            logger.error(
                "UNEXPECTED_STATUS symbol=%s status=%d", symbol, response.status_code,
            )
            raise SymbolRequestError(
                symbol=symbol, status_code=response.status_code, body=body,
            )

        raise TransientNetworkError(
            symbol=symbol, attempts=MAX_ATTEMPTS,
            last_error=RuntimeError("Request failed"),
        )

    @staticmethod
    def _retry_after_seconds(response: requests.Response) -> int | None:
        value = response.headers.get("Retry-After")
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            logger.warning("INVALID_RETRY_AFTER value=%r", value)
            return None
