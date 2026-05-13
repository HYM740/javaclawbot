/**
 * GitNexus 插件（Node.js 版本）
 *
 * 对标 GitNexus session-start.sh — 检测项目索引并注入引导上下文
 *
 * 功能：
 * 1. 扫描项目注册表中的所有绑定项目路径
 * 2. 检测每个项目是否存在 .gitnexus/ 索引
 * 3. 找到索引 → 读取 gitnexus-guide/SKILL.md 生成引导上下文
 * 4. 未找到索引 → 静默退出
 *
 * 可用变量：
 * - workspace: 工作区路径（字符串，通过环境变量 JAVACLAWBOT_WORKSPACE 传入）
 */

import path from 'path';
import fs from 'fs';
import os from 'os';

// workspace 由 Java wrapper 注入为全局变量，env var 作为 fallback
const ws = (typeof workspace !== 'undefined' && workspace)
  || process.env.JAVACLAWBOT_WORKSPACE
  || '';

// ======.gitnexus/ 索引检测 ======

/**
 * 向上遍历查找 .gitnexus/ 索引目录（最多 5 层）
 */
function findGitNexusDir(startDir) {
  let dir = path.resolve(startDir);
  for (let i = 0; i < 6; i++) {
    const candidate = path.join(dir, '.gitnexus');
    if (fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) {
      if (isGlobalRegistryDir(candidate)) return null;
      return candidate;
    }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  return null;
}

/**
 * 判断是否是全局 registry 目录（包含 registry.json 或 repos/ 的是全局目录）
 */
function isGlobalRegistryDir(candidate) {
  if (fs.existsSync(path.join(candidate, 'meta.json'))) return false;
  return (
    fs.existsSync(path.join(candidate, 'registry.json')) ||
    fs.existsSync(path.join(candidate, 'repos'))
  );
}

// ====== 项目路径发现 ======

/**
 * 从 javaclawbot 项目注册表中收集所有项目路径
 * 目录结构: {parent}/projects/{sessionId}/projects.json
 */
function collectProjectPaths() {
  const projectPaths = new Map(); // path → { main: boolean, session: string }
  const parentDir = path.resolve(ws, '..');
  const projectsRoot = path.join(parentDir, 'projects');

  if (!fs.existsSync(projectsRoot) || !fs.statSync(projectsRoot).isDirectory()) {
    return projectPaths;
  }

  let sessions;
  try {
    sessions = fs.readdirSync(projectsRoot);
  } catch {
    return projectPaths;
  }

  for (const sessionId of sessions) {
    const sessionDir = path.join(projectsRoot, sessionId);
    if (!fs.statSync(sessionDir).isDirectory()) continue;

    const projectsFile = path.join(sessionDir, 'projects.json');
    if (!fs.existsSync(projectsFile)) continue;

    try {
      const data = JSON.parse(fs.readFileSync(projectsFile, 'utf-8'));
      const projects = data.projects || {};
      for (const [name, info] of Object.entries(projects)) {
        if (info.path && fs.existsSync(info.path)) {
          // 主项目优先，或者覆盖非主项目
          if (!projectPaths.has(info.path) || info.main) {
            projectPaths.set(info.path, {
              main: !!info.main,
              name: name,
            });
          }
        }
      }
    } catch {
      // 跳过损坏的 projects.json
    }
  }

  return projectPaths;
}

/**
 * 在所有项目路径中查找 .gitnexus/ 索引
 * 优先返回主项目的索引
 */
function findProjectGitNexusDir() {
  const projectPaths = collectProjectPaths();

  // 优先检查主项目
  for (const [projPath, info] of projectPaths) {
    if (info.main) {
      const gitnexusDir = findGitNexusDir(projPath);
      if (gitnexusDir) return { gitnexusDir, projPath, info };
    }
  }

  // 再检查其他项目
  for (const [projPath, info] of projectPaths) {
    if (!info.main) {
      const gitnexusDir = findGitNexusDir(projPath);
      if (gitnexusDir) return { gitnexusDir, projPath, info };
    }
  }

  return null;
}

// ====== 技能文件读取 ======

/**
 * 简单的 frontmatter 提取
 */
function extractAndStripFrontmatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  if (!match) return { frontmatter: {}, content };
  const frontmatterStr = match[1];
  const body = match[2];
  const frontmatter = {};
  for (const line of frontmatterStr.split('\n')) {
    const colonIdx = line.indexOf(':');
    if (colonIdx > 0) {
      const key = line.slice(0, colonIdx).trim();
      const value = line.slice(colonIdx + 1).trim().replace(/^["']|["']$/g, '');
      frontmatter[key] = value;
    }
  }
  return { frontmatter, content: body };
}

/**
 * 查找 gitnexus-guide SKILL.md
 */
function findGuideSkill() {
  const possibleDirs = [
    path.join(ws, 'skills', 'gitnexus'),
    path.join(os.homedir(), '.javaclawbot', 'workspace', 'skills', 'gitnexus'),
  ];
  for (const skillDir of possibleDirs) {
    const skillFile = path.join(skillDir, 'gitnexus-guide', 'SKILL.md');
    if (fs.existsSync(skillFile) && fs.statSync(skillFile).isFile()) {
      return skillFile;
    }
  }
  return null;
}

/**
 * 读取索引元数据
 */
function readIndexMeta(gitnexusDir) {
  try {
    const metaPath = path.join(gitnexusDir, 'meta.json');
    if (fs.existsSync(metaPath)) {
      return JSON.parse(fs.readFileSync(metaPath, 'utf-8'));
    }
  } catch {}
  return null;
}

// ====== 引导上下文生成 ======

function getBootstrapContent(gitnexusDir, projPath, projInfo) {
  const meta = readIndexMeta(gitnexusDir);
  const repoName = meta?.repoName || path.basename(projPath);
  const stats = meta?.stats || {};
  const symbols = stats.nodes || '?';
  const edges = stats.edges || '?';
  const processes = stats.processes || '?';
  const lastCommit = meta?.lastCommit ? meta.lastCommit.slice(0, 7) : 'unknown';
  const indexedAt = meta?.indexedAt
    ? new Date(meta.indexedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
    : 'unknown';

  const guideFile = findGuideSkill();
  let guideContent = '';
  if (guideFile) {
    try {
      const fullContent = fs.readFileSync(guideFile, 'utf-8');
      const { content } = extractAndStripFrontmatter(fullContent);
      guideContent = content;
    } catch {}
  }

  const toolMapping = `** 工具映射：**
当技能引用你没有的工具时，请替换为 你具备的 等效工具：
- \`query\` → \`mcp__gitnexus__query\`
- \`context\` → \`mcp__gitnexus__context\`
- \`impact\` → \`mcp__gitnexus__impact\`
- \`detect_changes\` → \`mcp__gitnexus__detect_changes\`
- \`rename\` → \`mcp__gitnexus__rename\`
- \`cypher\` → \`mcp__gitnexus__cypher\`
- \`list_repos\` → \`mcp__gitnexus__list_repos\`
- \`route_map\` → \`mcp__gitnexus__route_map\`
- \`tool_map\` → \`mcp__gitnexus__tool_map\`
- \`api_impact\` → \`mcp__gitnexus__api_impact\`
- \`shape_check\` → \`mcp__gitnexus__shape_check\`
- \`group_list\` → \`mcp__gitnexus__group_list\`
- \`group_sync\` → \`mcp__gitnexus__group_sync\`

**技能位置：**
GitNexus 技能位于 \`${ws}/skills/gitnexus/\`
加载时使用前缀，示例: gitnexus/gitnexus-exploring
使用原生 skill 工具来加载技能。`;

  return `<gitnexus-bootstrap>
## GitNexus Code Intelligence — 已激活

当前项目已通过 GitNexus 索引：
- **项目**: ${repoName} (${projPath})
- **符号**: ${symbols} | **关系**: ${edges} | **执行流程**: ${processes}
- **索引时间**: ${indexedAt} | **索引提交**: ${lastCommit}

${guideContent}

${toolMapping}

**铁律：**
1. **修改前必查 impact** — 编辑任何符号前运行 \`impact({target: "符号名", direction: "upstream"})\`
2. **提交前必 detect_changes** — 验证修改范围不出意外
3. **HIGH/CRITICAL 风险必警告用户** — 让用户知情后再继续
4. **索引过期必提醒** — 如果 \`list_repos\` 显示 staleness，建议运行 \`npx gitnexus analyze\`
</gitnexus-bootstrap>`;
}

// ====== 主逻辑 ======

try {
  const result = findProjectGitNexusDir();
  if (result) {
    const bootstrapContent = getBootstrapContent(
      result.gitnexusDir,
      result.projPath,
      result.info,
    );
    if (bootstrapContent) {
      console.log(bootstrapContent);
    }
  }
} catch (err) {
  // 静默失败，不影响主流程
  if (process.env.GITNEXUS_DEBUG) {
    console.error('GitNexus 插件错误:', err.message);
  }
}

// 未找到索引时静默退出（不输出任何内容）
