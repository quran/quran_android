package com.quran.labs.androidquran;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class AboutUsActivity extends SherlockActivity {

	public void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
      super.onCreate(savedInstanceState);

      getSupportActionBar().setDisplayShowHomeEnabled(true);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		setContentView(R.layout.about_us);
		TextView txtAbout = (TextView) findViewById(R.id.txtAbout);
		txtAbout.setVerticalScrollBarEnabled(true);
      txtAbout.setText(Html.fromHtml(getString(R.string.aboutUs)));
      txtAbout.setMovementMethod(LinkMovementMethod.getInstance());

      Button button = (Button)findViewById(R.id.btnQuestions);
      button.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            onFaqClicked(view);
         }
      });
	}

	public void onFaqClicked(View v) {
		Intent faqIntent = new Intent(this, HelpActivity.class);
      startActivity(faqIntent);
	}

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == android.R.id.home){
         finish();
         return true;
      }
      return false;
   }
}
