#!/usr/bin/env sh
# 生成测试数据（转发到 python 脚本），用法见 README
set -e
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python3 "$DIR/make_test_data.py" "$@"
