package app.accrescent.parcelo.console.publish

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.accrescent.parcelo.console.repo.RepoData
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.storage.S3ObjectStorageService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

@kotlinx.serialization.Serializable
data class RepoIndex(
    val timestamp: Long,
    val apps: Map<String, AppInfo>
)

@kotlinx.serialization.Serializable
data class AppInfo(
    val name: String,
    @kotlinx.serialization.SerialName("min_version_code")
    val minVersionCode: Int,
    @kotlinx.serialization.SerialName("signing_cert_hashes")
    val signingCertHashes: List<String>
)

// 重载版本：直接接受 S3ObjectStorageService 实例
suspend fun generateRepoIndex(s3Service: S3ObjectStorageService, secretAccessKey: String) {
    generateRepoIndex(
        endpointUrl = s3Service.s3EndpointUrl.toString(),
        region = s3Service.s3Region,
        bucket = s3Service.s3Bucket,
        accessKeyId = s3Service.s3AccessKeyId,
        secretAccessKey = secretAccessKey
    )
}

suspend fun generateRepoIndex(
    endpointUrl: String,
    region: String,
    bucket: String,
    accessKeyId: String,
    secretAccessKey: String
) {
    println("[generateRepoIndex] Starting generation of repodata.0.json")
    
    val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
    }
    
    // 从数据库获取所有应用信息
    val appsInfo = transaction {
        App.all().associate { app ->
            val listing = Listing.find { 
                Listings.appId eq app.id and (Listings.locale eq "en-US") 
            }.singleOrNull()
            
            app.id.value to AppInfo(
                name = listing?.label ?: app.id.value,
                minVersionCode = app.versionCode,
                signingCertHashes = listOf("a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef1234") // 示例hash，实际应从APK中提取
            )
        }
    }
    
    println("[generateRepoIndex] Found ${appsInfo.size} apps in database")
    
    // 创建聚合索引
    val repoIndex = RepoIndex(
        timestamp = System.currentTimeMillis() / 1000, // Unix timestamp in seconds
        apps = appsInfo
    )
    
    val repoIndexJson = json.encodeToString(repoIndex)
    val repoIndexBytes = repoIndexJson.toByteArray()
    
    println("[generateRepoIndex] Generated repodata.0.json with ${appsInfo.size} apps")
    println("[generateRepoIndex] JSON content: $repoIndexJson")
    
    // 上传到S3
    S3Client {
        this.endpointUrl = aws.smithy.kotlin.runtime.net.url.Url.parse(endpointUrl)
        this.region = region
        this.forcePathStyle = true
        this.credentialsProvider = StaticCredentialsProvider {
            this.accessKeyId = accessKeyId
            this.secretAccessKey = secretAccessKey
        }
    }.use { s3Client ->
        val putRequest = PutObjectRequest {
            this.bucket = bucket
            this.key = "repodata.0.json"
            this.body = ByteStream.fromBytes(repoIndexBytes)
        }
        
        try {
            s3Client.putObject(putRequest)
            println("[generateRepoIndex] Successfully uploaded repodata.0.json to s3://$bucket/repodata.0.json")
        } catch (e: Exception) {
            println("[generateRepoIndex] Failed to upload repodata.0.json: ${e.message}")
            throw e
        }
    }
} 