package com.example.mycalculator.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistoryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val historyCollection = db.collection("history")

    fun saveHistory(
        entry: HistoryEntry,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        historyCollection
            .add(entry)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun loadRecentHistory(
        limit: Long = 20,
        onSuccess: (List<HistoryEntry>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        historyCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(HistoryEntry::class.java)
                }
                onSuccess(items)
            }
            .addOnFailureListener(onError)
    }

    fun clearAllHistory(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        historyCollection.get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onError)
            }.addOnFailureListener(onError)
    }

    fun enforceLimit(limit: Int = 20) {
        historyCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.size() > limit) {
                    val documentsToDelete = snapshot.documents.drop(limit)
                    val batch = db.batch()
                    for (doc in documentsToDelete) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                }
            }
    }
}