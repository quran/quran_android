package com.quran.labs.androidquran.fakes

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.quran.labs.androidquran.presenter.ContentResolverOps
import java.io.InputStream

class FakeContentResolverOps(
  private val inputStream: InputStream? = null,
  private val fileDescriptor: ParcelFileDescriptor? = null,
  private val fileDescriptorException: Throwable? = null
) : ContentResolverOps {
  override fun openInputStream(uri: Uri): InputStream? = inputStream

  override fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? {
    fileDescriptorException?.let { throw it }
    return fileDescriptor
  }
}
