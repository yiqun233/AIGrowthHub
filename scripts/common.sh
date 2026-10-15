#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f "$ROOT/.env" ]]; then echo '请先复制 .env.example 为 .env 并填写数据库密码。' >&2; exit 1; fi
python3 "$ROOT/scripts/local_env.py" "$ROOT/.env" --check
while IFS= read -r -d '' key && IFS= read -r -d '' value; do
 export "$key=$value"
done < <(python3 "$ROOT/scripts/local_env.py" "$ROOT/.env" --null)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:/usr/bin:$PATH"
