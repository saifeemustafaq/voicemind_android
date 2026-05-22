package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class MyShare(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val recipientEmail: String = "",
    val itemType: String = "",
    val itemId: String = "",
    @ServerTimestamp val sharedAt: Timestamp? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Timestamp? = null,
)
