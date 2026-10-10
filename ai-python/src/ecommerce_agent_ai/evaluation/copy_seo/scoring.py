import re

from ecommerce_agent_ai.agents.copy_seo.models import CopySeoContent
from ecommerce_agent_ai.evaluation.copy_seo.dataset import EvaluationSample


def automatic_scores(sample: EvaluationSample, content: CopySeoContent) -> dict[str, float]:
    text = " ".join(
        [
            content.title,
            *content.bullet_points,
            content.description,
            content.seo_title,
            content.meta_description,
            *content.tags,
        ]
    ).casefold()
    source_terms = set(_terms(f"{sample.title} {sample.description}"))
    output_terms = set(_terms(text))
    keyword_hits = sum(keyword.casefold() in text for keyword in sample.expected_keywords)
    completeness = (
        sum(
            bool(value)
            for value in [
                content.title,
                content.bullet_points,
                content.description,
                content.seo_title,
                content.meta_description,
                content.tags,
            ]
        )
        / 6
    )
    return {
        "informationCompleteness": round(completeness * 5, 2),
        "factualConsistency": round(
            len(source_terms & output_terms) / max(1, len(source_terms)) * 5, 2
        ),
        "seoCoverage": round(keyword_hits / len(sample.expected_keywords) * 5, 2),
        "readability": _readability(content.description),
    }


def _terms(value: str) -> list[str]:
    return [term for term in re.findall(r"[a-z0-9]+", value.casefold()) if len(term) > 2]


def _readability(value: str) -> float:
    words = _terms(value)
    average = len(words) / max(1, len(re.findall(r"[.!?]+", value)))
    if 8 <= average <= 24:
        return 5.0
    if 5 <= average <= 32:
        return 4.0
    if 3 <= average <= 40:
        return 3.0
    return 2.0
