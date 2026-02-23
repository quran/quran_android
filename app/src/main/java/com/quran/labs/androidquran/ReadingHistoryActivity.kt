package com.quran.labs.androidquran

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadingHistoryActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val recyclerView = RecyclerView(this)
    recyclerView.layoutManager = LinearLayoutManager(this)
    setContentView(recyclerView)
    
    title = "Reading History"
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val app = application as QuranApplication
    val model = app.applicationComponent.recentPageModel()

    model.getReadingHistory()
      .subscribe({ historyList ->
        runOnUiThread {
          recyclerView.adapter = HistoryAdapter(historyList) { page ->
            val intent = Intent(this, QuranDataActivity::class.java)
            intent.putExtra("pageNumber", page)
            startActivity(intent)
          }
        }
      }, {})
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }
}

class HistoryAdapter(
  private val items: List<com.quran.data.model.bookmark.ReadingHistory>,
  private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

  class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val pageText: TextView = TextView(view.context).also {
      (view as ViewGroup).addView(it)
    }
    val dateText: TextView = TextView(view.context).also {
      (view as ViewGroup).addView(it)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val container = android.widget.LinearLayout(parent.context).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      setPadding(32, 24, 32, 24)
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }
    return ViewHolder(container)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = items[position]
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val date = Date(item.visited_date * 1000)
    
    holder.pageText.text = "Page ${item.page}"
    holder.pageText.textSize = 18f
    holder.dateText.text = dateFormat.format(date)
    holder.dateText.textSize = 14f
    
    holder.itemView.setOnClickListener { onItemClick(item.page) }
  }

  override fun getItemCount() = items.size
}
