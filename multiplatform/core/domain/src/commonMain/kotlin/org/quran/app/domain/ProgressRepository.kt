package org.quran.app.domain

import org.quran.app.model.StudyProgress

interface ProgressRepository {
    fun read(): StudyProgress
    fun save(progress: StudyProgress)
}
