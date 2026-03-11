# Examples

## 1. 读取 sheet 列表
```python
from workbook import WorkbookTools
print(WorkbookTools.get_workbook_info("demo.xlsx"))
```

## 2. 自动识别表头
```python
from schema import SchemaTools
print(SchemaTools.detect_table_structure("demo.xlsx", "Sheet1"))
```

## 3. 按记录读取数据
```python
from reader import ReaderTools
print(ReaderTools.read_sheet_rows("demo.xlsx", "Sheet1", ["2-10"], output_mode="record"))
```

## 4. 批量写结果表
```python
from writer import WriterTools
WriterTools.write_table(
    file_path="demo.xlsx",
    sheet_name="分析结果",
    start_cell="A1",
    headers=["类别", "结果"],
    rows=[["苹果", 12], ["香蕉", 18]],
    mode="replace_sheet",
)
```

## 5. 运行分析计划
```python
from planner import PlannerTools
plan = {
    "sheet_name": "Sheet1",
    "tasks": [
        {
            "type": "filter_and_aggregate",
            "label": "苹果",
            "filter": {"field": "编号", "operator": "odd"},
            "aggregate": {"field": "苹果", "operation": "avg"}
        }
    ],
    "output": {
        "write_to_new_sheet": "结果",
        "sheet_mode": "replace_sheet",
        "create_chart": True,
        "chart_type": "line"
    }
}
print(PlannerTools.run_analysis_plan("demo.xlsx", plan))
```
