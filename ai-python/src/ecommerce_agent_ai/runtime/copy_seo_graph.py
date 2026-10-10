from typing import Any, TypedDict

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph

from ecommerce_agent_ai.agents.copy_seo.models import CopySeoGeneration
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent


class _CopySeoState(TypedDict, total=False):
    source_product_id: str
    target_market: str
    language: str
    generation: dict[str, Any]


class DurableCopySeoGraph:
    """仅持久化任务输入和结构化产物，不保存模型隐藏推理。"""

    def __init__(self, agent: CopySeoAgent, checkpointer: BaseCheckpointSaver) -> None:
        self._agent = agent
        builder = StateGraph(_CopySeoState)
        builder.add_node("generate", self._generate)
        builder.add_edge(START, "generate")
        builder.add_edge("generate", END)
        self._graph = builder.compile(checkpointer=checkpointer)

    def generate(
        self,
        thread_id: str,
        source_product_id: str,
        target_market: str,
        language: str,
    ) -> CopySeoGeneration:
        config = {"configurable": {"thread_id": thread_id}}
        snapshot = self._graph.get_state(config)
        persisted = snapshot.values.get("generation")
        if persisted is not None:
            return CopySeoGeneration.model_validate(persisted)
        result = self._graph.invoke(
            {
                "source_product_id": source_product_id,
                "target_market": target_market,
                "language": language,
            },
            config,
        )
        return CopySeoGeneration.model_validate(result["generation"])

    def _generate(self, state: _CopySeoState) -> _CopySeoState:
        generation = self._agent.generate(
            state["source_product_id"],
            state["target_market"],
            state["language"],
        )
        return {"generation": generation.model_dump(mode="json", by_alias=True)}
