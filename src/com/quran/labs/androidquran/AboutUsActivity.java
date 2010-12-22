package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AboutUsActivity extends Activity implements OnClickListener {
	protected Button btnEmailUs;
	protected TextView txtAbout;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_us);
        
        btnEmailUs = (Button)findViewById(R.id.btnEmailUs);
        btnEmailUs.setOnClickListener(this);
        
        txtAbout = (TextView)findViewById(R.id.txtAbout);
        txtAbout.setVerticalScrollBarEnabled(true);
        txtAbout.setMovementMethod(new ScrollingMovementMethod());
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btnEmailUs:
				final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);			 
				emailIntent.setType("plain/text");						 
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
				emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{getString(R.string.email_to)});
				startActivity(Intent.createChooser(emailIntent, "Send mail..."));
				break;
			default:
				break;
		}		
	}
}
