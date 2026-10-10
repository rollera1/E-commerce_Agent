import json
import os
import time
from collections import defaultdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.factory import build_copy_seo_model
from ecommerce_agent_ai.agents.copy_seo.tools import RuleBasedContentValidator
from ecommerce_agent_ai.evaluation.copy_seo.dataset import load_dataset
from ecommerce_agent_ai.evaluation.copy_seo.scoring import automatic_scores
from ecommerce_agent_ai.evaluation.copy_seo.strategies import build_strategies

HUMAN_DIMENSIONS = ["titleAttractiveness", "informationQuality", "overallUsability"]


def run_evaluation(
    dataset_path: Path,
    output_path: Path,
    *,
    experiment_mode: str = "SMOKE_FAKE",
    max_revisions: int = 1,
) -> dict[str, Any]:
    samples = load_dataset(dataset_path)
    if experiment_mode == "SMOKE_FAKE":
        model = None
    elif (
        experiment_mode == "FORMAL_LIVE_MODEL"
        and os.getenv("LLM_ENABLED", "false").lower() == "true"
    ):
        model = build_copy_seo_model()
    else:
        raise ValueError("FORMAL_LIVE_MODEL requires LLM_ENABLED=true and model credentials")
    rows: list[dict[str, Any]] = []
    total_runs = len(samples) * 3
    for sample in samples:
        product = sample.to_product()
        strategies = build_strategies(product, model, max_revisions)
        for strategy_name, strategy in strategies.items():
            started = time.perf_counter()
            try:
                result = strategy.generate(product, "US", "en")  # type: ignore[attr-defined]
                trace = (
                    result.trace.model_dump(by_alias=True, mode="json") if result.trace else None
                )
                rows.append(
                    {
                        "caseId": sample.case_id,
                        "strategy": strategy_name,
                        "success": True,
                        "latencyMs": int((time.perf_counter() - started) * 1000),
                        "modelAttempts": result.trace.model_attempts if result.trace else 1,
                        "automaticScores": automatic_scores(sample, result.content),
                        "compliancePassed": _compliance_passed(product, result.content),
                        "humanScores": {dimension: None for dimension in HUMAN_DIMENSIONS},
                        "content": result.content.model_dump(by_alias=True, mode="json"),
                        "trace": trace,
                        "errorType": None,
                    }
                )
            except Exception as error:
                trace = error.trace if isinstance(error, CopySeoAgentError) else None
                rows.append(
                    {
                        "caseId": sample.case_id,
                        "strategy": strategy_name,
                        "success": False,
                        "latencyMs": int((time.perf_counter() - started) * 1000),
                        "modelAttempts": trace.model_attempts if trace else 0,
                        "automaticScores": None,
                        "compliancePassed": False,
                        "humanScores": {dimension: None for dimension in HUMAN_DIMENSIONS},
                        "content": None,
                        "trace": trace.model_dump(by_alias=True, mode="json") if trace else None,
                        "errorType": (
                            error.error_type
                            if isinstance(error, CopySeoAgentError)
                            else type(error).__name__
                        ),
                    }
                )
            print(
                f"[{len(rows)}/{total_runs}] {sample.case_id} {strategy_name}",
                flush=True,
            )
            _write_report(
                output_path,
                _build_report(samples, rows, experiment_mode, run_status="IN_PROGRESS"),
            )
    report = _build_report(samples, rows, experiment_mode, run_status="COMPLETE")
    _write_report(output_path, report)
    return report


def _build_report(
    samples: list[Any],
    rows: list[dict[str, Any]],
    experiment_mode: str,
    *,
    run_status: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": "copy-seo-eval-v1",
        "runStatus": run_status,
        "experimentMode": experiment_mode,
        "researchConclusionAllowed": experiment_mode == "FORMAL_LIVE_MODEL",
        "warning": (
            "Fake model smoke run: validates the evaluation pipeline only; "
            "it must not be used as thesis evidence."
            if experiment_mode != "FORMAL_LIVE_MODEL"
            else None
        ),
        "generatedAt": datetime.now(UTC).isoformat(),
        "dataset": {"name": "copy-seo-v1", "sampleCount": len(samples)},
        "summary": _summarize(rows),
        "results": rows,
    }


def _write_report(output_path: Path, report: dict[str, Any]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def _summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[row["strategy"]].append(row)
    summary = {}
    for strategy, values in grouped.items():
        successful = [value for value in values if value["success"]]
        score_rows = [value["automaticScores"] for value in successful]
        dimensions = score_rows[0].keys() if score_rows else []
        summary[strategy] = {
            "sampleCount": len(values),
            "successRate": round(len(successful) / len(values), 4),
            "averageLatencyMs": round(sum(value["latencyMs"] for value in values) / len(values), 2),
            "averageAutomaticScores": {
                dimension: round(
                    sum(scores[dimension] for scores in score_rows) / len(score_rows), 2
                )
                for dimension in dimensions
            },
            "compliancePassRate": round(
                sum(value["compliancePassed"] for value in values) / len(values), 4
            ),
            "humanScoringComplete": False,
        }
    return summary


def _compliance_passed(product: Any, content: Any) -> bool:
    return RuleBasedContentValidator().validate(content, product).passed
