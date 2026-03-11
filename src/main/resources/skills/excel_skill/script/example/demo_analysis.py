from planner import PlannerTools

FILE = "demo.xlsx"

plan = {
    "sheet_name": "Sheet1",
    "filters": [{"field": "编号", "operator": "not_empty"}],
    "group_summary": {
        "group_by": ["类别"],
        "metrics": [
            {"name": "数量求和", "target_field": "数量", "operation": "sum"},
            {"name": "数量平均", "target_field": "数量", "operation": "avg"},
        ],
    },
    "output": {"sheet_name": "分析结果", "mode": "replace_sheet", "header_style": True},
    "chart": {"enabled": True, "type": "bar", "position": "H2", "title": "分类汇总"},
}

print(PlannerTools.run_analysis_plan(FILE, plan))
