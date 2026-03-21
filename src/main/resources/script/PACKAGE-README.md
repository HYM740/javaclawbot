# Windows EXE 打包指南

将 JavaClawBot 打包成 Windows 可执行文件，无需安装 JDK 即可运行。

## 前提条件

- **Windows 10/11** 系统
- **JDK 17+**（仅打包时需要，用户运行时不需要）

## 使用方法

### 方式 1：PowerShell 脚本（推荐）

```powershell
# 进入脚本目录
cd scripts

# 生成安装版 EXE（带安装向导）
.\build-exe.ps1

# 生成便携版 EXE（免安装，解压即用）
.\build-exe.ps1 -Portable

# 指定版本号
.\build-exe.ps1 -Version 2.0.0
```

### 方式 2：批处理脚本

```batch
cd scripts
build-exe.bat
```

## 输出文件

打包完成后，在 `scripts/dist/` 目录下生成：

| 类型 | 文件 | 说明 |
|------|------|------|
| 安装版 | `javaclawbot-1.0.0.exe` | 安装程序，带安装向导 |
| 便携版 | `javaclawbot/` 文件夹 | 解压即用，无需安装 |

## 打包原理

1. **jlink** - 创建精简 JRE（只包含必要的 Java 模块）
2. **jpackage** - 将 JAR + JRE 打包成原生可执行文件

## 文件大小估算

| 组件 | 大小 |
|------|------|
| JAR 文件 | ~93 MB |
| 精简 JRE | ~40-60 MB |
| 最终 EXE | ~100-120 MB |

## 常见问题

### Q: 打包失败，提示找不到 jpackage
A: 确保安装的是 **JDK** 而非 JRE，jpackage 是 JDK 工具。

### Q: 运行 EXE 时提示缺少 DLL
A: 确保打包时使用了正确的 `--win-console` 参数。

### Q: 如何减小 EXE 体积？
A: jlink 会自动分析依赖，只打包必要的模块。如果仍然太大，可以检查是否有不必要的依赖。

## 跨平台打包

**注意**：jpackage 不支持跨平台打包：
- 在 Windows 上只能打包 Windows EXE
- 在 macOS 上只能打包 macOS APP
- 在 Linux 上只能打包 Linux 包

如需在其他平台打包 Windows EXE，可以使用：
- GitHub Actions（Windows runner）
- Windows 虚拟机
- Docker + Wine（不推荐，兼容性问题）