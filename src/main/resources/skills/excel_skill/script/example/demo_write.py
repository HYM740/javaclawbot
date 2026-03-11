from writer import WriterTools
from formulas import FormulaTools
from formatter import FormatterTools

FILE = "demo.xlsx"

print(WriterTools.write_cells(FILE, "Sheet1", [
    {"range": "B2", "value": "苹果"},
    {"range": "C2:D2", "value": 100, "merge": True},
]))
print(FormulaTools.write_formulas(FILE, "Sheet1", [
    {"cell": "E2", "formula": "=SUM(C2:D2)"}
]))
print(FormatterTools.format_cells(FILE, "Sheet1", [
    {"range": "B2:E2", "bold": True, "fill_color": "FFF2CC", "border": True, "column_width": 14}
]))
