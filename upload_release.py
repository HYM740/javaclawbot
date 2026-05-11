#!/usr/bin/env python3
"""
NexusAI 发布上传脚本
用法: python upload_release.py [changelog]
  - 自动从 pom.xml 读取版本号
  - 执行 mvn package 打包
  - 编译 Inno Setup 安装器 (NexusAI-Setup-*.exe)
  - 上传 JAR + 安装器到 MinIO (agent/releases/latest/)
  - 备份到版本号目录 (agent/releases/{version}/)
  - 生成并上传版本信息 (info)
  - changelog 可选参数，留空则自动从 CHANGELOG.md 提取
"""

import os
import sys
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

# ===================== 配置 =====================

PROJECT_DIR = Path("D:/code/ai_project/javaclawbot")
POM_FILE = PROJECT_DIR / "pom.xml"
JAR_FILE = PROJECT_DIR / "target" / "NexusAI.jar"
SETUP_DIR = PROJECT_DIR / "dist"
ISCC_EXE = "C:/Users/WIN/AppData/Local/Programs/Inno Setup 6/ISCC.exe"
ISS_FILE = PROJECT_DIR / "installer/windows/NexusAI.iss"
CHANGELOG_FILE = PROJECT_DIR / "CHANGELOG.md"

# MinIO / S3 配置
MINIO_ENDPOINT = "http://192.168.20.125:9000"
MINIO_ACCESS_KEY = "XSnCKcUT4Z5M2BbYIRQP"
MINIO_SECRET_KEY = "UbeHwkVka2CGm0mvp51ADYIKppkr9GS2gadE8EFf"
MINIO_BUCKET = "agent"
MINIO_REGION = "us-east-1"  # MinIO 默认 region

# 公开访问地址（netHost 代理）
PUBLIC_BASE_URL = "http://101.68.93.109:9102"

# 上传目标路径
RELEASE_PATH = "releases/latest"
JAR_OBJECT_KEY = f"{RELEASE_PATH}/NexusAI.jar"
SETUP_OBJECT_KEY = f"{RELEASE_PATH}/NexusAI-Setup-latest.exe"
INFO_OBJECT_KEY = f"{RELEASE_PATH}/info"


# ===================== Maven 编译 =====================

JAVA_HOME = "C:/Program Files/Java/jdk-17"
MAVEN_HOME = "D:/IDEA20240307/plugins/maven/lib/maven3"
MAVEN_REPO = "D:/apps/maven/repository"
CLASSWORLDS_JAR = f"{MAVEN_HOME}/boot/plexus-classworlds-2.8.0.jar"
CLASSWORLDS_LICENSE = f"{MAVEN_HOME}/boot/plexus-classworlds.license"
M2_CONF = f"{MAVEN_HOME}/bin/m2.conf"


def run_maven_package():
    """执行 mvn package 打包"""
    print("[1/5] 执行 mvn package 打包...")
    cmd = [
        f"{JAVA_HOME}/bin/java.exe",
        f"-Dmaven.multiModuleProjectDirectory={PROJECT_DIR}",
        f"-Dmaven.home={MAVEN_HOME}",
        f"-Dclassworlds.conf={M2_CONF}",
        "-Dfile.encoding=UTF-8",
        "-classpath", f"{CLASSWORLDS_JAR};{CLASSWORLDS_LICENSE}",
        "org.codehaus.plexus.classworlds.launcher.Launcher",
        f"-Dmaven.repo.local={MAVEN_REPO}",
        "-f", str(POM_FILE),
        "package", "-DskipTests"
    ]
    result = subprocess.run(cmd, cwd=str(PROJECT_DIR), capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        print("[ERROR] Maven package failed:")
        print(result.stdout)
        print(result.stderr)
        sys.exit(1)
    print("   Package OK")


# ===================== Inno Setup 安装器编译 =====================

def build_installer():
    """编译 Inno Setup 安装器"""
    print("   编译 Inno Setup 安装器...")
    if not ISCC_EXE or not Path(ISCC_EXE).exists():
        print("[WARN] ISCC.exe 未找到，跳过安装器编译")
        return None
    result = subprocess.run(
        [str(ISCC_EXE), str(ISS_FILE)],
        cwd=str(ISS_FILE.parent),
        capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if result.returncode != 0:
        print("[WARN] 安装器编译失败（非致命）:")
        print(result.stdout[-500:] if len(result.stdout) > 500 else result.stdout)
        print(result.stderr[-500:] if len(result.stderr) > 500 else result.stderr)
        return None
    # 查找生成的安装器
    version = read_version()
    setup_exe = SETUP_DIR / f"NexusAI-Setup-{version}.exe"
    if setup_exe.exists():
        print(f"   安装器编译成功: {setup_exe.name} ({setup_exe.stat().st_size / 1_048_576:.1f} MB)")
        return setup_exe
    # fallback: 查找 dist 下最新的 exe
    exes = list(SETUP_DIR.glob("NexusAI-Setup-*.exe"))
    if exes:
        latest = max(exes, key=os.path.getmtime)
        print(f"   安装器编译成功: {latest.name} ({latest.stat().st_size / 1_048_576:.1f} MB)")
        return latest
    print("[WARN] 未找到安装器输出文件")
    return None


# ===================== 版本信息 =====================

def read_version():
    """从 pom.xml 读取版本号"""
    print("[2/5] 读取版本信息...")
    ns = {"mvn": "http://maven.apache.org/POM/4.0.0"}
    tree = ET.parse(POM_FILE)
    root = tree.getroot()
    version = root.findtext("mvn:version", namespaces=ns)
    if not version:
        version = root.findtext("version")
    if not version:
        parent = root.find("mvn:parent", namespaces=ns)
        if parent is not None:
            version = parent.findtext("mvn:version", namespaces=ns)
    if not version:
        print("[ERROR] 无法从 pom.xml 读取版本号")
        sys.exit(1)
    print(f"   版本: {version}")
    return version


def read_changelog(args_changelog):
    """获取更新日志"""
    if args_changelog:
        return args_changelog
    if CHANGELOG_FILE.exists():
        content = CHANGELOG_FILE.read_text(encoding="utf-8")
        lines = content.split("\n")
        changelog_lines = []
        in_section = False
        for line in lines:
            if line.startswith("## ["):
                if in_section:
                    break
                in_section = True
                continue
            if in_section and line.strip():
                changelog_lines.append(line.strip())
        if changelog_lines:
            return " ".join(changelog_lines[:5])
    return ""


# ===================== MinIO 上传 =====================

def upload_to_minio(version, jar_size, setup_path, changelog):
    """上传 JAR + 安装器 + 版本信息到 MinIO"""
    print("[3/5] 连接 MinIO...")
    try:
        import boto3
        from botocore.config import Config
    except ImportError:
        print("[ERROR] 需要安装 boto3: pip install boto3")
        sys.exit(1)

    s3 = boto3.client(
        "s3",
        endpoint_url=MINIO_ENDPOINT,
        aws_access_key_id=MINIO_ACCESS_KEY,
        aws_secret_access_key=MINIO_SECRET_KEY,
        region_name=MINIO_REGION,
        config=Config(s3={"addressing_style": "path"})
    )

    # 确保 bucket 存在
    try:
        s3.head_bucket(Bucket=MINIO_BUCKET)
    except Exception:
        print(f"   Bucket '{MINIO_BUCKET}' 不存在，尝试创建...")
        s3.create_bucket(Bucket=MINIO_BUCKET)

    # ---- 上传 JAR 到 latest ----
    print(f"[4/5] 上传 JAR ({jar_size / 1_048_576:.1f} MB)...")
    try:
        s3.upload_file(
            Filename=str(JAR_FILE),
            Bucket=MINIO_BUCKET,
            Key=JAR_OBJECT_KEY,
            ExtraArgs={"ContentType": "application/java-archive"}
        )
        print(f"   JAR 上传成功 → {PUBLIC_BASE_URL}/{MINIO_BUCKET}/{JAR_OBJECT_KEY}")
    except Exception as e:
        print(f"[ERROR] JAR 上传失败: {e}")
        sys.exit(1)

    # ---- 上传安装器 EXE 到 latest ----
    setup_size = 0
    setup_url = ""
    if setup_path and setup_path.exists():
        setup_size = setup_path.stat().st_size
        print(f"   上传安装器 ({setup_size / 1_048_576:.1f} MB)...")
        try:
            s3.upload_file(
                Filename=str(setup_path),
                Bucket=MINIO_BUCKET,
                Key=SETUP_OBJECT_KEY,
                ExtraArgs={"ContentType": "application/x-msdownload"}
            )
            setup_url = f"{PUBLIC_BASE_URL}/{MINIO_BUCKET}/{SETUP_OBJECT_KEY}"
            print(f"   安装器上传成功 → {setup_url}")
        except Exception as e:
            print(f"[WARN] 安装器上传失败: {e}")
    else:
        print("   [SKIP] 安装器文件不存在，跳过上传")

    # ---- 上传版本信息 JSON ----
    print("   上传版本信息...")
    info = {
        "version": version,
        "jar": {
            "url": f"{PUBLIC_BASE_URL}/{MINIO_BUCKET}/{JAR_OBJECT_KEY}",
            "size": jar_size
        },
        "size": jar_size,
        "changelog": changelog
    }
    if setup_url:
        info["setup"] = {
            "url": setup_url,
            "size": setup_size
        }
    info_json = json.dumps(info, ensure_ascii=False, indent=2)
    print(f"   info 内容:\n{info_json}")

    try:
        s3.put_object(
            Bucket=MINIO_BUCKET,
            Key=INFO_OBJECT_KEY,
            Body=info_json.encode("utf-8"),
            ContentType="text/plain; charset=utf-8"
        )
        info_url = f"{PUBLIC_BASE_URL}/{MINIO_BUCKET}/{INFO_OBJECT_KEY}"
        print(f"   版本信息上传成功 → {info_url}")
    except Exception as e:
        print(f"[ERROR] 版本信息上传失败: {e}")
        sys.exit(1)

    # ---- 备份到版本号目录 ----
    ver_path = f"releases/{version}"
    print(f"[备份] 复制到版本目录 {ver_path}/ ...")
    try:
        s3.copy_object(
            Bucket=MINIO_BUCKET,
            CopySource={"Bucket": MINIO_BUCKET, "Key": JAR_OBJECT_KEY},
            Key=f"{ver_path}/NexusAI.jar",
            MetadataDirective="COPY"
        )
        print(f"   JAR → {ver_path}/NexusAI.jar")
    except Exception as e:
        print(f"[WARN] JAR 版本备份失败: {e}")

    if setup_url:
        try:
            setup_name = f"NexusAI-Setup-{version}.exe"
            s3.copy_object(
                Bucket=MINIO_BUCKET,
                CopySource={"Bucket": MINIO_BUCKET, "Key": SETUP_OBJECT_KEY},
                Key=f"{ver_path}/{setup_name}",
                MetadataDirective="COPY"
            )
            print(f"   安装器 → {ver_path}/{setup_name}")
        except Exception as e:
            print(f"[WARN] 安装器版本备份失败: {e}")

    return info


# ===================== 主流程 =====================

def main():
    os.chdir(str(PROJECT_DIR))

    # 解析命令行参数
    args_changelog = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else ""

    # 1. 打包
    run_maven_package()

    # 2. 编译安装器
    setup_path = build_installer()

    # 3. 读取版本
    version = read_version()

    # 4. 获取 JAR 大小
    jar_size = JAR_FILE.stat().st_size

    # 5. 获取更新日志
    changelog = read_changelog(args_changelog)

    # 6. 上传
    info = upload_to_minio(version, jar_size, setup_path, changelog)

    print()
    print("=" * 60)
    print("  发布完成!")
    print(f"  版本: {version}")
    print(f"  JAR:  {info['jar']['url']}")
    if "setup" in info:
        print(f"  安装器: {info['setup']['url']}")
    print(f"  API:  {PUBLIC_BASE_URL}/{MINIO_BUCKET}/{INFO_OBJECT_KEY}")
    print("=" * 60)


if __name__ == "__main__":
    main()
