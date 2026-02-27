package com.voicemind.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Folder(
    @DocumentId val id: String = "",
    val name: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    companion object {
        const val UNFILED_ID = "unfiled"

        val DEFAULTS = listOf(
            Folder(id = "work", name = "Work"),
            Folder(id = "meetings", name = "Meetings"),
            Folder(id = "ideas", name = "Ideas"),
            Folder(id = "personal", name = "Personal"),
            Folder(id = "journal", name = "Journal"),
            Folder(id = "archive", name = "Archive"),
            Folder(id = UNFILED_ID, name = "Unfiled"),
        )
    }
}
