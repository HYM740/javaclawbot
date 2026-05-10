# Architecture

本 Skill 采用模块化设计：

- `workbook.py`：读取工作簿、sheet 总览、合并单元格映射
- `schema.py`：自动识别表头、多级表头、交叉表、数据区
- `reader.py`：按坐标或按记录读取数据
- `writer.py`：单元格写入、区域写入、结果表写入、sheet 覆盖/追加
- `formatter.py`：字体、填充、边框、对齐、宽高、冻结窗格
- `formulas.py`：批量公式写入
- `charts.py`：折线图/柱状图/饼图/散点图/面积图
- `analysis.py`：条件筛选、聚合、轻量透视
- `planner.py`：结构化分析计划的一键执行

## 设计原则

1. **原子能力和高层计划分离**
2. **统一 JSON 输入输出**
3. **兼容合并单元格**
4. **优先为 AI 解析而不是人类手工点击设计**
5. **渐进式披露**：主 Skill 保持简洁，细节放在 references 中
