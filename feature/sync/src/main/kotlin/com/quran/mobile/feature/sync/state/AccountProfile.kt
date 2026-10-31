package com.quran.mobile.feature.sync.state

import androidx.compose.runtime.Immutable
import com.quran.shared.auth.model.UserInfo

@Immutable
internal data class AccountProfile(
  val name: String?,
  val email: String?,
  val initials: String,
  val photoUrl: String?
)

internal fun accountProfile(user: UserInfo?): AccountProfile {
  val fullName = listOfNotNull(user?.firstName?.trim(), user?.lastName?.trim())
    .filter { it.isNotEmpty() }
    .joinToString(" ")
  val name = user?.name?.trim()?.ifEmpty { null } ?: fullName.ifEmpty { null }
  val email = user?.email?.trim()?.ifEmpty { null }
  return AccountProfile(
    name = name,
    email = email,
    initials = initials(name, email),
    photoUrl = user?.photoUrl?.trim()?.ifEmpty { null }
  )
}

private fun initials(name: String?, email: String?): String {
  if (name != null) {
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = listOfNotNull(words.firstOrNull(), words.lastOrNull()?.takeIf { words.size > 1 })
    return letters.joinToString("") { it.firstLetter() }.uppercase()
  }
  return email?.firstLetter()?.uppercase().orEmpty()
}

private fun String.firstLetter(): String = substring(0, Character.charCount(codePointAt(0)))
