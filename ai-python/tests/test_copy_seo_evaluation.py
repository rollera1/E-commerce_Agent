import json
from pathlib import Path

import pytest

from ecommerce_agent_ai.evaluation.copy_seo import runner
from ecommerce_agent_ai.evaluation.copy_seo.dataset import load_dataset
from ecommerce_agent_ai.evaluation.copy_seo.runner import run_evaluation
from ecommerce_agent_ai.evaluation.copy_seo.strategies import TemplateBaseline

DATASET = Path(__file__).parents[1] / "evaluation" / "copy_seo" / "datasets" / "copy-seo-v1.json"


def test_dataset_contains_twenty_unique_synthetic_samples() -> None:
    samples = load_dataset(DATASET)

    assert len(samples) == 20
    assert len({sample.case_id for sample in samples}) == 20
    assert all("example.invalid" in sample.to_product().source_url for sample in samples)


def test_smoke_evaluation_runs_all_strategies_on_same_cases(tmp_path: Path) -> None:
    output = tmp_path / "smoke.json"

    report = run_evaluation(DATASET, output)

    assert report["experimentMode"] == "SMOKE_FAKE"
    assert report["researchConclusionAllowed"] is False
    assert "must not be used as thesis evidence" in report["warning"]
    assert len(report["results"]) == 60
    expected_ids = {f"case-{number:02}" for number in range(1, 21)}
    for strategy in ["template_baseline", "single_pass", "tool_agent"]:
        rows = [row for row in report["results"] if row["strategy"] == strategy]
        assert {row["caseId"] for row in rows} == expected_ids
        assert report["summary"][strategy]["sampleCount"] == 20
        assert 0 <= report["summary"][strategy]["compliancePassRate"] <= 1
    saved = json.loads(output.read_text(encoding="utf-8"))
    assert saved["schemaVersion"] == "copy-seo-eval-v1"
    assert all(score is None for row in saved["results"] for score in row["humanScores"].values())


def test_formal_mode_rejects_disabled_live_model(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "false")

    with pytest.raises(ValueError, match="requires LLM_ENABLED=true"):
        run_evaluation(DATASET, tmp_path / "formal.json", experiment_mode="FORMAL_LIVE_MODEL")


def test_network_failure_is_recorded_without_aborting_the_batch(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    class NetworkFailureStrategy:
        def generate(self, product, target_market, language):
            del product, target_market, language
            raise ConnectionError("connection reset")

    def strategies(product, model, max_revisions):
        del model, max_revisions
        return {
            "template_baseline": TemplateBaseline(),
            "single_pass": NetworkFailureStrategy(),
            "tool_agent": TemplateBaseline(),
        }

    monkeypatch.setattr(runner, "build_strategies", strategies)
    output = tmp_path / "network-failure.json"

    report = run_evaluation(DATASET, output)

    assert output.exists()
    assert len(report["results"]) == 60
    failures = [row for row in report["results"] if not row["success"]]
    assert len(failures) == 20
    assert {row["errorType"] for row in failures} == {"ConnectionError"}
