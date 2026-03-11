from workbook import WorkbookTools
from schema import SchemaTools
from reader import ReaderTools

FILE = "demo.xlsx"

print(WorkbookTools.get_workbook_info(FILE))
print(SchemaTools.detect_table_structure(FILE, "Sheet1"))
print(ReaderTools.read_sheet_rows(FILE, "Sheet1", ["2-5"], output_mode="record"))
