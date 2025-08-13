#!/bin/bash

# 连接测试脚本

echo "=== 测试域名解析 ==="
nslookup taiphone.net

echo -e "\n=== 测试端口连通性 ==="
nc -zv taiphone.net 9000

echo -e "\n=== 测试 MinIO 连接 ==="
curl -I http://taiphone.net:9000/parcelo-repo/repodata.0.json

echo -e "\n=== 测试 MinIO 控制台 ==="
curl -I http://taiphone.net:9000/

echo -e "\n=== 测试 bucket 列表 ==="
curl -s http://taiphone.net:9000/parcelo-repo/ | head -20 