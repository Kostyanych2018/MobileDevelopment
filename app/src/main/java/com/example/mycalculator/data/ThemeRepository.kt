package com.example.mycalculator.data

import com.google.firebase.firestore.FirebaseFirestore

class ThemeRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val themeDoc = db.collection("app_config").document("theme3")

    fun loadTheme(
        onSuccess: (Map<String, String?>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        themeDoc.get()
            .addOnSuccessListener { doc ->
                val result = mapOf(
                    "background" to doc.getString("background"),
                    "numberButton" to doc.getString("numberButton"),
                    "operatorButton" to doc.getString("operatorButton"),
                    "utilityButton" to doc.getString("utilityButton"),
                    "text" to doc.getString("text"),
                    "statusBar" to doc.getString("statusBar")
                )
                onSuccess(result)
            }
            .addOnFailureListener(onError)
    }

    fun saveTheme(
        theme: Map<String, String>,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        themeDoc.set(theme)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }
}