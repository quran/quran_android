package com.quran.recitation.presenter

interface Presenter<T : Any> {
  fun bind(what: T)
  fun unbind(what: T)
}
