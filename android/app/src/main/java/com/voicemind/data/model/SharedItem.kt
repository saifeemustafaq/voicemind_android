package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SharedItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val itemType: String = "",       // "recording" or "collectiveSummary"
    val itemId: String = "",
    @ServerTimestamp val sharedAt: Timestamp? = null,
    val isRead: Boolean = false,
)
