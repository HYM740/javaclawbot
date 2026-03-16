# 会话搜索功能复刻方案

## 目标

复刻 OpenClaw 的会话搜索功能到 nanobot-dev，使 memory 工具能够搜索会话历史记录。

## 实现状态

### ✅ Phase 1: 会话文件解析

1. 创建 `SessionFiles.java` - 会话文件处理工具
2. 实现 JSONL 解析
3. 实现消息文本提取
4. 实现敏感信息脱敏

### ✅ Phase 2: 索引扩展

1. 扩展 `MemoryIndexManager` 支持 sessions
   - 添加 `sources` 配置
   - 添加 `sessionsDir` 配置
   - 实现 `syncSessions()` 方法
   - 实现 `indexSessionFile()` 方法
   - 实现行号映射到 JSONL 源行号

2. 扩展 `MemorySearch` 支持 sources 配置
   - 添加 `setSources()` 方法
   - 添加 `setSessionsDir()` 方法

### ✅ Phase 3: 搜索集成

1. 扩展 `MemorySearchTool` 支持 sessions
   - 添加 `sessionsDir` 参数
   - 自动检测并启用会话搜索

## OpenClaw 实现分析

### 1. 配置支持

```typescript
// memory-search.ts
sources: Array<"memory" | "sessions">;  // 搜索来源
experimental: { sessionMemory: boolean };  // 是否启用会话记忆
```

### 2. 会话文件处理 (session-files.ts)

```typescript
// 读取 .jsonl 会话文件
// 解析每行 JSON，提取 type: "message" 的记录
// 提取 user 和 assistant 角色的文本内容
// 生成 lineMap 映射内容行到 JSONL 源行号

export type SessionFileEntry = {
  path: string;           // 相对路径 "sessions/xxx.jsonl"
  absPath: string;        // 绝对路径
  mtimeMs: number;        // 修改时间
  size: number;           // 文件大小
  hash: string;           // 内容哈希
  content: string;        // 提取的文本内容
  lineMap: number[];      // 行号映射
};
```

### 3. 索引管理

- 监听会话文件变化 (`onSessionTranscriptUpdate`)
- 增量索引会话内容
- 按 `source` 字段区分 memory 和 sessions

### 4. 搜索接口

```typescript
interface MemorySearchResult {
  path: string;
  startLine: number;
  endLine: number;
  score: number;
  snippet: string;
  source: "memory" | "sessions";  // 来源标识
  citation?: string;
}

interface MemorySearchManager {
  search(query, opts?: { sessionKey?: string }): Promise<MemorySearchResult[]>;
}
```

## nanobot-dev 实现方案

### 1. 新增 SessionFiles.java

```
src/main/java/memory/SessionFiles.java
```

功能：
- `listSessionFiles(Path sessionsDir)` - 列出所有会话文件
- `buildSessionEntry(Path sessionFile)` - 解析会话文件，提取文本
- `extractSessionText(Object content)` - 提取消息文本

### 2. 扩展 MemoryIndexManager.java

新增：
- `sources: Set<String>` - 搜索来源配置
- `syncSessions()` - 同步会话文件
- `indexSessionFile(Path file)` - 索引单个会话文件
- `listSessionFiles()` - 列出会话文件

修改：
- `sync()` - 同时同步 memory 和 sessions
- `search()` - 支持按 source 过滤

### 3. 扩展 MemorySearch.java

新增：
- `sources: Set<String>` - 搜索来源配置
- `setSources(Set<String>)` - 设置搜索来源

### 4. 修改 MemorySearchTool.java (agent/tool)

新增：
- `sessionKey` 参数
- 传递 sessionKey 到搜索接口

### 5. 修改 SessionManager.java

新增：
- 会话文件变更监听接口
- 与 MemoryIndexManager 的集成

## 数据库表结构

已有表结构支持 `source` 字段：

```sql
CREATE TABLE files (
    path TEXT PRIMARY KEY,
    source TEXT NOT NULL DEFAULT 'memory',  -- 'memory' 或 'sessions'
    hash TEXT NOT NULL,
    mtime INTEGER NOT NULL,
    size INTEGER NOT NULL
);

CREATE TABLE chunks (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'memory',  -- 'memory' 或 'sessions'
    ...
);
```

## 实现步骤

### Phase 1: 会话文件解析

1. 创建 `SessionFiles.java`
2. 实现 JSONL 解析
3. 实现消息文本提取
4. 编写单元测试

### Phase 2: 索引扩展

1. 扩展 `MemoryIndexManager` 支持 sessions
2. 实现会话文件索引
3. 实现增量同步
4. 编写单元测试

### Phase 3: 搜索集成

1. 扩展 `MemorySearch` 支持 sources 配置
2. 修改 `MemorySearchTool` 添加 sessionKey 参数
3. 集成测试

### Phase 4: 会话监听

1. 添加会话文件变更监听
2. 实现增量更新
3. 性能优化

## 文件清单

### 新增文件

- `src/main/java/memory/SessionFiles.java` - 会话文件处理工具

### 修改文件

- `src/main/java/memory/MemoryIndexManager.java` - 添加 sessions 支持
- `src/main/java/memory/MemorySearch.java` - 添加 sources 配置
- `src/main/java/agent/tool/MemorySearchTool.java` - 添加 sessionKey 参数

## 测试用例

1. 解析 JSONL 会话文件
2. 索引会话文件
3. 搜索会话内容
4. 混合搜索（memory + sessions）
5. 增量更新