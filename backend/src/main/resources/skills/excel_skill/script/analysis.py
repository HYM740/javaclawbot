from __future__ import annotations

from statistics import median
from .utils import err, ok, value_to_number


class AnalysisTools:
    @staticmethod
    def filter_rows(records: list[dict], conditions: list[dict], logic: str = "and") -> dict:
        logic = logic.lower()
        if logic not in {"and", "or"}:
            return err("logic must be and/or", "INVALID_ARGUMENT")

        def match_condition(record: dict, cond: dict) -> bool:
            field = cond.get("field")
            op = str(cond.get("operator", "eq")).lower()
            target = record.get(field)
            value = cond.get("value")
            num = value_to_number(target)
            cmp_num = value_to_number(value)
            text = "" if target is None else str(target)
            if op == "eq":
                return target == value
            if op == "neq":
                return target != value
            if op == "gt":
                return num is not None and cmp_num is not None and num > cmp_num
            if op == "gte":
                return num is not None and cmp_num is not None and num >= cmp_num
            if op == "lt":
                return num is not None and cmp_num is not None and num < cmp_num
            if op == "lte":
                return num is not None and cmp_num is not None and num <= cmp_num
            if op == "between":
                vals = value or []
                if len(vals) != 2:
                    return False
                a = value_to_number(vals[0])
                b = value_to_number(vals[1])
                return num is not None and a is not None and b is not None and a <= num <= b
            if op == "contains":
                return str(value) in text
            if op == "starts_with":
                return text.startswith(str(value))
            if op == "ends_with":
                return text.endswith(str(value))
            if op == "in":
                return target in (value or [])
            if op == "not_in":
                return target not in (value or [])
            if op == "odd":
                return num is not None and int(num) % 2 == 1
            if op == "even":
                return num is not None and int(num) % 2 == 0
            if op == "multiple_of":
                return num is not None and cmp_num not in (None, 0) and int(num) % int(cmp_num) == 0
            if op == "is_empty":
                return target in (None, "")
            if op == "not_empty":
                return target not in (None, "")
            return False

        out = []
        for r in records:
            checks = [match_condition(r, c) for c in conditions]
            if (logic == "and" and all(checks)) or (logic == "or" and any(checks)):
                out.append(r)
        return ok({"rows": out, "count": len(out)})

    @staticmethod
    def aggregate_rows(records: list[dict], rules: list[dict]) -> dict:
        results = []
        for rule in rules:
            field = rule.get("target_field")
            operation = str(rule.get("operation", "sum")).lower()
            name = rule.get("name") or f"{field}_{operation}"
            raw_values = [r.get(field) for r in records]
            values = [value_to_number(v) for v in raw_values]
            values = [v for v in values if v is not None]

            if operation == "sum":
                result = sum(values)
            elif operation == "avg":
                result = sum(values) / len(values) if values else None
            elif operation == "count":
                result = len([v for v in raw_values if v not in (None, "")])
            elif operation == "max":
                result = max(values) if values else None
            elif operation == "min":
                result = min(values) if values else None
            elif operation == "median":
                result = median(values) if values else None
            elif operation == "distinct_count":
                result = len(set(v for v in raw_values if v not in (None, "")))
            else:
                return err(f"unsupported operation: {operation}", "UNSUPPORTED_OPERATION")
            results.append({"name": name, "target_field": field, "operation": operation, "result": result})
        return ok({"results": results})

    @staticmethod
    def group_summary(records: list[dict], group_by: list[str], metrics: list[dict]) -> dict:
        buckets = {}
        for rec in records:
            key = tuple(rec.get(f) for f in group_by)
            buckets.setdefault(key, []).append(rec)
        out = []
        for key, rows in buckets.items():
            row = {group_by[i]: key[i] for i in range(len(group_by))}
            agg_res = AnalysisTools.aggregate_rows(rows, metrics)
            if not agg_res.get("success"):
                return agg_res
            for item in agg_res["data"]["results"]:
                row[item["name"]] = item["result"]
            out.append(row)
        return ok({"rows": out, "count": len(out)})

    @staticmethod
    def pivot_output(records: list[dict], rows: list[str], columns: list[str], values: list[dict], fill_value=0) -> dict:
        try:
            import pandas as pd
        except Exception as e:
            return err(f"pandas is required for pivot output: {e}", "DEPENDENCY_ERROR")
        if not values:
            return err("values is required", "INVALID_ARGUMENT")
        df = pd.DataFrame(records)
        if df.empty:
            return ok({"headers": [], "rows": []})
        value_field = values[0].get("field")
        agg = values[0].get("agg", "sum")
        table = pd.pivot_table(df, index=rows, columns=columns, values=value_field, aggfunc=agg, fill_value=fill_value)
        flat = table.reset_index()
        headers = [str(c) for c in flat.columns]
        out_rows = flat.values.tolist()
        return ok({"headers": headers, "rows": out_rows, "row_count": len(out_rows)})
