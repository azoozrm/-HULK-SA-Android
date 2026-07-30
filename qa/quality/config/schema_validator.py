#!/usr/bin/env python3
"""Small fail-closed JSON-Schema subset used before external dependencies exist."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


TYPE_MAP: dict[str, type | tuple[type, ...]] = {
    "object": dict,
    "array": list,
    "string": str,
    "integer": int,
    "number": (int, float),
    "boolean": bool,
    "null": type(None),
}


class SchemaError(ValueError):
    pass


def _matches_type(value: Any, expected: str) -> bool:
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    return isinstance(value, TYPE_MAP[expected])


def validate(value: Any, schema: dict[str, Any], path: str = "$") -> None:
    declared = schema.get("type")
    allowed = declared if isinstance(declared, list) else [declared] if declared else []
    if allowed and not any(_matches_type(value, item) for item in allowed):
        raise SchemaError(f"{path}: expected {allowed}, got {type(value).__name__}")
    if "enum" in schema and value not in schema["enum"]:
        raise SchemaError(f"{path}: {value!r} not in {schema['enum']!r}")
    if isinstance(value, (int, float)) and "minimum" in schema and value < schema["minimum"]:
        raise SchemaError(f"{path}: {value} is below {schema['minimum']}")
    if isinstance(value, str) and len(value) < schema.get("minLength", 0):
        raise SchemaError(f"{path}: string is shorter than {schema['minLength']}")
    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            raise SchemaError(f"{path}: expected at least {schema['minItems']} item(s)")
        item_schema = schema.get("items")
        if item_schema:
            for index, item in enumerate(value):
                validate(item, item_schema, f"{path}[{index}]")
    if isinstance(value, dict):
        required = set(schema.get("required", []))
        missing = sorted(required - value.keys())
        if missing:
            raise SchemaError(f"{path}: missing {missing}")
        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            unexpected = sorted(value.keys() - properties.keys())
            if unexpected:
                raise SchemaError(f"{path}: unexpected {unexpected}")
        for key, child_schema in properties.items():
            if key in value:
                validate(value[key], child_schema, f"{path}.{key}")


def validate_file(document: Path, schema_file: Path) -> dict[str, Any]:
    data = json.loads(document.read_text(encoding="utf-8"))
    schema = json.loads(schema_file.read_text(encoding="utf-8"))
    validate(data, schema)
    return data


def validate_matrix_contract(data: dict[str, Any]) -> None:
    ids = [profile["id"] for profile in data["profiles"]]
    if len(ids) != len(set(ids)):
        raise SchemaError("matrix: duplicate profile id")
    known = set(ids)
    for tier, selected in data["tiers"].items():
        unknown = set(selected) - known - {"*"}
        if unknown:
            raise SchemaError(f"matrix.tiers.{tier}: unknown profiles {sorted(unknown)}")
    for profile in data["profiles"]:
        width_dp = profile["width_px"] * 160 / profile["density_dpi"]
        if width_dp < 280:
            raise SchemaError(f"{profile['id']}: implausible Compose width {width_dp:.1f}dp")
        if profile["family"] == "tv" and profile["orientations"] != ["landscape"]:
            raise SchemaError(f"{profile['id']}: TV must be landscape-only")
        if profile["evidence_level"] == "simulation" and "simulation" not in profile["label"].lower():
            raise SchemaError(f"{profile['id']}: simulation must be explicit in its label")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("document", type=Path)
    parser.add_argument("schema", type=Path)
    parser.add_argument("--matrix-contract", action="store_true")
    args = parser.parse_args()
    data = validate_file(args.document, args.schema)
    if args.matrix_contract:
        validate_matrix_contract(data)
    print(f"PASS: {args.document} matches {args.schema}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

