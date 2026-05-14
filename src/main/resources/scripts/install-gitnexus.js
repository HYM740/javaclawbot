#!/usr/bin/env node
/**
 * GitNexus 安装脚本 — NexusAI 插件增强
 *
 * 用途: npm install gitnexus + skills 同步 + config.json 更新
 * 运行: node scripts/install-gitnexus.js
 *
 * 幂等：重复运行不会破坏已有配置
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const os = require('os');

// ========== 路径常量 ==========
const HOME = os.homedir();
const CONFIG_PATH = path.join(HOME, '.javaclawbot', 'config.json');
const SKILLS_DIR = path.join(HOME, '.javaclawbot', 'workspace', 'skills', 'gitnexus');
const TMP_CLONE_DIR = path.join(os.tmpdir(), 'gitnexus-clone-' + Date.now());
const REPO_URL = 'https://gitee.com/wangyh8216/GitNexus.git';

// ========== 日志工具 ==========
const log = {
  info: (msg) => console.log(`[INFO] ${msg}`),
  warn: (msg) => console.warn(`[WARN] ${msg}`),
  error: (msg) => { console.error(`[ERROR] ${msg}`); process.exit(1); },
  step: (msg) => console.log(`\n[STEP] ${msg}`),
  skip: (msg) => console.log(`[SKIP] ${msg}`),
  ok: (msg) => console.log(`[OK]   ${msg}`),
};

// ========== 工具函数 ==========
function exec(cmd, opts = {}) {
  log.info(`执行: ${cmd}`);
  return execSync(cmd, {
    stdio: opts.silent ? 'pipe' : 'inherit',
    encoding: 'utf-8',
    ...opts
  });
}

function jsonRead(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf-8'));
}

function jsonWrite(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2) + '\n', 'utf-8');
}

// 确保嵌套路径存在
function ensurePath(obj, keys, defaultValue) {
  let current = obj;
  for (const key of keys) {
    if (!current[key] || typeof current[key] !== 'object' || Array.isArray(current[key])) {
      current[key] = defaultValue;
    }
    current = current[key];
  }
  return current;
}

// 递归复制目录
function copyDirSync(src, dst) {
  fs.mkdirSync(dst, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const dstPath = path.join(dst, entry.name);
    if (entry.isDirectory()) {
      copyDirSync(srcPath, dstPath);
    } else {
      fs.copyFileSync(srcPath, dstPath);
    }
  }
}

// ========== 步骤 1：安装 gitnexus npm 包 ==========
function ensureNpmPackage() {
  log.step('检查 gitnexus npm 包...');

  // 检查是否已安装
  let installed = false;
  try {
    const result = execSync('npm list -g gitnexus --depth=0', {
      encoding: 'utf-8',
      stdio: 'pipe'
    });
    installed = result.includes('gitnexus@');
  } catch (e) {
    // npm list 返回非零表示未找到（或部分包不完整），忽略
    installed = false;
  }

  if (installed) {
    log.skip('gitnexus 已安装，跳过 npm install');
    // 验证可用性
    try {
      execSync('gitnexus --version', { stdio: 'inherit' });
    } catch (e) {
      log.warn('gitnexus --version 失败，尝试重新安装...');
      exec('npm install -g gitnexus');
    }
    return;
  }

  log.info('正在安装 gitnexus...');
  try {
    exec('npm install -g gitnexus');
    log.ok('gitnexus 安装成功');
  } catch (e) {
    log.error(`npm install 失败: ${e.message}\n请检查网络连接，国内可能需要代理。`);
  }
}

// ========== 步骤 2：同步 GitNexus skills ==========
function ensureSkills() {
  log.step('同步 GitNexus skills...');

  const repoSkillsDir = path.join(TMP_CLONE_DIR, '.claude', 'skills', 'gitnexus');

  // 2.1 克隆仓库（浅克隆加速）
  try {
    log.info(`克隆仓库: ${REPO_URL}`);
    exec(`git clone --depth 1 ${REPO_URL} "${TMP_CLONE_DIR}"`);
  } catch (e) {
    log.error(`git clone 失败: ${e.message}\n请检查 gitee.com 可访问性。`);
  }

  // 2.2 确保目标目录存在
  fs.mkdirSync(SKILLS_DIR, { recursive: true });

  // 2.3 读取源目录下的子目录和文件列表
  const srcEntries = fs.readdirSync(repoSkillsDir, { withFileTypes: true });
  const srcDirs = srcEntries.filter(e => e.isDirectory());

  // 2.4 逐个同步子目录（只新增，不覆盖已有）
  let addedCount = 0;
  for (const dir of srcDirs) {
    const srcPath = path.join(repoSkillsDir, dir.name);
    const dstPath = path.join(SKILLS_DIR, dir.name);

    if (fs.existsSync(dstPath)) {
      log.skip(`技能已存在: ${dir.name}`);
    } else {
      copyDirSync(srcPath, dstPath);
      log.ok(`新增技能: ${dir.name}`);
      addedCount++;
    }
  }

  // 2.5 复制根级别 SKILL.md 文件（如果源中存在且目标不存在）
  const srcFiles = srcEntries.filter(e => e.isFile());
  for (const file of srcFiles) {
    const srcPath = path.join(repoSkillsDir, file.name);
    const dstPath = path.join(SKILLS_DIR, file.name);
    if (!fs.existsSync(dstPath)) {
      fs.copyFileSync(srcPath, dstPath);
      log.ok(`新增文件: ${file.name}`);
    }
  }

  // 2.6 清理临时目录
  fs.rmSync(TMP_CLONE_DIR, { recursive: true, force: true });

  if (addedCount > 0) {
    log.ok(`skills 同步完成，新增 ${addedCount} 个技能`);
  } else {
    log.skip('skills 已是最新，无需更新');
  }
}

// ========== 步骤 3：更新 config.json ==========
function updateConfig() {
  log.step('更新 config.json...');

  // 3.1 读取配置
  if (!fs.existsSync(CONFIG_PATH)) {
    log.error(`config.json 不存在: ${CONFIG_PATH}`);
  }
  let config;
  try {
    config = jsonRead(CONFIG_PATH);
  } catch (e) {
    log.error(`config.json 解析失败: ${e.message}`);
  }

  let changed = false;

  // 3.2 确保 mcpServers 路径存在
  ensurePath(config, ['tools', 'mcpServers'], {});

  // 3.3 添加 mcpServers.gitnexus（仅当不存在时）
  if (!config.tools.mcpServers.gitnexus) {
    config.tools.mcpServers.gitnexus = {
      type: '',
      enable: true,
      command: 'cmd',
      args: ['/c', 'gitnexus', 'mcp'],
      env: {},
      url: '',
      headers: {},
      tool_timeout: 120
    };
    changed = true;
    log.ok('已添加 tools.mcpServers.gitnexus');
  } else {
    log.skip('tools.mcpServers.gitnexus 已存在');
  }

  // 3.4 确保 plugins.items 路径存在
  ensurePath(config, ['plugins', 'items'], {});

  // 3.5 添加 plugins.items.gitnexus（仅当不存在时）
  if (!config.plugins.items.gitnexus) {
    config.plugins.items.gitnexus = {
      name: 'gitnexus',
      enabled: true,
      priority: 100
    };
    changed = true;
    log.ok('已添加 plugins.items.gitnexus');
  } else {
    log.skip('plugins.items.gitnexus 已存在');
  }

  // 3.6 写入（仅在变更时）
  if (changed) {
    jsonWrite(CONFIG_PATH, config);
    log.ok('config.json 已更新');
  } else {
    log.skip('config.json 无需更新，所有条目已存在');
  }
}

// ========== 主流程 ==========
function main() {
  log.step('GitNexus 安装脚本开始');
  log.info(`平台: ${os.platform()}`);
  log.info(`config.json: ${CONFIG_PATH}`);
  log.info(`skills 目录: ${SKILLS_DIR}`);

  ensureNpmPackage();
  ensureSkills();
  updateConfig();

  console.log('\n' + '='.repeat(50));
  log.ok('GitNexus 安装完成！');
  log.info('重启 NexusAI 后生效。');
  console.log('='.repeat(50));
}

main();
