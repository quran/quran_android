package com.quran.labs.androidquran.presenter

interface Presenter<T> {
  fun bind(what: T)
  fun unbind(what: T)
}
