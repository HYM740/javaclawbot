#!/usr/bin/env python3
"""
Python 插件示例

插件说明：
- 插件文件放在 workspace/plugins/ 目录下
- 支持 .js 和 .py 文件
- Python 插件通过 subprocess 执行（python 命令）
- stdout 输出作为返回结果

配置示例（config.json）：
{
  "plugins": {
    "items": {
      "example": { "enabled": true, "priority": 10 },
      "another": { "enabled": false, "priority": 20 }
    }
  }
}

注意事项：
- 执行目录为工作区根目录
- stderr 会被合并到 stdout
- 执行失败时会有警告日志
"""

import os
import sys
from datetime import datetime

# 获取当前工作目录（即 workspace）
workspace = os.getcwd()

# 生成输出内容
output = f"""这是来自 Python 插件的示例输出。
工作区路径: {workspace}
当前时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
Python 版本: {sys.version.split()[0]}"""

# 输出到 stdout（这会被作为插件结果返回）
print(output)