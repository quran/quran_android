package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AboutUsActivity extends Activity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_us);
		TextView txtAbout = (TextView) findViewById(R.id.txtAbout);
		txtAbout.setVerticalScrollBarEnabled(true);
		txtAbout.setMovementMethod(new ScrollingMovementMethod());

      Button button = (Button)findViewById(R.id.btnEmailUs);
      button.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            onEmailClick(view);
         }
      });
	}

	public void onEmailClick(View v) {
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { getString(R.string.email_to) });
		startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)));
	}
}
