import json
import random
from pathlib import Path
from typing import Any


def create_blind_review(
    report_path: Path,
    review_path: Path,
    key_path: Path,
    *,
    seed: int = 20261003,
) -> None:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    successful = [row for row in report["results"] if row["success"]]
    random.Random(seed).shuffle(successful)

    lines = [
        "# 文案与 SEO 对照实验人工盲评表",
        "",
        "评分人：________________    日期：________________",
        "",
        "评分范围：1 分最低，5 分最高。请独立评价标题吸引力、信息质量和整体可用性。",
        "实验组信息已隐藏；生成失败的样本不进入主观质量评分，另由成功率指标体现。",
        "",
    ]
    key: list[dict[str, Any]] = []
    for index, row in enumerate(successful, start=1):
        review_id = f"R{index:03d}"
        content = row["content"]
        key.append(
            {
                "reviewId": review_id,
                "caseId": row["caseId"],
                "strategy": row["strategy"],
            }
        )
        lines.extend(
            [
                f"## {review_id}",
                "",
                f"- 商品标题：{content['title']}",
                f"- 卖点：{'；'.join(content['bulletPoints'])}",
                f"- 商品描述：{content['description']}",
                f"- SEO 标题：{content['seoTitle']}",
                f"- Meta Description：{content['metaDescription']}",
                f"- 标签：{'、'.join(content['tags'])}",
                "- 标题吸引力（1—5）：____",
                "- 信息质量（1—5）：____",
                "- 整体可用性（1—5）：____",
                "- 备注：",
                "",
            ]
        )

    review_path.write_text("\n".join(lines), encoding="utf-8")
    key_path.write_text(
        json.dumps(
            {
                "seed": seed,
                "successfulOutputCount": len(successful),
                "failedOutputCount": len(report["results"]) - len(successful),
                "mapping": key,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    base = Path(__file__).parent
    create_blind_review(
        base / "results" / "copy-seo-formal.json",
        base / "results" / "copy-seo-human-review.md",
        base / "results" / "copy-seo-human-review-key.json",
    )
