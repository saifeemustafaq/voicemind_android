package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class CollectiveSummary(
    @DocumentId val id: String = "",
    val summary: String = "",
    val recordingIds: List<String> = emptyList(),
    val recordingTitles: List<String> = emptyList(),
    val sharedWith: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Timestamp? = null,
)
