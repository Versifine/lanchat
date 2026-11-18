#!/usr/bin/env bash
# LanChat 文件上传吞吐测试（EV-PERF-05 / EV-SEC-06）
#
# 流程：注册一个测试用户 → 生成 20MB 随机文件 → curl 循环上传 N 次计时 →
#       校验响应 SHA-256 与本地一致 → 额外验证 21MB 上传返回 413/1006。
# 结果写入 assets/bench/file_upload.csv。
#
# 用法：
#   tools/bench/file_upload_bench.sh
#   HTTP_BASE=http://localhost:8081 ROUNDS=5 SIZE_MB=20 tools/bench/file_upload_bench.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

HTTP_BASE="${HTTP_BASE:-http://localhost:8081}"
ROUNDS="${ROUNDS:-5}"
SIZE_MB="${SIZE_MB:-20}"
OUT="${OUT:-assets/bench/file_upload.csv}"
TMP_DIR="$(mktemp -d /tmp/lanchat-filebench-XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$(dirname "$OUT")"

if command -v jq >/dev/null 2>&1; then
  JSON_GET() { jq -r "$1" 2>/dev/null || true; }
else
  JSON_GET() { sed -n "s/.*\"${1#.}\":\"\\([^\"]*\\)\".*/\\1/p"; }
fi

echo "== LanChat 文件上传吞吐测试 =="
echo "目标: $HTTP_BASE  轮数: $ROUNDS  单文件: ${SIZE_MB}MB  输出: $OUT"

TS="$(date +%s)"
USERNAME="upl_${TS}"
PASSWORD="bench123"
REGISTER_RESPONSE="$(curl -s -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"上传测试\"}" \
  "$HTTP_BASE/api/auth/register")"
TOKEN="$(printf '%s' "$REGISTER_RESPONSE" | JSON_GET .token)"
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "注册测试用户失败: $REGISTER_RESPONSE" >&2
  exit 1
fi
echo "测试用户: $USERNAME"

FILE="$TMP_DIR/payload-${SIZE_MB}mb.bin"
head -c $((SIZE_MB * 1024 * 1024)) /dev/urandom > "$FILE"
LOCAL_SIZE="$(stat -c %s "$FILE")"
LOCAL_SHA="$(sha256sum "$FILE" | awk '{print $1}')"
echo "本地文件: $LOCAL_SIZE 字节, SHA-256=$LOCAL_SHA"

{
  echo "round,file_mb,http_code,time_total_s,speed_upload_mbps,size_upload_bytes,sha256_match,note"
} > "$OUT"

SPEEDS=()
for ((i = 1; i <= ROUNDS; i++)); do
  METRICS="$(curl -s -o "$TMP_DIR/resp.json" -w '%{http_code},%{size_upload},%{time_total},%{speed_upload}' \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$FILE;filename=bench_${SIZE_MB}mb.bin" \
    "$HTTP_BASE/api/files")"
  IFS=',' read -r HTTP_CODE UPLOAD_BYTES TIME_TOTAL SPEED_BYTES <<< "$METRICS"
  MBPS="$(awk -v s="$SPEED_BYTES" 'BEGIN{printf "%.2f", s/1048576}')"
  RESP_SHA="$(JSON_GET .file.sha256 < "$TMP_DIR/resp.json")"
  if [ "$RESP_SHA" = "$LOCAL_SHA" ]; then MATCH="yes"; else MATCH="no"; fi
  echo "$i,$SIZE_MB,$HTTP_CODE,$TIME_TOTAL,$MBPS,$UPLOAD_BYTES,$MATCH,\"单连接 multipart 上传 ${SIZE_MB}MB\"" >> "$OUT"
  SPEEDS+=("$MBPS")
  echo "  第 $i 轮: HTTP $HTTP_CODE, 用时 ${TIME_TOTAL}s, 吞吐 ${MBPS} MB/s, sha256 一致=$MATCH"
done

AVG="$(printf '%s\n' "${SPEEDS[@]}" | awk '{s+=$1;n++} END{if(n>0) printf "%.2f", s/n; else print "0"}')"
echo "avg,$SIZE_MB,200,,$AVG,,yes,\"$ROUNDS 轮平均上传吞吐（EV-PERF-05 目标 ≥ 20 MB/s）\"" >> "$OUT"

# EV-SEC-06：21MB 必须被拒绝（413 / code 1006）
OVERSIZE_FILE="$TMP_DIR/payload-21mb.bin"
head -c $((21 * 1024 * 1024)) /dev/urandom > "$OVERSIZE_FILE"
OVERSIZE_METRICS="$(curl -s -o "$TMP_DIR/oversize.json" -w '%{http_code},%{size_upload},%{time_total}' \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$OVERSIZE_FILE;filename=oversize21mb.bin" \
  "$HTTP_BASE/api/files")"
IFS=',' read -r OV_CODE OV_BYTES OV_TIME <<< "$OVERSIZE_METRICS"
OV_CODE_IN_BODY="$(JSON_GET .code < "$TMP_DIR/oversize.json")"
echo "oversize,21,$OV_CODE,$OV_TIME,,$OV_BYTES,,\"EV-SEC-06 21MB 上传应 413/1006（响应 code=$OV_CODE_IN_BODY）\"" >> "$OUT"

echo
echo "== 结果摘要 =="
echo "平均上传吞吐: $AVG MB/s（目标 ≥ 20 MB/s）"
echo "21MB 超限: HTTP $OV_CODE / code=${OV_CODE_IN_BODY:-?}（期望 413 / 1006）"
echo "CSV: $OUT"
