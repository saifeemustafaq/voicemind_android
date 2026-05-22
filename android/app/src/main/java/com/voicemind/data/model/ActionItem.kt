package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class ActionItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val recordingId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val dueDate: Timestamp? = null,
    val deadline: Timestamp? = null,
    val notes: String? = null,
    val googleTaskId: String? = null,
    val calendarEventId: String? = null,
    val autoScheduled: Boolean = false,
    val sharedFromUid: String? = null,
    val sharedFromName: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Timestamp? = null,
)
