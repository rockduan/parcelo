# 设置 GitHub OAuth 配置
export GITHUB_OAUTH2_CLIENT_ID="Ov23liWEHm9u3dxLnWlo"
export GITHUB_OAUTH2_CLIENT_SECRET="be505887ddc1c75b25afa642a446badc3eca2a04"
export GITHUB_OAUTH2_REDIRECT_URL="http://localhost/auth/github/callback"
export PORT=8080
export HOST=localhost

# 设置 PostgreSQL 数据库配置
export POSTGRESQL_SERVER_NAME="localhost"
export POSTGRESQL_DATABASE_NAME="postgres"
export POSTGRESQL_PORT_NUMBER="5432"
export POSTGRESQL_USER="postgres"
export POSTGRESQL_PASSWORD="taiphone"
export POSTGRESQL_SSL_MODE="disable"
# 设置开发模式
export KTOR_ENV=development
export DEBUG_USER_GITHUB_ID=6718119
export DEBUG_USER_EMAIL="duanrock@foxmail.com"
export DEBUG_USER_REVIEWER_EMAIL="duanrock@foxmail.com"

# 设置 CORS 配置
export CORS_ALLOWED_HOST="localhost:8080"
export CORS_ALLOWED_SCHEME="http"

# 设置 BASE_URL
export BASE_URL="http://localhost:8080"

# 设置私有存储配置（本地MinIO示例）
export PRIVATE_STORAGE_BACKEND="S3"
#export PRIVATE_STORAGE_ENDPOINT_URL="http://127.0.0.1:9000"
export PRIVATE_STORAGE_ENDPOINT_URL="https://taiphone.net"
export PRIVATE_STORAGE_REGION="us-east-1"
export PRIVATE_STORAGE_BUCKET="parcelo-privatestorage"
export PRIVATE_STORAGE_ACCESS_KEY_ID="minioadmin"
export PRIVATE_STORAGE_SECRET_ACCESS_KEY="minioadmin"

# 设置S3发布服务配置（本地MinIO示例）
#export S3_ENDPOINT_URL="http://127.0.0.1:9000"
export S3_ENDPOINT_URL="https://taiphone.net"
export S3_REGION="us-east-1"
export S3_BUCKET="parcelo-repo"
export S3_ACCESS_KEY_ID="minioadmin"
export S3_SECRET_ACCESS_KEY="minioadmin"

echo "环境变量已设置完成！"
