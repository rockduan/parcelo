package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File
import app.accrescent.parcelo.console.data.Files
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.ObjectMetadata
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Files as JavaFiles
import java.nio.file.Path
import java.util.UUID

class AliyunOSSObjectStorageService(
    private val endpoint: String,
    private val bucket: String,
    private val accessKeyId: String,
    private val accessKeySecret: String,
) : ObjectStorageService, AutoCloseable {
    private val ossClient: OSS = OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)

    override suspend fun uploadFile(path: Path): EntityID<Int> {
        val key = UUID.randomUUID().toString()
        val metadata = ObjectMetadata().apply {
            contentType = JavaFiles.probeContentType(path)
            contentLength = JavaFiles.size(path)
        }

        JavaFiles.newInputStream(path).use { inputStream ->
            ossClient.putObject(bucket, key, inputStream, metadata)
        }

        return transaction {
            File.new {
                this.s3ObjectKey = key
                this.deleted = false
            }.id
        }
    }

    override suspend fun uploadBytes(bytes: ByteArray): EntityID<Int> {
        val key = UUID.randomUUID().toString()
        val metadata = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
        }

        ByteArrayInputStream(bytes).use { inputStream ->
            ossClient.putObject(bucket, key, inputStream, metadata)
        }

        return transaction {
            File.new {
                this.s3ObjectKey = key
                this.deleted = false
            }.id
        }
    }

    override suspend fun markDeleted(id: Int) {
        transaction {
            File.findById(id)?.apply {
                deleted = true
            }
        }
    }

    override suspend fun cleanObject(id: Int) {
        val file = transaction {
            File.findById(id)?.takeIf { it.deleted }
        } ?: return

        try {
            file.s3ObjectKey?.let { key ->
                ossClient.deleteObject(bucket, key)
                transaction {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Log error but don't throw
            println("Failed to delete object ${file.s3ObjectKey}: ${e.message}")
        }
    }

    override suspend fun cleanAllObjects() {
        transaction {
            File.find { Files.deleted eq true }.forEach { file ->
                try {
                    file.s3ObjectKey?.let { key ->
                        ossClient.deleteObject(bucket, key)
                        file.delete()
                    }
                } catch (e: Exception) {
                    // Log error but don't throw
                    println("Failed to delete object ${file.s3ObjectKey}: ${e.message}")
                }
            }
        }
    }

    override suspend fun <T> loadObject(id: EntityID<Int>, block: suspend (InputStream) -> T): T {
        val file = transaction {
            File.findById(id)?.takeIf { !it.deleted }
        } ?: throw java.nio.file.NoSuchFileException("File not found or deleted")

        val key = file.s3ObjectKey ?: throw NoSuchFileException("File has no object key")
        return ossClient.getObject(bucket, key).objectContent.use { inputStream ->
            block(inputStream)
        }
    }

    override fun close() {
        ossClient.shutdown()
    }
} 