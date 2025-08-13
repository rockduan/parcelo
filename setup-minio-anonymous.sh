#!/bin/bash

# MinIO 匿名访问设置脚本
# 在 MinIO 服务器 (172.16.3.19) 上执行

echo "设置 MinIO 匿名访问权限..."

# 1. 安装 MinIO Client (如果未安装)
if ! command -v mc &> /dev/null; then
    echo "安装 MinIO Client..."
    wget https://dl.min.io/client/mc/release/linux-amd64/mc
    chmod +x mc
    sudo mv mc /usr/local/bin/
fi

# 2. 配置 MinIO 别名
mc alias set myminio http://127.0.0.1:80 minioadmin minioadmin

# 3. 设置 bucket 匿名下载权限
echo "设置 parcelo-repo bucket 匿名下载权限..."
mc anonymous set download myminio/parcelo-repo

# 4. 验证设置
echo "验证匿名访问设置..."
mc anonymous get myminio/parcelo-repo

echo "MinIO 匿名访问设置完成！"
echo "现在可以通过以下地址访问："
echo "http://taiphone.net:9000/parcelo-repo/repodata.0.json" 
