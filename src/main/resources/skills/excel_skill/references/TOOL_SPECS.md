# Tool Specs

## WorkbookTools

### get_workbook_info(file_path)
返回工作簿总览、sheet 名称、每个 sheet 的 used_range、max_row、max_column、merged_ranges。

### get_merged_cell_map(file_path, sheet_name)
返回合并区域映射：range、top_left_cell、value。

## SchemaTools

### detect_table_structure(file_path, sheet_name, scan_rows=8)
自动推断 header_row_start、header_row_end、data_start_row、data_end_row、structure_type。

### get_sheet_schema(file_path, sheet_name, header_rows=None, auto_detect=True)
返回 columns、header_path、field_name、display_name、data_row_count。

## ReaderTools

### read_sheet_rows(..., output_mode="coordinate")
返回带 row / columns / merged_range 的坐标型 JSON。

### read_sheet_rows(..., output_mode="record")
返回 `[{"_row": 2, "字段A": "...", ...}]`。

## WriterTools

### write_cells(...)
支持 `range` 或 `row+column`；支持合并区域写入。

### write_table(...)
支持 `overwrite` / `append` / `replace_sheet`。

## FormulaTools

### write_formulas(...)
批量写公式到指定 cell。

## FormatterTools

### format_cells(...)
支持 font/fill/alignment/border/number_format/column_width/row_height/freeze_panes。

## AnalysisTools

### filter_rows(records, conditions, logic="AND")
支持 eq/neq/gt/gte/lt/lte/contains/in/not_in/odd/even/multiple_of/is_empty/not_empty/between。

### aggregate_rows(records, rules)
支持 sum/avg/count/max/min/median/distinct_count。

### pivot_records(records, rows, columns, values)
输出轻量透视结果表。

## PlannerTools

### run_analysis_plan(file_path, plan)
一次性执行：识别 → 读取 → 筛选 → 聚合 → 输出结果 sheet → 生成图表。
