#!/usr/bin/env python3
"""Estimate character distribution for Claune agent run context.

This reads run artifact directories containing agent-messages.json and reports
how much message content is user input, assistant text, assistant tool calls,
tool results, or other records. It intentionally uses character counts rather
than tokenization.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


CATEGORIES = ("user_input", "model_text", "model_tool_call", "tool_result", "other")


@dataclass
class RunCounts:
    run_id: str
    counts: dict[str, int]

    @property
    def total(self) -> int:
        return sum(self.counts.values())


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def text_from_content(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        chunks: list[str] = []
        for item in content:
            if isinstance(item, dict):
                item_type = item.get("type")
                if item_type == "text":
                    chunks.append(str(item.get("text", "")))
                else:
                    chunks.append(compact_json(item))
            else:
                chunks.append(str(item))
        return "\n".join(chunk for chunk in chunks if chunk)
    return compact_json(content)


def classify_message(message: dict[str, Any]) -> dict[str, int]:
    counts = {category: 0 for category in CATEGORIES}
    message_type = message.get("type")
    payload = message.get("payload") or {}
    role = payload.get("role") or message.get("role")

    if message_type == "user" or role == "user":
        counts["user_input"] += len(text_from_content(payload.get("content") or message.get("content")))
        return counts

    if message_type == "assistant" or role == "assistant":
        content = payload.get("content") or message.get("content") or []
        if not isinstance(content, list):
            counts["model_text"] += len(text_from_content(content))
            return counts
        for item in content:
            if not isinstance(item, dict):
                counts["model_text"] += len(str(item))
                continue
            item_type = item.get("type")
            if item_type == "text":
                counts["model_text"] += len(str(item.get("text", "")))
            elif item_type == "tool_call":
                counts["model_tool_call"] += len(
                    compact_json(
                        {
                            "id": item.get("id"),
                            "name": item.get("name"),
                            "arguments": item.get("arguments"),
                        },
                    ),
                )
            else:
                counts["other"] += len(compact_json(item))
        return counts

    if message_type == "tool_result" or role == "toolResult":
        content_text = text_from_content(payload.get("content") or message.get("content"))
        if content_text:
            counts["tool_result"] += len(content_text)
        else:
            counts["tool_result"] += len(compact_json(payload.get("details") or payload or message))
        return counts

    counts["other"] += len(compact_json(message))
    return counts


def run_id_for(run_dir: Path) -> str:
    metadata_path = run_dir / "metadata.json"
    if metadata_path.exists():
        try:
            metadata = json.loads(metadata_path.read_text())
            run_id = metadata.get("runId")
            if isinstance(run_id, str) and run_id:
                return run_id
        except json.JSONDecodeError:
            pass
    return run_dir.name


def analyze_run(run_dir: Path) -> RunCounts:
    messages_path = run_dir / "agent-messages.json"
    if not messages_path.exists():
        raise FileNotFoundError(f"{run_dir} does not contain agent-messages.json")
    messages = json.loads(messages_path.read_text())
    counts = {category: 0 for category in CATEGORIES}
    for message in messages:
        if not isinstance(message, dict):
            counts["other"] += len(str(message))
            continue
        message_counts = classify_message(message)
        for category, value in message_counts.items():
            counts[category] += value
    model_input_path = run_dir / "model-input.txt"
    if counts["user_input"] == 0 and model_input_path.exists():
        counts["user_input"] += len(model_input_path.read_text())
    return RunCounts(run_id=run_id_for(run_dir), counts=counts)


def print_run(counts: RunCounts) -> None:
    print(f"\n{counts.run_id}")
    print("-" * len(counts.run_id))
    total = counts.total
    for category in CATEGORIES:
        value = counts.counts[category]
        percent = (value / total * 100) if total else 0.0
        print(f"{category:16} {value:10d} {percent:6.2f}%")
    print(f"{'total':16} {total:10d} 100.00%")


def print_aggregate(runs: list[RunCounts]) -> None:
    aggregate = {category: 0 for category in CATEGORIES}
    for run in runs:
        for category, value in run.counts.items():
            aggregate[category] += value
    print_run(RunCounts(run_id="aggregate", counts=aggregate))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_dirs", nargs="+", type=Path, help="Run artifact directories")
    args = parser.parse_args()

    runs = [analyze_run(run_dir) for run_dir in args.run_dirs]
    for run in runs:
        print_run(run)
    if len(runs) > 1:
        print_aggregate(runs)


if __name__ == "__main__":
    main()
