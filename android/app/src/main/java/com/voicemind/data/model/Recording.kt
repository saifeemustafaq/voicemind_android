package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Recording(
    @DocumentId val id: String = "",
    val title: String = "",
    val folderId: String = "unfiled",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val transcription: String? = null,
    val summary: String? = null,
    val audioPath: String = "",
    val durationSeconds: Long = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Timestamp? = null,
)
