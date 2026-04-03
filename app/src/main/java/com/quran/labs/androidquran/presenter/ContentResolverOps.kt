package com.quran.labs.androidquran.presenter

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream

interface ContentResolverOps {
  fun openInputStream(uri: Uri): InputStream?
  fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor?
}
