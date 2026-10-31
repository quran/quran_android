package com.quran.mobile.feature.sync.state

import com.quran.shared.auth.model.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountProfileTest {
  @Test fun `name falls back to first and last name`() {
    assertEquals("Aisha Rahman", accountProfile(user(name = "  Aisha Rahman ")).name)
    assertEquals("Aisha Rahman", accountProfile(user(name = " ", firstName = "Aisha", lastName = "Rahman")).name)
    assertEquals("Aisha", accountProfile(user(firstName = " Aisha ", lastName = " ")).name)
    assertNull(accountProfile(user(firstName = " ")).name)
  }

  @Test fun `initials come from the first and last words of the name`() {
    assertEquals("AR", accountProfile(user(name = "aisha bint  rahman")).initials)
    assertEquals("A", accountProfile(user(name = "Aisha")).initials)
    assertEquals("عر", accountProfile(user(name = "عائشة رحمن")).initials)
  }

  @Test fun `initials fall back to the email and then to none`() {
    val profile = accountProfile(user(email = " aisha@example.com "))
    assertEquals("aisha@example.com", profile.email)
    assertEquals("A", profile.initials)
    assertEquals("", accountProfile(user(email = " ")).initials)
    assertEquals("", accountProfile(null).initials)
  }

  @Test fun `blank photo url means no photo`() {
    assertNull(accountProfile(user(photoUrl = " ")).photoUrl)
    assertEquals("https://example.com/a.png", accountProfile(user(photoUrl = "https://example.com/a.png")).photoUrl)
  }

  private fun user(
    name: String? = null,
    firstName: String? = null,
    lastName: String? = null,
    email: String? = null,
    photoUrl: String? = null
  ) = UserInfo(
    id = "1",
    firstName = firstName,
    lastName = lastName,
    name = name,
    email = email,
    photoUrl = photoUrl
  )
}
