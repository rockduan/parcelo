#!/bin/bash

# Nginx 反向代理设置脚本

echo "=== 安装 Nginx ==="
sudo apt update
sudo apt install -y nginx

echo "=== 安装 SSL 证书工具 ==="
sudo apt install -y certbot python3-certbot-nginx

echo "=== 备份默认配置 ==="
sudo cp /etc/nginx/sites-available/default /etc/nginx/sites-available/default.backup

echo "=== 部署 MinIO 反向代理配置 ==="
sudo cp nginx-minio.conf /etc/nginx/sites-available/parcelo-minio

echo "=== 启用站点配置 ==="
sudo ln -sf /etc/nginx/sites-available/parcelo-minio /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

echo "=== 测试 Nginx 配置 ==="
sudo nginx -t

if [ $? -eq 0 ]; then
    echo "=== 重新加载 Nginx ==="
    sudo systemctl reload nginx
    sudo systemctl enable nginx
    
    echo "=== 获取 SSL 证书 ==="
    echo "请确保域名 taiphone.net 已正确解析到此服务器"
    echo "然后运行: sudo certbot --nginx -d taiphone.net"
    
    echo "=== 配置完成 ==="
    echo "现在可以通过以下地址访问："
    echo "https://taiphone.net/parcelo-repo/repodata.0.json"
else
    echo "Nginx 配置测试失败，请检查配置文件"
    exit 1
fi 