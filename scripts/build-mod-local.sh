#!/usr/bin/env bash
# 本地编译 BiliPai Mod（包名 com.android.purebilibili.mod，与官方版并存）。
#
# Windows：在 Git Bash 中运行；macOS / Linux：直接在终端运行。
# 需要：JDK 21（Android Studio 自带的 JBR 即可）、Android SDK（compileSdk 37）、Git、网络。
#
# 用法：
#   bash scripts/build-mod-local.sh                         # debug 签名（与 Releases 版本签名不同）
#   bash scripts/build-mod-local.sh /path/to/bilipai-mod-ec.p12   # 固定签名，与 Releases 版本可互相覆盖安装
#
# 可选环境变量：
#   BUILD_NUMBER  versionCode 序号（默认 9）；versionCode = 619 × 10000 + BUILD_NUMBER
#   KEYSTORE_PASSWORD  固定签名口令；不设置时运行中会提示输入
set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE="${1:-}"
BUILD_NUMBER="${BUILD_NUMBER:-9}"

to_native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# 去掉脚本里的 CR（Windows 上 core.autocrlf=true 检出的 gradlew 会报 /bin/sh^M: bad interpreter）
fix_crlf() {
  # 不用 grep 检测：Git Bash 的 grep 会把 CRLF 当作行尾吞掉 CR，检测不到。
  sed -i 's/\r$//' "$1"
}

# 1. 环境检查
if ! java -version 2>&1 | grep -q 'version "21'; then
  echo "✗ 需要 JDK 21。安装 Android Studio 后可把 JAVA_HOME 指向它自带的 jbr 目录，例如："
  echo "    export JAVA_HOME=\"/c/Program Files/Android/Android Studio/jbr\"; export PATH=\"\$JAVA_HOME/bin:\$PATH\""
  exit 1
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && ! grep -q '^sdk.dir=' local.properties 2>/dev/null; then
  echo "✗ 找不到 Android SDK。设置 ANDROID_HOME，或在项目根目录 local.properties 写一行（Windows 路径用 \\\\ 或 /）："
  echo "    sdk.dir=C:/Users/<你的用户名>/AppData/Local/Android/Sdk"
  exit 1
fi

# 2. Miuix：GitHub Packages 只保留最近的 SNAPSHOT，按 libs.versions.toml 固定的提交从源码发布到 mavenLocal
MIUIX_VERSION=$(sed -n 's/^miuix = "\(.*\)"$/\1/p' gradle/libs.versions.toml | tr -d '\r')
MIUIX_SHA=$(printf '%s' "$MIUIX_VERSION" | sed -n 's/^[0-9.]*-\([0-9a-f]\{7,40\}\)-SNAPSHOT$/\1/p')
if [ -z "$MIUIX_SHA" ]; then
  echo "✗ 无法从 Miuix 版本 '$MIUIX_VERSION' 解析提交号"
  exit 1
fi
if [ ! -d "$HOME/.m2/repository/top/yukonga/miuix/kmp/miuix-core/$MIUIX_VERSION" ]; then
  WORK="${TMPDIR:-/tmp}/miuix-$MIUIX_SHA"
  echo "→ 编译 Miuix $MIUIX_VERSION 到 mavenLocal（首次较慢，会下载 Kotlin/Native 工具链）"
  if [ ! -d "$WORK/.git" ]; then
    # Windows 的 Git 默认 core.autocrlf=true，会把 gradlew 检出成 CRLF，Git Bash 无法执行。
    git -c core.autocrlf=false clone --filter=blob:none --no-checkout https://github.com/compose-miuix-ui/miuix "$WORK"
  fi
  (
    cd "$WORK"
    git config core.autocrlf false
    git checkout --quiet "$MIUIX_SHA"
    fix_crlf gradlew
    # Miuix 用 git rev-parse --short 生成版本号，固定缩写长度以得到与 BiliPai 一致的版本。
    git config core.abbrev ${#MIUIX_SHA}
    TASKS=""
    for m in miuix-core miuix-squircle miuix-material miuix-ui miuix-preference miuix-blur miuix-icons; do
      TASKS="$TASKS :$m:publishKotlinMultiplatformPublicationToMavenLocal :$m:publishAndroidPublicationToMavenLocal"
    done
    chmod +x gradlew
    # shellcheck disable=SC2086
    bash ./gradlew --no-configuration-cache $TASKS
  )
  test -d "$HOME/.m2/repository/top/yukonga/miuix/kmp/miuix-core/$MIUIX_VERSION" || {
    echo "✗ Miuix 发布到 mavenLocal 失败"; exit 1;
  }
fi

# 3. 签名：传入 keystore 时使用与 GitHub Actions 相同的固定签名（别名 bilipai-mod）
if [ -n "$KEYSTORE" ]; then
  if [ ! -f "$KEYSTORE" ]; then
    echo "✗ 找不到 keystore：$KEYSTORE"
    exit 1
  fi
  if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    read -r -s -p "keystore 口令（secrets.txt 里的 RELEASE_STORE_PASSWORD）: " KEYSTORE_PASSWORD
    echo
  fi
  ABS_KEYSTORE="$(cd "$(dirname "$KEYSTORE")" && pwd)/$(basename "$KEYSTORE")"
  export BILI_RELEASE_STORE_FILE="$(to_native_path "$ABS_KEYSTORE")"
  export BILI_RELEASE_STORE_PASSWORD="$KEYSTORE_PASSWORD"
  export BILI_RELEASE_KEY_PASSWORD="$KEYSTORE_PASSWORD"
  export BILI_RELEASE_KEY_ALIAS="bilipai-mod"
  echo "→ 使用固定签名：$BILI_RELEASE_STORE_FILE"
else
  echo "→ 未传入 keystore，使用本机 debug 签名（与 Releases 版本不能互相覆盖安装）"
fi

# 4. 编译
fix_crlf gradlew
chmod +x gradlew
bash ./gradlew -Pbili.miuix.mavenLocal=true "-Pbili.mod.buildNumber=$BUILD_NUMBER" assembleMod

APK="app/build/outputs/bilipai/mod/BiliPai-0.10.5-mod.apk"
if [ -f "$APK" ]; then
  echo "✓ 完成：$(to_native_path "$(pwd)/$APK")"
else
  echo "✓ 编译结束，APK 位于 app/build/outputs/bilipai/mod/"
fi
