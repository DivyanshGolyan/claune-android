#!/usr/bin/env python3
"""Replay persisted ScreenState artifacts through deployable projection variants.

The variants here are intentionally generic: they use only accessibility and
layout signals that the Android app can compute at runtime. They should not
encode ride-booking/domain-specific text rules.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


MAX_DIFF_CHARS = 3500
MAX_COMPACT_CHARS = 6000
LARGE_DIFF_RATIO = 0.35
BOUNDS_QUANTUM = 4

@dataclass
class Stats:
    additions: int
    removals: int
    unchanged: int
    before_line_count: int
    after_line_count: int
    change_ratio: float


@dataclass
class ProjectionResult:
    variant: str
    mode: str
    reason: str
    text: str
    stats: Stats


def normalized_text(value: str | None) -> str:
    if not value:
        return ""
    return (
        re.sub(r"\s+", " ", value)
        .replace("\ufeff", "")
        .replace("\u200b", "")
        .replace("\u200c", "")
        .replace("\u200d", "")
        .replace("\u2060", "")
        .strip()
    )


def quote(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def path_key(path: list[int]) -> str:
    return "_".join(str(part) for part in path) or "root"


def quantized_bounds(bounds: list[int]) -> str:
    return "[" + ",".join(str(((value + BOUNDS_QUANTUM // 2) // BOUNDS_QUANTUM) * BOUNDS_QUANTUM) for value in bounds) + "]"


def flatten(node: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not node:
        return []
    nodes: list[dict[str, Any]] = []

    def visit(current: dict[str, Any]) -> None:
        nodes.append(current)
        for child in current.get("children") or []:
            if isinstance(child, dict):
                visit(child)

    visit(node)
    return nodes


def is_visible(node: dict[str, Any]) -> bool:
    return bool(node.get("visibleToUser"))


def has_meaningful_label(node: dict[str, Any]) -> bool:
    return any(normalized_text(node.get(field)) for field in ("label", "text", "contentDescription", "resourceId"))


def is_actionable(node: dict[str, Any]) -> bool:
    return is_visible(node) and (
        bool(node.get("clickable"))
        or bool(node.get("focusable"))
        or bool(node.get("editable"))
        or bool(node.get("scrollable"))
        or bool(node.get("actions"))
    )


def compact_flags(node: dict[str, Any]) -> list[str]:
    flags: list[str] = []
    if node.get("clickable"):
        flags.append("click")
    if node.get("editable"):
        flags.append("edit")
    if node.get("scrollable"):
        flags.append("scroll")
    if node.get("focusable"):
        flags.append("focusable")
    if node.get("enabled") is False:
        flags.append("disabled")
    if node.get("checked"):
        flags.append("checked")
    if node.get("selected"):
        flags.append("selected")
    if node.get("tapFallbackEligible") and not node.get("clickable"):
        flags.append("tapFallback")
    return flags


def ancestors(path: list[int]) -> Iterable[str]:
    for index in range(len(path) + 1):
        yield path_key(path[:index])


def keep_paths(root: dict[str, Any] | None, strategy: str) -> set[str]:
    keep: set[str] = set()
    for node in flatten(root):
        path = node.get("path") or []
        if not isinstance(path, list):
            continue
        should_keep = False
        if strategy == "current":
            should_keep = is_visible(node) and (is_actionable(node) or has_meaningful_label(node))
        elif strategy == "actionable_only":
            should_keep = is_visible(node) and is_actionable(node)
        else:
            raise ValueError(f"unknown keep strategy: {strategy}")
        if should_keep:
            keep.update(ancestors(path))
    return keep


def render_node(node: dict[str, Any], keep: set[str], lines: list[str]) -> None:
    path = node.get("path") or []
    key = path_key(path)
    if key in keep:
        parts = [
            f"{'  ' * len(path)}- path={key}",
            f"role={node.get('role', '')}",
            f"ref={node.get('ref', '')}",
        ]
        for output_name, field in (
            ("label", "label"),
            ("text", "text"),
            ("desc", "contentDescription"),
            ("rid", "resourceId"),
        ):
            value = normalized_text(node.get(field))
            if value:
                parts.append(f"{output_name}={quote(value)}")
        class_name = normalized_text(node.get("className"))
        if class_name:
            parts.append(f"class={quote(class_name.split('.')[-1])}")
        flags = compact_flags(node)
        if flags:
            parts.append("flags=" + ",".join(flags))
        bounds = node.get("bounds")
        if isinstance(bounds, list) and len(bounds) == 4:
            parts.append("bounds=" + quantized_bounds(bounds))
        lines.append(" ".join(parts))
    for child in node.get("children") or []:
        if isinstance(child, dict):
            render_node(child, keep, lines)


def selected_window(state: dict[str, Any]) -> dict[str, Any] | None:
    for window in state.get("windows") or []:
        if isinstance(window, dict) and window.get("selected"):
            return window
    return None


def selected_window_key(state: dict[str, Any]) -> str:
    window = selected_window(state)
    if not window:
        return ""
    return f"{window.get('packageName')}|{window.get('type')}|{window.get('layer')}|{window.get('bounds')}"


def canonical_lines(
    state: dict[str, Any],
    *,
    strategy: str = "current",
    include_snapshot_id: bool = False,
    include_window_reason: bool = True,
) -> list[str]:
    first = "screen-v1"
    if include_snapshot_id:
        first += f" snapshot={state.get('snapshotId')}"
    first += f" package={state.get('foregroundPackage')}"
    lines = [first]
    window = selected_window(state)
    if window:
        line = f"window selected package={window.get('packageName')} type={window.get('type')} layer={window.get('layer')}"
        visible_text = " | ".join(normalized_text(text) for text in (window.get("visibleText") or [])[:3]).strip()
        if visible_text:
            line += f" text={quote(visible_text)}"
        lines.append(line)
    if include_window_reason and normalized_text(state.get("selectedWindowReason")):
        lines.append(f"windowReason={quote(normalized_text(state.get('selectedWindowReason')))}")
    lines.append("tree:")
    root = state.get("root")
    if isinstance(root, dict):
        render_node(root, keep_paths(root, strategy), lines)
    return lines


def bounds_area(node: dict[str, Any]) -> int:
    bounds = node.get("bounds")
    if not isinstance(bounds, list) or len(bounds) != 4:
        return 0
    return max(bounds[2] - bounds[0], 0) * max(bounds[3] - bounds[1], 0)


def bounds_top_left(node: dict[str, Any]) -> tuple[int, int]:
    bounds = node.get("bounds")
    if not isinstance(bounds, list) or len(bounds) != 4:
        return (0, 0)
    return (bounds[1], bounds[0])


def salience_score(node: dict[str, Any]) -> int:
    """Rank nodes using deployable accessibility/layout signals only."""
    if not is_visible(node):
        return -1
    score = 0
    if node.get("focused"):
        score += 120
    if node.get("editable"):
        score += 90
    if node.get("clickable"):
        score += 70
    if node.get("tapFallbackEligible") and not node.get("clickable"):
        score += 55
    if node.get("focusable"):
        score += 45
    if node.get("selected") or node.get("checked"):
        score += 35
    if node.get("scrollable"):
        score += 25
    if node.get("importantForAccessibility"):
        score += 20
    actions = node.get("actions") or []
    if isinstance(actions, list):
        score += min(len(actions), 5) * 5
    if normalized_text(node.get("contentDescription")):
        score += 18
    if normalized_text(node.get("text")):
        score += 14
    if normalized_text(node.get("label")):
        score += 10
    if normalized_text(node.get("resourceId")):
        score += 6
    class_name = normalized_text(node.get("className")).lower()
    if "button" in class_name:
        score += 18
    elif "edittext" in class_name:
        score += 18
    elif "textview" in class_name:
        score += 6
    area = bounds_area(node)
    if area > 0:
        score += min(area // 50_000, 20)
    if node.get("enabled") is False:
        score -= 25
    return score


def render_salient_node(node: dict[str, Any], score: int) -> str:
    path = node.get("path") or []
    parts = [
        f"- score={score}",
        f"path={path_key(path)}",
        f"role={node.get('role', '')}",
        f"ref={node.get('ref', '')}",
    ]
    for output_name, field in (
        ("label", "label"),
        ("text", "text"),
        ("desc", "contentDescription"),
        ("rid", "resourceId"),
    ):
        value = normalized_text(node.get(field))
        if value:
            parts.append(f"{output_name}={quote(value)}")
    class_name = normalized_text(node.get("className"))
    if class_name:
        parts.append(f"class={quote(class_name.split('.')[-1])}")
    flags = compact_flags(node)
    if flags:
        parts.append("flags=" + ",".join(flags))
    bounds = node.get("bounds")
    if isinstance(bounds, list) and len(bounds) == 4:
        parts.append("bounds=" + quantized_bounds(bounds))
    return " ".join(parts)


def salient_lines(state: dict[str, Any], *, include_snapshot_id: bool = False, max_nodes: int = 36) -> list[str]:
    first = "screen-v1-salience"
    if include_snapshot_id:
        first += f" snapshot={state.get('snapshotId')}"
    first += f" package={state.get('foregroundPackage')}"
    lines = [first]
    window = selected_window(state)
    if window:
        line = f"window selected package={window.get('packageName')} type={window.get('type')} layer={window.get('layer')}"
        visible_text = " | ".join(normalized_text(text) for text in (window.get("visibleText") or [])[:3]).strip()
        if visible_text:
            line += f" text={quote(visible_text)}"
        lines.append(line)
    scored = [
        (salience_score(node), bounds_top_left(node), path_key(node.get("path") or []), node)
        for node in flatten(state.get("root") if isinstance(state.get("root"), dict) else None)
    ]
    scored = [entry for entry in scored if entry[0] >= 0 and (is_actionable(entry[3]) or has_meaningful_label(entry[3]))]
    scored.sort(key=lambda entry: (-entry[0], entry[1], entry[2]))
    lines.append("priority:")
    seen: set[str] = set()
    for score, _, _, node in scored:
        identity = "|".join(
            normalized_text(node.get(field))
            for field in ("label", "text", "contentDescription", "resourceId", "className")
        )
        if identity in seen:
            continue
        seen.add(identity)
        lines.append(render_salient_node(node, score))
        if len(lines) >= max_nodes + 3:
            break
    return lines


def limit_chars(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    suffix = f"\n[truncated at {max_chars} chars]"
    return text[: max(max_chars - len(suffix), 0)].rstrip() + suffix


def line_diff(before: list[str], after: list[str]) -> tuple[str, Stats, list[str]]:
    matcher = difflib.SequenceMatcher(a=before, b=after, autojunk=False)
    rendered: list[str] = []
    changed: list[str] = []
    additions = removals = unchanged = 0
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            unchanged += i2 - i1
            continue
        if tag in {"delete", "replace"}:
            for line in before[i1:i2]:
                rendered.append("- " + line)
                changed.append("- " + line)
                removals += 1
        if tag in {"insert", "replace"}:
            for line in after[j1:j2]:
                rendered.append("+ " + line)
                changed.append("+ " + line)
                additions += 1
    denominator = max(max(len(before), len(after)), 1)
    stats = Stats(
        additions=additions,
        removals=removals,
        unchanged=unchanged,
        before_line_count=len(before),
        after_line_count=len(after),
        change_ratio=(additions + removals) / denominator,
    )
    return "\n".join(rendered), stats, changed


def reason_for(previous: dict[str, Any] | None, current: dict[str, Any], stats: Stats, diff_text: str) -> str:
    if previous is None:
        return "baseline_missing"
    if previous.get("foregroundPackage") != current.get("foregroundPackage"):
        return "package_changed"
    if selected_window_key(previous) != selected_window_key(current):
        return "window_changed"
    if stats.change_ratio >= LARGE_DIFF_RATIO:
        return "large_diff"
    if len(diff_text) > MAX_DIFF_CHARS:
        return "diff_too_large"
    return "small_diff"


def observation_header(current: dict[str, Any], reason: str, stats: Stats) -> str:
    return (
        f"screen-observation mode=summary reason={reason} snapshot={current.get('snapshotId')} "
        f"package={current.get('foregroundPackage')} additions={stats.additions} removals={stats.removals} "
        f"unchanged={stats.unchanged} changeRatio={stats.change_ratio:.2f}"
    )


def project(
    previous: dict[str, Any] | None,
    current: dict[str, Any],
    *,
    variant: str,
) -> ProjectionResult:
    salience_variant = variant.startswith("salience")
    before_lines = (
        salient_lines(previous) if previous and salience_variant else
        canonical_lines(previous, strategy="current") if previous else
        []
    )
    after_lines = salient_lines(current) if salience_variant else canonical_lines(current, strategy="current")
    diff_text, stats, changed = line_diff(before_lines, after_lines)
    reason = reason_for(previous, current, stats, diff_text)

    if previous is None:
        text = "\n".join(
            salient_lines(current, include_snapshot_id=True) if salience_variant else
            canonical_lines(current, strategy="current", include_snapshot_id=True),
        )
        return ProjectionResult(variant, "compact_snapshot", reason, limit_chars(text, MAX_COMPACT_CHARS), stats)

    if variant == "current":
        send_snapshot = reason != "small_diff"
        if send_snapshot:
            text = "\n".join(canonical_lines(current, strategy="current", include_snapshot_id=True))
            return ProjectionResult(variant, "compact_snapshot", reason, limit_chars(text, MAX_COMPACT_CHARS), stats)
        return ProjectionResult(variant, "diff", reason, limit_chars(diff_text, MAX_DIFF_CHARS), stats)

    if variant == "diff_first":
        return ProjectionResult(variant, "diff", reason, limit_chars(diff_text, MAX_DIFF_CHARS), stats)

    if variant == "summary_top20":
        text = "\n".join([observation_header(current, reason, stats), *changed[:20]])
        return ProjectionResult(variant, "summary", reason, limit_chars(text, 2500), stats)

    if variant == "salience_snapshot":
        send_snapshot = reason in {"baseline_missing", "package_changed", "window_changed"}
        if send_snapshot:
            text = "\n".join(salient_lines(current, include_snapshot_id=True))
            return ProjectionResult(variant, "compact_snapshot", reason, limit_chars(text, 4000), stats)
        text = "\n".join([observation_header(current, reason, stats), *changed[:24]])
        return ProjectionResult(variant, "summary_diff", reason, limit_chars(text, 3000), stats)

    if variant == "salience_summary":
        text = "\n".join([observation_header(current, reason, stats), *salient_lines(current, max_nodes=24)[1:]])
        return ProjectionResult(variant, "summary", reason, limit_chars(text, 3500), stats)

    if variant == "metadata_only":
        return ProjectionResult(variant, "summary", reason, observation_header(current, reason, stats), stats)

    raise ValueError(f"unknown variant: {variant}")


def load_states(run_dir: Path) -> list[dict[str, Any]]:
    path = run_dir / "screen-states.json"
    if not path.exists():
        raise FileNotFoundError(f"{path} does not exist")
    states = json.loads(path.read_text())
    if not isinstance(states, list):
        raise ValueError(f"{path} is not a JSON array")
    return [state for state in states if isinstance(state, dict)]


def actual_tool_result_summary(run_dir: Path) -> dict[str, float] | None:
    path = run_dir / "agent-messages.json"
    if not path.exists():
        return None
    messages = json.loads(path.read_text())
    lengths = [
        len((((message.get("payload") or {}).get("content") or [{}])[0]).get("text") or "")
        for message in messages
        if isinstance(message, dict) and message.get("type") == "tool_result"
    ]
    if not lengths:
        return None
    return {
        "count": len(lengths),
        "total": sum(lengths),
        "avg": sum(lengths) / len(lengths),
        "max": max(lengths),
    }


def run_variant(states: list[dict[str, Any]], variant: str) -> dict[str, Any]:
    previous: dict[str, Any] | None = None
    results: list[ProjectionResult] = []
    for current in states:
        result = project(previous, current, variant=variant)
        results.append(result)
        previous = current
    lengths = [len(result.text) for result in results]
    total_lines = sum(len(result.text.splitlines()) for result in results)
    current_line_count = sum(len(canonical_lines(state, strategy="current")) for state in states)
    return {
        "variant": variant,
        "captures": len(results),
        "total_chars": sum(lengths),
        "avg_chars": sum(lengths) / len(lengths) if lengths else 0,
        "max_chars": max(lengths) if lengths else 0,
        "snapshots": sum(1 for result in results if result.mode == "compact_snapshot"),
        "diffs": sum(1 for result in results if result.mode == "diff"),
        "summaries": sum(1 for result in results if result.mode not in {"compact_snapshot", "diff"}),
        "lines": total_lines,
        "line_ratio": total_lines / current_line_count if current_line_count else 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_dir", type=Path)
    parser.add_argument(
        "--variant",
        action="append",
        choices=[
            "current",
            "diff_first",
            "summary_top20",
            "salience_snapshot",
            "salience_summary",
            "metadata_only",
        ],
    )
    args = parser.parse_args()

    variants = args.variant or [
        "current",
        "diff_first",
        "summary_top20",
        "salience_snapshot",
        "salience_summary",
        "metadata_only",
    ]
    states = load_states(args.run_dir)
    actual = actual_tool_result_summary(args.run_dir)
    if actual:
        print(
            "actual_tool_results\t"
            f"count={actual['count']:.0f}\ttotal={actual['total']:.0f}\t"
            f"avg={actual['avg']:.1f}\tmax={actual['max']:.0f}",
        )
    print("variant\tcaptures\ttotal_chars\tavg_chars\tmax_chars\tsnapshots\tdiffs\tsummaries\tlines\tline_ratio")
    for variant in variants:
        row = run_variant(states, variant)
        print(
            f"{row['variant']}\t{row['captures']}\t{row['total_chars']}\t{row['avg_chars']:.1f}\t"
            f"{row['max_chars']}\t{row['snapshots']}\t{row['diffs']}\t{row['summaries']}\t"
            f"{row['lines']}\t{row['line_ratio']:.2f}",
        )


if __name__ == "__main__":
    main()
