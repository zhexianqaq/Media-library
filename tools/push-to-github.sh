#!/usr/bin/env sh
# 一键把本仓库推送到 GitHub（自动建仓库 + 推送 main + 推送标签）。
#
# 用法：
#   GH_TOKEN=ghp_xxx ./tools/push-to-github.sh                     # 推到 Media-library（默认）
#   GH_TOKEN=ghp_xxx ./tools/push-to-github.sh my-name --private   # 私有仓库、自定义名字
#
# Token 要求（github.com/settings/tokens 新建 classic token）：
#   - repo       ：建仓库 + 推送代码
#   - workflow   ：推送 .github/workflows/ 下的文件（缺这个会报 refusing to allow a Personal Access Token to create or update workflow）
#
# 推送完成后建议立刻在 GitHub 上删除该 token。
set -eu

REPO_NAME=${1:-Media-library}
VISIBILITY=public
[ "${2:-}" = "--private" ] && VISIBILITY=private

TOKEN=${GH_TOKEN:-${GITHUB_TOKEN:-}}
if [ -z "$TOKEN" ]; then
  echo "❌ 请先设置 GH_TOKEN（classic token，需 repo + workflow 权限）" >&2
  echo "   例：GH_TOKEN=ghp_xxx $0 $REPO_NAME" >&2
  exit 1
fi

cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

API=https://api.github.com
AUTH="Authorization: Bearer $TOKEN"

# 1. 取账号名
OWNER=$(curl -fsS -H "$AUTH" "$API/user" | sed -n 's/.*"login":[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
if [ -z "$OWNER" ]; then
  echo "❌ 取不到账号信息，token 可能无效或过期" >&2
  exit 1
fi
echo "→ 账号：$OWNER"

# 2. 建仓库（已存在则跳过）
CODE=$(curl -sS -o /tmp/gh-create.json -w '%{http_code}' -X POST \
  -H "$AUTH" -H "Accept: application/vnd.github+json" \
  "$API/user/repos" \
  -d "{\"name\":\"$REPO_NAME\",\"description\":\"「抖音解析」配套私有图库：按 UP主 → 作品 → 文件 浏览本地抖音解析文件，全程离线、不进系统相册。\",\"private\":$([ "$VISIBILITY" = private ] && echo true || echo false),\"has_issues\":true,\"has_wiki\":false}")

case "$CODE" in
  201) echo "✅ 已创建仓库：https://github.com/$OWNER/$REPO_NAME" ;;
  422) echo "ℹ️  仓库已存在，直接推送：https://github.com/$OWNER/$REPO_NAME" ;;
  *)   echo "❌ 建仓库失败（HTTP $CODE）：" >&2; cat /tmp/gh-create.json >&2; echo >&2
       echo "   常见原因：token 缺 repo 权限；或团队策略禁止建仓库。" >&2
       exit 1 ;;
esac

# 3. 推送 main（token 只放在本次推送的 URL 里，不写进 .git/config）
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$OWNER/$REPO_NAME.git"
echo "→ 推送 main ..."
git push "https://x-access-token:$TOKEN@github.com/$OWNER/$REPO_NAME.git" main:main
git branch --set-upstream-to=origin/main main >/dev/null 2>&1 || true

# 4. 推送标签（v* 会触发 CI 自动发 Release）
if [ -n "$(git tag -l 'v*')" ]; then
  echo "→ 推送标签（触发 CI 发版）..."
  git push "https://x-access-token:$TOKEN@github.com/$OWNER/$REPO_NAME.git" --tags
fi

echo
echo "✅ 完成"
echo "   仓库：https://github.com/$OWNER/$REPO_NAME"
echo "   Actions：https://github.com/$OWNER/$REPO_NAME/actions"
echo "   Releases：https://github.com/$OWNER/$REPO_NAME/releases"
echo
echo "下一步（可选）：在 Settings → Secrets and variables → Actions 配 4 个签名 Secret，"
echo "CI 才能产出可直接安装的签名包，详见 README §2.1。"
