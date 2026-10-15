from __future__ import annotations


class PostgresETLError(Exception):
    pass


class PostgresConnectionError(PostgresETLError):


    def __init__(self, message: str):
        super().__init__(f"Postgres connection failed: {message}")


class PostgresExtractError(PostgresETLError):
    def __init__(self, message: str):
        super().__init__(f"Postgres extract failed: {message}")


class TransformError(PostgresETLError):

    def __init__(self, row: dict, reason: str):
        self.row    = row
        self.reason = reason
        super().__init__(f"Transform failed: {reason} | row={row}")


class LoadError(PostgresETLError):


    def __init__(self, message: str):
        super().__init__(f"DuckDB load failed: {message}")
