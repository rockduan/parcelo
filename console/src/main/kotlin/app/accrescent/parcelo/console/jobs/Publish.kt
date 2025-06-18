// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.Draft as DraftDao
import app.accrescent.parcelo.console.data.Update as UpdateDao
import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.publish.PublishService
import app.accrescent.parcelo.console.storage.ObjectStorageService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.java.KoinJavaComponent.inject
import org.koin.java.KoinJavaComponent.get
import java.util.UUID
import app.accrescent.parcelo.console.Config
import java.io.InputStream

/**
 * Publishes the draft with the given ID, making it available for download
 */
fun registerPublishAppJob(draftId: UUID) {
    println("[registerPublishAppJob] called with draftId=$draftId")
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val draft = transaction { DraftDao.findById(draftId) } ?: run {
        println("[registerPublishAppJob] draft not found for draftId=$draftId")
        return
    }
    val iconFileId =
        transaction { Icon.findById(draft.iconId)?.fileId } ?: throw IllegalStateException()
    println("[registerPublishAppJob] iconFileId=$iconFileId")

    // Publish to the repository
    val metadata = runBlocking {
        if (storageService is app.accrescent.parcelo.console.storage.S3ObjectStorageService) {
            val s3 = storageService as app.accrescent.parcelo.console.storage.S3ObjectStorageService
            println("[registerPublishAppJob] storageService config: endpoint=${s3.s3EndpointUrl}, region=${s3.s3Region}, bucket=${s3.s3Bucket}, accessKeyId=${s3.s3AccessKeyId}")
        } else {
            println("[registerPublishAppJob] storageService is not S3ObjectStorageService, actual type: ${storageService::class.qualifiedName}")
        }
        println("[registerPublishAppJob] before loading draft fileId=${draft.fileId}")
        storageService.loadObject(draft.fileId) { draftStream: InputStream ->
            println("[registerPublishAppJob] loaded draft fileId=${draft.fileId}")
            println("[registerPublishAppJob] before loading icon fileId=$iconFileId")
            storageService.loadObject(iconFileId) { iconStream: InputStream ->
                println("[registerPublishAppJob] loaded icon fileId=$iconFileId")
                println("[registerPublishAppJob] calling publishService.publishDraft ...")
                publishService.publishDraft(draftStream, iconStream, draft.shortDescription)
            }
        }
    }
    println("[registerPublishAppJob] publishService.publishDraft finished")

    // Account for publication
    transaction {
        draft.delete()
        val app = App.new(draft.appId) {
            versionCode = draft.versionCode
            versionName = draft.versionName
            fileId = draft.fileId
            reviewIssueGroupId = draft.reviewIssueGroupId
            repositoryMetadata = ExposedBlob(metadata)
        }
        println("[registerPublishAppJob] App created: appId=${app.id}")
        Listing.new {
            appId = app.id
            locale = "en-US"
            iconId = draft.iconId
            label = draft.label
            shortDescription = draft.shortDescription
        }
        println("[registerPublishAppJob] Listing created for appId=${app.id}")
        AccessControlList.new {
            this.userId = draft.creatorId
            appId = app.id
            update = true
            editMetadata = true
        }
        println("[registerPublishAppJob] AccessControlList created for appId=${app.id}")
    }
}

/**
 * Publishes the update with the given ID, making it available for download
 */
fun registerPublishUpdateJob(updateId: UUID) {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val update = transaction { UpdateDao.findById(updateId) } ?: return

    // Publish to the repository
    val updatedMetadata = runBlocking {
        storageService.loadObject(update.fileId!!) {
            runBlocking { publishService.publishUpdate(it, update.appId.value) }
        }
    }

    // Account for publication
    val oldAppFileId = transaction {
        App.findById(update.appId)?.run {
            versionCode = update.versionCode
            versionName = update.versionName
            repositoryMetadata = ExposedBlob(updatedMetadata)

            val oldAppFileId = fileId
            fileId = update.fileId!!

            update.published = true
            updating = false

            oldAppFileId
        }
    }

    // Delete old app file
    if (oldAppFileId != null) {
        runBlocking { storageService.markDeleted(oldAppFileId.value) }
        BackgroundJob.enqueue { cleanFile(oldAppFileId.value) }
    }
}
