#!/bin/bash
###############################################################################
# sjy.qisont.art 自动拉取 → 构建 → 发布   (v2 · 适配单仓三端)
#   仓库   https://github.com/SHIJYang/blog.git   (public, main)
#   子目录 frontend/                              (Vue3 + Vite8，package.json 在 frontend 下)
#   站点   sjy.qisont.art  ->  /www/wwwroot/sjy.qisont.art
#   用法   bash deploy-sjy.sh            正常执行（计划任务里用这个）
#          bash deploy-sjy.sh --force    忽略“已发布”判断，强制重建
#          bash deploy-sjy.sh --offline  不联网，直接部署本地已拉到的 origin/分支
#          bash deploy-sjy.sh --help     看参数说明
#
# ── v2 相对 v1 的改动（都在下面标注了 why）──────────────────────────────
#  1. 仓库已变成单仓三端（frontend/ + backend/ + apps/ + archive/），
#     部署目录不再需要完整历史 → 改浅克隆（--depth 1 --single-branch），
#     已有的完整克隆在下次 fetch 时自动瘦身。历史会一直长，深克隆只会越来越慢。
#  2. 脚本自身会被 git reset --hard 重写。若从仓库内直接运行，bash 边读边执行
#     会在文件被改写后读到错位内容（"unexpected EOF" 之类）。→ 启动时把自己
#     复制到 STATE_DIR 再重新 exec，保证运行中的脚本不被覆盖。
#  3. 构建前主动 rm -rf dist。Vite 的 emptyDir 在某些情况下会失败
#     （实测踩过：outDir 里有被占用的文件就报错中断），先清掉最省事。
#  4. 发布后只对 assets/ 做 --delete 清理。原来全站 SYNC_DELETE=0，带 hash 的
#     旧 JS/CSS 会无限堆积；但全站 --delete 又可能误删 uploads/ 之类非构建产物，
#     所以只精确清理 assets 子目录。
#  5. 空站点首次发布失败时不再执行 rsync --delete 回滚 —— 那会把目录清空，
#     属于"回滚把事故扩大"。
#  6. 加磁盘余量检查、Node 版本校验（package.json engines 是 ^20.19 || >=22.12，
#     Node 21 装上去会白屏但构建能过，提前拦住）。
#  7. 加 /api 健康探测，只告警不阻断 —— 后端切库期间接口短暂不可用是预期内的，
#     不能因此卡住前端发布。
#  8. 单仓之后，后端/文档提交也会改变 HEAD。若只按 commit 判断，每次改后端都会
#     重建一次前端（白花 40 秒 + 多一份备份）。改成比对 frontend/ 子树的 tree hash，
#     只有前端内容真的变了才构建。
#  9. backend/ 与前端发布无关，脚本不会碰它；后端上线是另一条链路。
#
#   与 card-table-score 的脚本差异：
#     * 只有 frontend 子目录有 package.json，必须在 frontend/ 里装依赖和构建
#     * 状态、备份、锁全部独立，两个站的定时任务互不干扰
#     * 健康检查容忍 301/302（站点强制 HTTPS 时 HTTP 会跳转，不能当成失败）
#     * 增加“产物校验”：发布的 index.html 必须与构建产物完全一致
###############################################################################

set -uo pipefail

# ------------------------------ 可调参数 ------------------------------
REPO_URL="https://github.com/SHIJYang/blog.git"
BRANCH="main"
SRC_DIR="/www/git/blog"                        # 整仓检出目录
APP_DIR="frontend"                             # 前端在仓库里的子目录
WEB_ROOT="/www/wwwroot/sjy.qisont.art"         # 站点根目录
BACKUP_ROOT="/www/backup/sjy-deploy"
LOG_FILE="/www/wwwlogs/deploy-sjy.log"
LOCK_FILE="/var/lock/deploy-sjy.lock"
STATE_DIR="/var/lib/deploy-sjy"
WEB_USER="www"
KEEP_BACKUPS=3
SYNC_DELETE=0                                  # 1 = 站点目录严格等于构建产物（会删掉 uploads 等非构建文件，慎用）
PRUNE_ASSETS=1                                 # 1 = 每次只清理 assets/ 下过期的带 hash 文件
REPO_DEPTH=1                                   # 克隆/拉取深度；0 = 完整历史（会越来越慢）
SKIP_IF_FRONTEND_UNCHANGED=1                   # 1 = 只有 frontend/ 内容变化才发布（单仓下后端提交不再触发前端重建）
MIN_FREE_MB=1024                               # 磁盘余量低于此值就中止，避免构建一半写满
NPM_REGISTRY="https://registry.npmmirror.com"
HEALTH_HOST="sjy.qisont.art"
HEALTH_PATH="/"                                # 主健康检查：首页
HEALTH_SPA_PATH="/home"                        # 附加检查：SPA 深链能否回退（只告警）
HEALTH_API_PATH="/api/public/configs/info"     # 附加检查：后端反代是否通（只告警）
FORCE=0
OFFLINE=0
for arg in "$@"; do
    case "$arg" in
        --force)   FORCE=1 ;;
        --offline) OFFLINE=1 ;;
        --help|-h)
            # 打印文件头注释，跳过边框行，遇到 set 就停
            awk 'NR>1 && /^set -uo/{exit} NR>1 && /^#{5,}$/{next} NR>1 {sub(/^# ?/,""); print}' "$0"
            exit 0 ;;
    esac
done

# ---- 网络抖动容错（服务器到 github.com 偶发超时时靠这几个参数扛） ----
FETCH_ATTEMPTS=3        # git clone/fetch 失败重试次数
FETCH_SLEEP=5           # 首次重试等待秒数，之后 5 -> 15 -> 45 递增
STRICT_FETCH=0          # 1 = fetch 重试全失败就报错退出；0 = 记 WARN 并跳过本轮
GIT_MIRROR_URL=""       # 可选镜像。填了就作为 clone 失败后的备选地址，例如：
                        #   GIT_MIRROR_URL="https://ghproxy.net/https://github.com/SHIJYang/blog.git"
GIT_LOW_SPEED_TIME=90   # 连续多少秒低速就判定连接卡死并断开（默认要 300 秒）
# ---------------------------------------------------------------------

ts()  { date '+%Y-%m-%d %H:%M:%S'; }
log() { echo "[$(ts)] $*" >>"$LOG_FILE"; }
say() { log "$*"; echo "[$(ts)] $*"; }
die() { say "ERROR: $*"; exit 1; }

mkdir -p "$(dirname "$LOG_FILE")" "$BACKUP_ROOT" "$STATE_DIR"

# 只在异常退出时补一条日志，正常退出不刷噪音
trap 'rc=$?; if [ "$rc" -ne 0 ]; then log "脚本异常退出，rc=$rc"; fi' EXIT

# ---------------------- 自我搬迁（避免被 git reset 覆盖） ----------------------
# 脚本放在仓库 scripts/ 下便于版本管理，但它会对 SRC_DIR 做 reset --hard。
# 如果正在执行的文件就在 SRC_DIR 里，被改写后 bash 会读到错位的字节。
case "$(readlink -f "$0" 2>/dev/null || echo "$0")" in
    "$SRC_DIR"/*)
        RUN_COPY="$STATE_DIR/deploy-sjy.run.sh"
        cp -f "$0" "$RUN_COPY" 2>/dev/null || die "无法复制脚本到 $RUN_COPY"
        chmod +x "$RUN_COPY"
        say "检测到脚本位于仓库内，已复制到 $RUN_COPY 并重新执行"
        exec bash "$RUN_COPY" "$@"
        ;;
esac

export GIT_TERMINAL_PROMPT=0    # 绝不允许卡在交互式账号密码提示上
GIT_OPTS=(-c http.version=HTTP/1.1
          -c http.lowSpeedLimit=1000
          -c "http.lowSpeedTime=$GIT_LOW_SPEED_TIME"
          -c core.askPass=/bin/true)

# git_try <git 子命令...>：带指数退避重试，输出统一写日志
git_try() {
    local attempt=1 wait_s=$FETCH_SLEEP
    while :; do
        if git "${GIT_OPTS[@]}" "$@" >>"$LOG_FILE" 2>&1; then
            return 0
        fi
        if [ "$attempt" -ge "$FETCH_ATTEMPTS" ]; then
            return 1
        fi
        log "git $1 第 $attempt/$FETCH_ATTEMPTS 次失败，${wait_s}s 后重试"
        sleep "$wait_s"
        attempt=$((attempt + 1))
        wait_s=$((wait_s * 3))
    done
}

# probe <路径>：优先走 HTTPS（带 SNI 指向本机），HTTPS 不通再退回 HTTP。
# 站点开了强制 HTTPS 时，走 HTTP 只会拿到 301 —— 那既可能是“正常跳转”，也可能是
# “真的 404 被跳转掩盖”，所以必须用 HTTPS 才能问出真实状态码。
probe() {
    local path="$1" code=""
    code=$(curl -sk -o /dev/null -m 10 -w '%{http_code}' \
                --resolve "$HEALTH_HOST:443:127.0.0.1" \
                "https://$HEALTH_HOST$path" 2>/dev/null)
    if [ -n "$code" ] && [ "$code" != "000" ]; then
        printf '%s' "$code"
        return
    fi
    # 退回 HTTP 探测：curl 失败时 -w 仍可能吐出 000，避免拼成 "000000"
    code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' -H "Host: $HEALTH_HOST" \
                "http://127.0.0.1$path" 2>/dev/null) || code="000"
    [ -n "$code" ] || code="000"
    printf '%s' "$code"
}

# 回滚：只在上一次发布确实有内容时才做，否则“回滚”等于把空目录 rsync 上去
HAS_OLD=0
BK=""
rollback() {
    if [ "$HAS_OLD" -eq 1 ] && [ -n "$BK" ] && [ -d "$BK" ]; then
        rsync -a --delete "$BK/" "$WEB_ROOT/" >>"$LOG_FILE" 2>&1
        chown -R "$WEB_USER:$WEB_USER" "$WEB_ROOT" 2>/dev/null
        say "已回滚到上一版本"
    else
        say "无可回滚的历史版本（首次发布），站点目录保持当前状态不动"
    fi
}

# ------------------------------ 单实例锁 ------------------------------
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    log "上一次任务仍在运行，本轮跳过"
    exit 0
fi

say "===== 开始检查 sjy.qisont.art ====="

# ------------------------------ 依赖预检 ------------------------------
for bin in git curl rsync; do
    command -v "$bin" >/dev/null 2>&1 || die "缺少命令 $bin（yum -y install git rsync curl / apt install -y git rsync curl）"
done

# package.json 的 engines 是 "^20.19.0 || >=22.12.0"：Node 21.x 两头都不占，
# 但 build 能过、页面白屏，这种错要到发布后才发现，所以先卡住。
# 注意不能简单地"取最高版本"——宝塔里可能同时装了 v21 和 v22，
# 取最高反而会撞上不兼容的那个，所以是"在兼容的里面取最高"。
node_ver_ok() {
    local v="$1" maj rest min
    maj=${v%%.*}; rest=${v#*.}; min=${rest%%.*}
    case "$maj" in ''|*[!0-9]*) return 1 ;; esac
    [ "$maj" -eq 20 ] && [ "${min:-0}" -ge 19 ] && return 0
    [ "$maj" -eq 22 ] && [ "${min:-0}" -ge 12 ] && return 0
    [ "$maj" -ge 23 ] && return 0
    return 1
}

NODE_BIN=""
BEST_VER=""
CANDIDATES=""
if [ -n "${NODE_BIN_OVERRIDE:-}" ]; then
    CANDIDATES="$NODE_BIN_OVERRIDE"
else
    for d in /www/server/nodejs/v*/bin; do
        [ -d "$d" ] && CANDIDATES="$CANDIDATES $d"
    done
    command -v node >/dev/null 2>&1 && CANDIDATES="$CANDIDATES $(dirname "$(command -v node)")"
fi
for d in $CANDIDATES; do
    [ -x "$d/node" ] || continue
    v=$("$d/node" -p 'process.versions.node' 2>/dev/null) || continue
    node_ver_ok "$v" || continue
    if [ -z "$BEST_VER" ] || [ "$(printf '%s\n%s\n' "$BEST_VER" "$v" | sort -V | tail -1)" = "$v" ]; then
        NODE_BIN="$d"
        BEST_VER="$v"
    fi
done

if [ -z "$NODE_BIN" ]; then
    FOUND=$(ls -d /www/server/nodejs/v*/bin 2>/dev/null | tr '\n' ' ')
    die "没有找到满足 engines（^20.19.0 || >=22.12.0）的 Node。已发现: ${FOUND:-无}。请在「软件商店 → Node.js 版本管理器」装 v22.12+ 或 v24"
fi
export PATH="$NODE_BIN:$PATH"
say "Node $BEST_VER ($NODE_BIN) / npm $(npm -v 2>/dev/null)"

# ------------------------------ 磁盘余量 ------------------------------
# 首次运行时 SRC_DIR 还不存在，直接 df 它会失败；失败时只告警，不能因为取不到
# 数值就拒绝发布（否则全新机器第一次永远部署不上去）。
DF_TARGET="$SRC_DIR"
[ -d "$DF_TARGET" ] || DF_TARGET="$(dirname "$SRC_DIR")"
[ -d "$DF_TARGET" ] || DF_TARGET="/"
FREE_MB=$(df -Pk "$DF_TARGET" 2>/dev/null | awk 'NR==2 {print int($4/1024)}')
if [ -n "${FREE_MB:-}" ] && [ "$FREE_MB" -ge 0 ] 2>/dev/null; then
    if [ "$FREE_MB" -lt "$MIN_FREE_MB" ]; then
        die "磁盘余量仅 ${FREE_MB}MB（< ${MIN_FREE_MB}MB），先清理再发布，避免构建中途写满"
    fi
    say "磁盘余量 ${FREE_MB}MB（下限 ${MIN_FREE_MB}MB）"
else
    say "WARN: 无法读取 $DF_TARGET 的磁盘余量，跳过检查"
fi

# ------------------------------ 首次克隆 ------------------------------
FRESH_CLONE=0
DEPTH_OPT=()
[ "$REPO_DEPTH" -gt 0 ] && DEPTH_OPT=(--depth "$REPO_DEPTH" --single-branch)

if [ ! -d "$SRC_DIR/.git" ]; then
    case "$SRC_DIR" in
        /www/git/*) : ;;
        *) die "SRC_DIR=$SRC_DIR 不在 /www/git 下，为安全起见拒绝清理重建" ;;
    esac
    say "克隆仓库到 $SRC_DIR（深度 ${REPO_DEPTH}，最多尝试 $FETCH_ATTEMPTS 轮）"
    mkdir -p "$(dirname "$SRC_DIR")"

    CLONE_URLS=("$REPO_URL")
    [ -n "${GIT_MIRROR_URL:-}" ] && CLONE_URLS+=("$GIT_MIRROR_URL")

    USED_URL=""
    for u in "${CLONE_URLS[@]}"; do
        rm -rf "$SRC_DIR"          # 清掉上一轮失败留下的半成品，避免“目录非空”报错
        if git_try clone "${DEPTH_OPT[@]}" --branch "$BRANCH" "$u" "$SRC_DIR"; then
            USED_URL="$u"
            say "克隆成功：$u"
            break
        fi
        log "从 $u 克隆失败"
    done
    [ -n "$USED_URL" ] || die "git clone 连续失败：服务器连不上 github.com（详见 $LOG_FILE）"
    FRESH_CLONE=1
fi

cd "$SRC_DIR" || die "无法进入 $SRC_DIR"
git config core.fileMode false
git remote set-url origin "${USED_URL:-$REPO_URL}"

# ------------------------------ 拉取比对 ------------------------------
# 刚 clone 下来的仓库里已经有 origin/<分支> 了，不必再 fetch 一次（少一次掉线机会）
if [ "$FRESH_CLONE" -eq 1 ]; then
    say "全新克隆，跳过本轮 fetch"
elif [ "$OFFLINE" -eq 1 ]; then
    say "离线模式（--offline），跳过 fetch，直接部署本地已有的 origin/$BRANCH"
elif ! git_try fetch ${DEPTH_OPT[@]+"${DEPTH_OPT[@]}"} --prune --no-tags origin "$BRANCH"; then
    if [ "$STRICT_FETCH" -eq 1 ]; then
        die "git fetch 连续 $FETCH_ATTEMPTS 次失败（github 连接问题）"
    fi
    say "WARN: git fetch 连续 $FETCH_ATTEMPTS 次失败，github 暂时连不上，本轮跳过，5 分钟后再试"
    exit 0
fi

REMOTE_SHA=$(git rev-parse "origin/$BRANCH") || die "无法解析 origin/$BRANCH"
LOCAL_SHA=$(git rev-parse HEAD 2>/dev/null || echo none)
LAST_SHA=$(cat "$STATE_DIR/last_sha" 2>/dev/null || echo none)
# frontend/ 子树的 tree hash：内容变了 hash 才变，与提交次数无关
REMOTE_FE_TREE=$(git rev-parse "origin/$BRANCH:$APP_DIR" 2>/dev/null || echo none)
LAST_FE_TREE=$(cat "$STATE_DIR/last_fe_tree" 2>/dev/null || echo none)

if [ "$REMOTE_SHA" = "$LAST_SHA" ] && [ "$FORCE" -eq 0 ]; then
    log "远端仍是已发布的 ${REMOTE_SHA:0:8}，无需部署"
    exit 0
fi

# 单仓特化：head 变了但 frontend/ 没变（改的是后端/文档/脚本）→ 不必重建前端
if [ "$SKIP_IF_FRONTEND_UNCHANGED" -eq 1 ] && [ "$FORCE" -eq 0 ] \
   && [ "$REMOTE_FE_TREE" != "none" ] && [ "$REMOTE_FE_TREE" = "$LAST_FE_TREE" ]; then
    say "新提交 ${LOCAL_SHA:0:8} -> ${REMOTE_SHA:0:8} 未改动 $APP_DIR/，跳过前端发布"
    echo "$REMOTE_SHA" >"$STATE_DIR/last_sha"
    exit 0
fi
say "新提交：${LOCAL_SHA:0:8} -> ${REMOTE_SHA:0:8}  $(git log -1 --format=%s "$REMOTE_SHA" 2>/dev/null | head -c 120)"

# 浅克隆下 reset --hard 依旧可用；仅当 HEAD 未对齐时才做
if [ "$LOCAL_SHA" != "$REMOTE_SHA" ]; then
    git reset --hard "origin/$BRANCH" >>"$LOG_FILE" 2>&1 || die "git reset --hard 失败"
fi

APP_PATH="$SRC_DIR/$APP_DIR"
[ -f "$APP_PATH/package.json" ] || die "找不到 $APP_PATH/package.json，仓库结构可能变了"
cd "$APP_PATH" || die "无法进入 $APP_PATH"

# ------------------------------ 安装依赖 ------------------------------
LOCK_HASH_FILE="$STATE_DIR/package_lock.sha"
NEW_LOCK_HASH=$(sha256sum package-lock.json 2>/dev/null | cut -d' ' -f1)
OLD_LOCK_HASH=$(cat "$LOCK_HASH_FILE" 2>/dev/null || echo none)
if [ -f package-lock.json ] && [ "$NEW_LOCK_HASH" = "$OLD_LOCK_HASH" ] && [ -d node_modules ]; then
    say "package-lock.json 未变化，跳过依赖安装"
elif [ -f package-lock.json ]; then
    say "安装依赖（npm ci）"
    npm ci --no-audit --no-fund --registry="$NPM_REGISTRY" >>"$LOG_FILE" 2>&1 \
        || { say "npm ci 失败，回退 npm install"; npm install --no-audit --no-fund --registry="$NPM_REGISTRY" >>"$LOG_FILE" 2>&1 || die "依赖安装失败"; }
    echo "$NEW_LOCK_HASH" >"$LOCK_HASH_FILE"
else
    say "无 package-lock.json，执行 npm install"
    npm install --no-audit --no-fund --registry="$NPM_REGISTRY" >>"$LOG_FILE" 2>&1 || die "依赖安装失败"
fi

# ------------------------------ 构建 ------------------------------
OUT_DIR="$APP_PATH/dist"
# Vite 的 emptyDir 遇到被占用/权限异常的文件会直接中断（实测踩过），先自己清干净
[ -d "$OUT_DIR" ] && rm -rf "$OUT_DIR"

say "开始构建（vite build）"
BUILD_LOG="$STATE_DIR/build.log"
if ! npm run build >>"$BUILD_LOG" 2>&1; then
    log "构建失败，最后 40 行输出："
    tail -n 40 "$BUILD_LOG" >>"$LOG_FILE"
    die "构建失败，线上站点未做任何改动"
fi

[ -f "$OUT_DIR/index.html" ] || die "找不到 $OUT_DIR/index.html（vite.config.js 里 build.outDir 变了？）"
say "构建完成：$OUT_DIR"

# ------------------------------ 发布 ------------------------------
[ -d "$WEB_ROOT" ] || die "站点目录 $WEB_ROOT 不存在，请先在面板添加站点"

BK="$BACKUP_ROOT/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BK"
if [ -n "$(ls -A "$WEB_ROOT" 2>/dev/null)" ]; then
    if cp -a "$WEB_ROOT/." "$BK/" 2>>"$LOG_FILE"; then
        HAS_OLD=1
        say "已备份到 $BK"
    else
        rm -rf "$BK"
        die "备份失败，中止发布（宁可不发，也不能没有退路）"
    fi
fi

RSYNC_OPTS=(-a --chmod=D755,F644)
[ "$SYNC_DELETE" -eq 1 ] && RSYNC_OPTS+=(--delete)
if ! rsync "${RSYNC_OPTS[@]}" "$OUT_DIR/" "$WEB_ROOT/" >>"$LOG_FILE" 2>&1; then
    say "发布失败，回滚"
    rollback
    die "rsync 失败"
fi

# 只清理 assets/：Vite 产物名带 hash，不清理会无限堆积；但不碰 uploads 等非构建目录
if [ "$PRUNE_ASSETS" -eq 1 ] && [ -d "$OUT_DIR/assets" ] && [ -d "$WEB_ROOT/assets" ]; then
    if rsync -a --delete --chmod=D755,F644 "$OUT_DIR/assets/" "$WEB_ROOT/assets/" >>"$LOG_FILE" 2>&1; then
        log "已清理 assets/ 下过期产物"
    else
        log "WARN: assets 清理失败（不影响本次发布）"
    fi
fi

chown -R "$WEB_USER:$WEB_USER" "$WEB_ROOT" 2>/dev/null || log "警告：chown $WEB_USER 失败"

# ------------------------------ 校验 + 健康检查 ------------------------------
SRC_INDEX_SUM=$(sha256sum "$OUT_DIR/index.html" | cut -d' ' -f1)
DST_INDEX_SUM=$(sha256sum "$WEB_ROOT/index.html" 2>/dev/null | cut -d' ' -f1)
if [ "$SRC_INDEX_SUM" != "$DST_INDEX_SUM" ]; then
    say "产物校验失败（index.html 不一致），回滚"
    rollback
    die "发布后文件与产物不一致"
fi

# 主检查：首页必须能通
CODE=$(probe "$HEALTH_PATH")
case "$CODE" in
    200|301|302|304) say "健康检查通过（HTTP $CODE）" ;;
    *)
        say "健康检查失败（HTTP $CODE），回滚"
        rollback
        die "健康检查 HTTP $CODE。检查 nginx 配置与 /api 反代"
        ;;
esac

# 附加检查：SPA 深链能否回退（只告警，不阻断发布）
SPA_CODE=$(probe "$HEALTH_SPA_PATH")
if [ "$SPA_CODE" = "200" ]; then
    say "SPA 回退正常（$HEALTH_SPA_PATH -> 200）"
else
    say "WARN: $HEALTH_SPA_PATH 返回 $SPA_CODE —— nginx 的 server{} 里还没有 location / { try_files \$uri \$uri/ /index.html; }，刷新深链会 404"
fi

# 附加检查：后端反代（只告警）。后端切库/重启期间不可用是预期内的，不能卡住前端发布
API_CODE=$(probe "$HEALTH_API_PATH")
if [ "$API_CODE" = "200" ]; then
    say "后端反代正常（$HEALTH_API_PATH -> 200）"
else
    say "WARN: $HEALTH_API_PATH 返回 $API_CODE —— 前端已发布但接口不通，页面会白屏。检查后端进程与 nginx 的 /api/ 反代"
fi

# ------------------------------ 收尾 ------------------------------
echo "$REMOTE_SHA" >"$STATE_DIR/last_sha"
echo "$REMOTE_FE_TREE" >"$STATE_DIR/last_fe_tree"
# 备份目录名不含空格，但用 -0 更稳妥
ls -1dt "$BACKUP_ROOT"/*/ 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | tr '\n' '\0' | xargs -0 -r rm -rf
say "发布完成：${REMOTE_SHA:0:8} -> $WEB_ROOT"
exit 0
