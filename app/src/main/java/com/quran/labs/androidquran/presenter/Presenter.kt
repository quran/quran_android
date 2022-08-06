package com.quran.labs.androidquran.presenter

interface Presenter<T : Any> {
  fun bind(what: T)
  fun unbind(what: T)
}
