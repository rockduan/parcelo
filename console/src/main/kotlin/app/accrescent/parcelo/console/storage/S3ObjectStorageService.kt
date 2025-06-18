// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File
import app.accrescent.parcelo.console.data.Files
import app.accrescent.parcelo.console.data.Files.deleted
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider.Companion.invoke
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.utils.io.core.use
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

/**
 * Implementation of [ObjectStorageService] using S3-compatible storage as the backing store
 */
class S3ObjectStorageService(
    val s3EndpointUrl: Url,
    val s3Region: String,
    val s3Bucket: String,
    val s3AccessKeyId: String,
    private val s3SecretAccessKey: String,
) : ObjectStorageService {
    override suspend fun uploadFile(path: Path): EntityID<Int> {
        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            forcePathStyle = true
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val objectKey = generateObjectId()

            val fileBytes = java.nio.file.Files.readAllBytes(path)
            val preview = fileBytes.take(64).toByteArray()
            println("[uploadFile] PutObjectRequest params: bucket=$s3Bucket, key=$objectKey, fileSize=${fileBytes.size}, filePreview(hex)=${preview.joinToString(" ") { String.format("%02x", it) }}")

            val req = PutObjectRequest {
                bucket = s3Bucket
                key = objectKey
                body = path.asByteStream()
            }
            println("[uploadFile] PutObjectRequest created: $req")
            try {
                s3Client.putObject(req)
                println("[uploadFile] S3 putObject success: key=$objectKey")
            } catch (e: Exception) {
                println("[uploadFile] S3 putObject failed: key=$objectKey, error=${e.message}")
                throw e
            }

            val fileId = transaction { File.new { s3ObjectKey = objectKey }.id }

            return fileId
        }
    }

    override suspend fun uploadBytes(bytes: ByteArray): EntityID<Int> {
        println("[uploadBytes] ENV: endpointUrl=$s3EndpointUrl, region=$s3Region, bucket=$s3Bucket, accessKeyId=${s3AccessKeyId.take(4)}..., secretAccessKey=${s3SecretAccessKey.take(4)}...")
        println("[uploadBytes] S3Client will be constructed with: endpointUrl=$s3EndpointUrl, region=$s3Region, accessKeyId=${s3AccessKeyId.take(4)}..., secretAccessKey=${s3SecretAccessKey.take(4)}...")
        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            forcePathStyle = true
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val objectKey = generateObjectId()

            val preview = bytes.take(64).toByteArray()
            println("[uploadBytes] PutObjectRequest params: bucket=$s3Bucket, key=$objectKey, bytesSize=${bytes.size}, bytesPreview(hex)=${preview.joinToString(" ") { String.format("%02x", it) }}")

            val req = PutObjectRequest {
                bucket = s3Bucket
                key = objectKey
                body = ByteStream.fromBytes(bytes)
            }
            println("[uploadBytes] PutObjectRequest created: $req")
            try {
                s3Client.putObject(req)
                println("[uploadBytes] S3 putObject success: key=$objectKey")
            } catch (e: Exception) {
                println("[uploadBytes] S3 putObject failed: key=$objectKey, error=${e.message}")
                throw e
            }

            val fileId = transaction { File.new { s3ObjectKey = objectKey }.id }

            return fileId
        }
    }

    override suspend fun markDeleted(id: Int) {
        transaction { findFile(id)?.apply { deleted = true } } ?: return
    }

    override suspend fun cleanObject(id: Int) {
        val file = transaction {
            File.find { Files.id eq id and (deleted eq true) }.singleOrNull()
        } ?: return
        val s3ObjectKey = file.s3ObjectKey

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val req = DeleteObjectRequest {
                bucket = s3Bucket
                key = s3ObjectKey
            }
            s3Client.deleteObject(req)
        }

        transaction { file.delete() }
    }

    override suspend fun cleanAllObjects() {
        val files = transaction { File.find { deleted eq true } }

        val deleteObjectsRequest = transaction {
            files
                .map { ObjectIdentifier { key = it.s3ObjectKey } }
                .let { Delete { objects = it } }
                .let {
                    DeleteObjectsRequest {
                        bucket = s3Bucket
                        delete = it
                    }
                }
        }

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            s3Client.deleteObjects(deleteObjectsRequest)

            transaction { files.forEach { it.delete() } }
        }
    }

    override suspend fun <T> loadObject(id: EntityID<Int>, block: suspend (InputStream) -> T): T {
        val s3ObjectKey =
            transaction { findFile(id.value)?.s3ObjectKey } ?: throw FileNotFoundException()

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val req = GetObjectRequest {
                bucket = s3Bucket
                key = s3ObjectKey
            }
            val result = s3Client.getObject(req) { response ->
                response.body?.toInputStream()?.use { block(it) } ?: throw IOException()
            }

            return result
        }
    }
}
