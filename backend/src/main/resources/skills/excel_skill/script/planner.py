from __future__ import annotations

from .analysis import AnalysisTools
from .charts import ChartTools
from .formatter import FormatterTools
from .formulas import FormulaTools
from .reader import ReaderTools
from .schema import SchemaTools
from .writer import WriterTools
from .utils import err, ok


class PlannerTools:
    @staticmethod
    def run_analysis_plan(file_path: str, plan: dict) -> dict:
        sheet_name = plan.get("sheet_name")
        if not sheet_name:
            return err("sheet_name is required", "INVALID_ARGUMENT")

        source_mode = plan.get("source_mode", "auto")
        if source_mode == "cross_table":
            norm = SchemaTools.normalize_cross_table(file_path, sheet_name)
            if not norm.get("success"):
                return norm
            records = norm["data"]["records"]
            schema_data = norm["data"]["schema"]
        else:
            schema_res = SchemaTools.detect_table_structure(file_path, sheet_name)
            if not schema_res.get("success"):
                return schema_res
            schema_data = schema_res["data"]
            start = schema_data["data_start_row"]
            end = schema_data["used_range"]["max_row"]
            read_res = ReaderTools.read_sheet_rows(file_path, sheet_name, [f"{start}-{end}"], output_mode="record")
            if not read_res.get("success"):
                return read_res
            records = read_res["data"]["rows"]

        working = records
        if plan.get("filters"):
            filtered = AnalysisTools.filter_rows(working, plan["filters"], logic=plan.get("filter_logic", "and"))
            if not filtered.get("success"):
                return filtered
            working = filtered["data"]["rows"]

        result_headers = []
        result_rows = []

        if plan.get("group_summary"):
            gs = plan["group_summary"]
            summary = AnalysisTools.group_summary(working, gs.get("group_by", []), gs.get("metrics", []))
            if not summary.get("success"):
                return summary
            working_rows = summary["data"]["rows"]
            result_headers = list(working_rows[0].keys()) if working_rows else []
            result_rows = [[row.get(h) for h in result_headers] for row in working_rows]
        elif plan.get("pivot"):
            pv = plan["pivot"]
            pivot = AnalysisTools.pivot_output(working, pv.get("rows", []), pv.get("columns", []), pv.get("values", []), fill_value=pv.get("fill_value", 0))
            if not pivot.get("success"):
                return pivot
            result_headers = pivot["data"]["headers"]
            result_rows = pivot["data"]["rows"]
        elif plan.get("tasks"):
            result_headers = ["标签", "筛选规则", "聚合方式", "结果"]
            for task in plan.get("tasks", []):
                filt = task.get("filter")
                label = task.get("label", "result")
                target_rows = working
                if filt:
                    filtered = AnalysisTools.filter_rows(working, [filt])
                    if not filtered.get("success"):
                        return filtered
                    target_rows = filtered["data"]["rows"]
                agg = task.get("aggregate", {})
                agg_res = AnalysisTools.aggregate_rows(target_rows, [{"name": label, "target_field": agg.get("field"), "operation": agg.get("operation", "sum")}])
                if not agg_res.get("success"):
                    return agg_res
                result = agg_res["data"]["results"][0]["result"]
                result_rows.append([label, str(filt), agg.get("operation", "sum"), result])
        else:
            result_headers = list(working[0].keys()) if working else []
            result_rows = [[row.get(h) for h in result_headers] for row in working]

        output = plan.get("output", {})
        result_sheet = output.get("sheet_name", "分析结果")
        mode = output.get("mode", "replace_sheet")
        write_res = WriterTools.write_table(file_path, result_sheet, output.get("start_cell", "A1"), result_headers, result_rows, mode=mode)
        if not write_res.get("success"):
            return write_res

        if output.get("header_style", True) and result_headers:
            end_col = chr(64 + min(26, len(result_headers)))
            FormatterTools.format_cells(file_path, result_sheet, [{"range": f"A1:{end_col}1", "bold": True, "fill_color": "D9EAF7", "border": True, "wrap_text": True, "column_width": output.get("column_width", 16)}])
            FormatterTools.set_sheet_view(file_path, result_sheet, freeze_panes="A2", auto_filter_range=f"A1:{end_col}{max(1, len(result_rows)+1)}")

        if plan.get("formulas"):
            FormulaTools.write_formulas(file_path, result_sheet, plan["formulas"])

        chart_result = None
        chart_cfg = plan.get("chart", {})
        if chart_cfg.get("enabled") and result_rows and len(result_headers) >= 2:
            data_col = chart_cfg.get("data_column", min(len(result_headers), 2))
            cat_col = chart_cfg.get("category_column", 1)
            start_row = 1
            end_row = len(result_rows) + 1
            data_range = f"{chr(64+data_col)}{start_row}:{chr(64+data_col)}{end_row}"
            cat_range = f"{chr(64+cat_col)}2:{chr(64+cat_col)}{end_row}"
            chart_result = ChartTools.create_chart(file_path, result_sheet, data_range, cat_range, chart_type=chart_cfg.get("type", "line"), position=chart_cfg.get("position", "H2"), title=chart_cfg.get("title", "分析图表"))

        return ok({
            "source_record_count": len(records),
            "working_record_count": len(working),
            "result_sheet": result_sheet,
            "result_headers": result_headers,
            "result_row_count": len(result_rows),
            "schema": schema_data,
            "chart_result": chart_result,
        })
