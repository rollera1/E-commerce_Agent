import argparse
from pathlib import Path

from ecommerce_agent_ai.evaluation.copy_seo.runner import run_evaluation


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the copy/SEO comparison experiment")
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--mode", default="SMOKE_FAKE", choices=["SMOKE_FAKE", "FORMAL_LIVE_MODEL"])
    args = parser.parse_args()
    run_evaluation(args.dataset, args.output, experiment_mode=args.mode)


if __name__ == "__main__":
    main()
