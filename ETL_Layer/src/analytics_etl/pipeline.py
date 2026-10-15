from __future__ import annotations


import logging
import sys


from .config import SYMBOL_UNIVERSE
from .extract import extract_candles
from .transform import transform
from .load import load
from .analysis.run_analysis import run_analysis


from .dashboard import generate_dashboard

from .errors import RateLimitError, SymbolRequestError, TransientNetworkError

FROM_DATE = "2026-04-10"
TO_DATE = "2026-07-11"


logging.basicConfig(
    level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s"
)


logger = logging.getLogger(__name__)


# MAIN PIPELINE


def run_pipeline(symbols=None):

    symbols = symbols or SYMBOL_UNIVERSE
    total_loaded = 0
    logger.info("=" * 60)

    logger.info("STARTING MARKET ANALYTICS PIPELINE")

    logger.info("=" * 60)

    for symbol in symbols:

        logger.info("Processing %s", symbol)

        # EXTRACT
        try:

            raw_data = extract_candles(
                symbol,
                FROM_DATE,  # "2026-07-10",
                TO_DATE,  # "2026-07-11"
            )

        except RateLimitError as exc:

            logger.error("RATE LIMIT HIT: %s", exc)

            sys.exit(1)

        except SymbolRequestError as exc:

            logger.error("INVALID SYMBOL %s : %s", symbol, exc)

            continue

        except TransientNetworkError as exc:

            logger.error("NETWORK FAILURE %s : %s", symbol, exc)

            continue

        # TRANSFORM

        try:

            result = transform(raw_data)

        except Exception as exc:

            logger.exception("TRANSFORM FAILED %s", symbol)

            continue

        # QUARANTINE SECTION

        if result["quarantine"]:

            logger.warning(
                "%d bad rows quarantined for %s", len(result["quarantine"]), symbol
            )

        # LOAD

        try:

            loaded = load(result)

            total_loaded += loaded

            logger.info("Loaded %d rows for %s", loaded, symbol)

        except Exception as exc:

            logger.exception("LOAD FAILED %s", symbol)

            continue

    # GENERATE THE DASHBOARD

    try:

        logger.info("Generating dashboard...")
        run_analysis()
        generate_dashboard()

        logger.info("Dashboard created successfully")

    except Exception as exc:

        logger.exception("Dashboard generation failed")

    logger.info("=" * 60)

    logger.info("PIPELINE COMPLETE")

    logger.info("TOTAL ROWS LOADED : %d", total_loaded)

    logger.info("=" * 60)

    return total_loaded


def main():
    run_pipeline()


if __name__ == "__main__":

    main()
