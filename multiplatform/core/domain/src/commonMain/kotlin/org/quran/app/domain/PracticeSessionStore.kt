package org.quran.app.domain

import org.quran.app.model.PracticeSessionSnapshot

interface PracticeSessionStore {
    fun read(): PracticeSessionSnapshot?
    fun save(snapshot: PracticeSessionSnapshot)
    fun clear()
}
