#!/usr/bin/env python3
"""Summarize Claune Android run artifacts for API/prompt efficiency analysis."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any


DEBUG_HOST_CALLS = {
    "observeScreen",
    "diffScreen",
    "inspectScreen",
    "findRawNodes",
    "tapRef",
    "tapPoint",
    "tapBounds",
    "scrollRef",
    "scrollScreen",
}

SUPPORTED_DISCOVERY_CALLS = {
    "locatorQuery",
    "locatorCount",
    "locatorDescribe",
    "locatorIsVisible",
    "locatorIsHidden",
    "locatorAllTextContents",
    "locatorTextContent",
}


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text())


def latest_run_dir(root: Path) -> Path:
    candidates = [path for path in root.iterdir() if path.is_dir()]
    if not candidates:
        raise SystemExit(f"No run directories found under {root}")
    return sorted(candidates, key=lambda path: path.name)[-1]


def parse_result(record: dict[str, Any]) -> dict[str, Any]:
    raw = record.get("resultJson")
    if not isinstance(raw, str):
        return {}
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def parse_json_object(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, str):
        return {}
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def walk_json(value: Any):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk_json(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_json(child)


def extract_tool_counts(agent_messages: Any) -> Counter[str]:
    counts: Counter[str] = Counter()
    if isinstance(agent_messages, list):
        for message in agent_messages:
            if not isinstance(message, dict) or message.get("type") != "assistant":
                continue
            payload = message.get("payload")
            if not isinstance(payload, dict):
                continue
            for item in payload.get("content", []):
                if isinstance(item, dict) and item.get("type") == "tool_call":
                    name = item.get("name")
                    if isinstance(name, str):
                        counts[name] += 1
    return counts


def count_bash_debug_usage(agent_messages: Any) -> Counter[str]:
    counts: Counter[str] = Counter()
    pattern = re.compile(r"claune\.debug\.([A-Za-z0-9_]+)")
    if isinstance(agent_messages, list):
        nodes = (
            item
            for message in agent_messages
            if isinstance(message, dict) and message.get("type") == "assistant"
            for item in (message.get("payload") or {}).get("content", [])
            if isinstance(item, dict) and item.get("type") == "tool_call"
        )
        for node in nodes:
            arguments = node.get("arguments")
            command = arguments.get("command") if isinstance(arguments, dict) else None
            if not isinstance(command, str):
                continue
            for match in pattern.findall(command):
                counts[f"claune.debug.{match}"] += 1
    return counts


def extract_file_tool_paths(agent_messages: Any, agent_events: Any) -> dict[str, Counter[str]]:
    paths = {"read": Counter(), "write": Counter(), "edit": Counter()}
    if isinstance(agent_events, list) and agent_events:
        for event in agent_events:
            if not isinstance(event, dict) or event.get("type") != "tool_call":
                continue
            payload = event.get("payload")
            if not isinstance(payload, dict):
                continue
            nested = payload.get("payload")
            if not isinstance(nested, dict):
                continue
            record_file_tool_path(paths, nested.get("toolName"), nested.get("arguments"))
        return paths
    if isinstance(agent_messages, list):
        for message in agent_messages:
            if not isinstance(message, dict) or message.get("type") != "assistant":
                continue
            payload = message.get("payload")
            if not isinstance(payload, dict):
                continue
            for item in payload.get("content", []):
                if not isinstance(item, dict) or item.get("type") != "tool_call":
                    continue
                record_file_tool_path(paths, item.get("name"), item.get("arguments"))
    return paths


def record_file_tool_path(paths: dict[str, Counter[str]], name: Any, arguments: Any) -> None:
    if name not in paths or not isinstance(arguments, dict):
        return
    path = arguments.get("path")
    if isinstance(path, str):
        paths[name][path] += 1


def count_text_mentions(agent_messages: Any, pattern: str) -> int:
    if not isinstance(agent_messages, list):
        return 0
    regex = re.compile(pattern, re.IGNORECASE)
    count = 0
    for message in agent_messages:
        if not isinstance(message, dict):
            continue
        payload = message.get("payload")
        if not isinstance(payload, dict):
            continue
        for item in payload.get("content", []):
            if isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str):
                    count += len(regex.findall(text))
    return count


def extract_bash_commands(agent_messages: Any) -> list[str]:
    commands: list[str] = []
    if not isinstance(agent_messages, list):
        return commands
    for message in agent_messages:
        if not isinstance(message, dict) or message.get("type") != "assistant":
            continue
        payload = message.get("payload")
        if not isinstance(payload, dict):
            continue
        for item in payload.get("content", []):
            if not isinstance(item, dict) or item.get("type") != "tool_call" or item.get("name") != "bash":
                continue
            arguments = item.get("arguments")
            command = arguments.get("command") if isinstance(arguments, dict) else None
            if isinstance(command, str):
                commands.append(command)
    return commands


def warning_counts(host_calls: Any, agent_messages: Any) -> Counter[str]:
    warnings: Counter[str] = Counter()
    if isinstance(host_calls, list):
        by_script: dict[str, list[dict[str, Any]]] = {}
        for call in host_calls:
            if isinstance(call, dict):
                by_script.setdefault(str(call.get("scriptExecutionId")), []).append(call)
        for calls in by_script.values():
            previous: dict[str, Any] | None = None
            for call in calls:
                if previous and previous.get("name") == "launchApp" and call.get("name") == "waitForState":
                    launch_args = parse_json_object(previous.get("argumentsJson"))
                    wait_args = parse_json_object(call.get("argumentsJson"))
                    if (
                        wait_args.get("type") == "package"
                        and launch_args.get("packageName") == wait_args.get("value")
                    ):
                        warnings["redundant_launch_wait_pair"] += 1
                previous = call
    for command in extract_bash_commands(agent_messages):
        if re.search(r"\bfor\s*\([^)]*\)\s*\{[^}]*\.nth\([^)]*\)\.textContent\(", command, re.S):
            warnings["per_item_nth_text_content_loop"] += 1
        elif ".nth(" in command and ".textContent(" in command:
            warnings["nth_text_content_usage"] += 1
    return warnings


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "run_dir",
        nargs="?",
        type=Path,
        help="Run artifact directory. Defaults to latest under artifacts/latest-run-analysis.",
    )
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON.")
    args = parser.parse_args()

    run_dir = args.run_dir or latest_run_dir(Path("artifacts/latest-run-analysis"))
    host_calls = load_json(run_dir / "host-calls.json", [])
    agent_messages = load_json(run_dir / "agent-messages.json", [])
    agent_events = load_json(run_dir / "agent-events.json", [])
    metadata = load_json(run_dir / "metadata.json", {})
    reflection_output_path = run_dir / "memory-reflection-output.txt"
    reflection_output = reflection_output_path.read_text() if reflection_output_path.exists() else ""

    host_names = Counter(call.get("name", "<unknown>") for call in host_calls)
    host_categories = Counter(call.get("category", "<unknown>") for call in host_calls)
    debug_calls = Counter(
        call.get("name", "<unknown>") for call in host_calls if call.get("name") in DEBUG_HOST_CALLS
    )
    supported_discovery = Counter(
        call.get("name", "<unknown>") for call in host_calls if call.get("name") in SUPPORTED_DISCOVERY_CALLS
    )
    errors = []
    trace_tags: Counter[str] = Counter()
    system_ui_transitions = 0
    memory_paths: Counter[str] = Counter()
    file_tool_paths = extract_file_tool_paths(agent_messages, agent_events)
    memory_file_writes: Counter[str] = Counter()
    for tool in ("write", "edit"):
        for path, count in file_tool_paths[tool].items():
            if path.startswith("/work/memory/"):
                memory_file_writes[path] += count
    durable_reflection_without_write = bool(
        reflection_output
        and not memory_file_writes
        and (
            re.search(r"durable learning found", reflection_output, re.IGNORECASE)
            or re.search(r"memory_updated", reflection_output, re.IGNORECASE)
        )
    )

    previous_package: str | None = None
    for call in host_calls:
        result = parse_result(call)
        if result.get("ok") is False:
            errors.append(
                {
                    "name": call.get("name"),
                    "errorCode": result.get("errorCode"),
                    "message": result.get("message"),
                    "startedAt": call.get("startedAt"),
                }
            )
        data = result.get("data")
        if isinstance(data, dict):
            for tag in data.get("traceTags", []) if isinstance(data.get("traceTags"), list) else []:
                if isinstance(tag, str):
                    trace_tags[tag] += 1
            package = data.get("foregroundPackage")
            if isinstance(package, str):
                if previous_package != "com.android.systemui" and package == "com.android.systemui":
                    system_ui_transitions += 1
                previous_package = package
        args_json = call.get("argumentsJson")
        if isinstance(args_json, str) and "/work/memory/" in args_json:
            memory_paths.update(re.findall(r"/work/memory/[A-Za-z0-9_./-]+", args_json))

    report = {
        "runDir": str(run_dir),
        "runId": metadata.get("runId") or run_dir.name,
        "hostCallCount": len(host_calls),
        "hostCounts": dict(host_names),
        "hostCategories": dict(host_categories),
        "toolCounts": dict(extract_tool_counts(agent_messages)),
        "debugHostCount": sum(debug_calls.values()),
        "debugHostCounts": dict(debug_calls),
        "bashDebugMentions": dict(count_bash_debug_usage(agent_messages)),
        "supportedDiscoveryCounts": dict(supported_discovery),
        "errors": errors,
        "traceTags": dict(trace_tags),
        "systemUiTransitionsFromHostData": system_ui_transitions,
        "memoryPathsMentionedInHostArgs": dict(memory_paths),
        "fileToolPaths": {name: dict(counter) for name, counter in file_tool_paths.items()},
        "memoryFileWrites": dict(memory_file_writes),
        "durableReflectionWithoutWrite": durable_reflection_without_write,
        "apiGapMentions": count_text_mentions(agent_messages, r"/work/outputs/api-gaps\.md|api gap|api-gap"),
        "warningCounts": dict(warning_counts(host_calls, agent_messages)),
    }

    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
        return

    print(f"Run: {report['runId']} ({report['runDir']})")
    print(f"Host calls: {report['hostCallCount']}  categories={report['hostCategories']}")
    print(f"Tools: {report['toolCounts']}")
    print(f"Debug host calls: {report['debugHostCount']} {report['debugHostCounts']}")
    print(f"Supported discovery: {report['supportedDiscoveryCounts']}")
    print(f"Trace tags: {report['traceTags']}")
    print(f"System UI transitions from host data: {report['systemUiTransitionsFromHostData']}")
    print(f"Memory writes: {report['memoryFileWrites']}")
    print(f"Durable reflection without write: {report['durableReflectionWithoutWrite']}")
    print(f"API gap mentions: {report['apiGapMentions']}")
    print(f"Warnings: {report['warningCounts']}")
    if errors:
        print("Errors:")
        for error in errors[:20]:
            print(f"- {error['name']} {error['errorCode']}: {error['message']}")
    if report["memoryPathsMentionedInHostArgs"]:
        print(f"Memory paths: {report['memoryPathsMentionedInHostArgs']}")


if __name__ == "__main__":
    main()
