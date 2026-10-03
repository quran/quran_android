package org.quran.app.domain

interface ReciterSelectionStore {
    fun selectedReciterId(): String
    fun select(id: String)
}
