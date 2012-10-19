package com.quran.labs.androidquran;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class HelpActivity extends SherlockActivity implements OnClickListener {
	protected Button mEmailButton;
	protected TextView mHelpText;

	public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.help);
        mEmailButton = (Button)findViewById(R.id.btnEmailUs);
        mEmailButton.setOnClickListener(this);

        mHelpText = (TextView)findViewById(R.id.txtHelp);
        mHelpText.setText(Html.fromHtml(getString(R.string.help)));
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnEmailUs:
         Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
         emailIntent.setType("plain/text");
         String body = "\n\n";
         try {
        	 PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        	 body = pInfo.packageName + " Version: " + pInfo.versionName;
         } catch (Exception e) {}
         
         try {
	         body += "\nPhone: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
	         body += "\nAndroid Version: " + android.os.Build.VERSION.CODENAME + " " 
	        		 + android.os.Build.VERSION.RELEASE;
         } catch (Exception e) {}
         
         emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
         emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                 getString(R.string.email_subject));
         emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
                 new String[] { getString(R.string.email_to) });
         startActivity(Intent.createChooser(emailIntent,
                 getString(R.string.send_email)));
			break;
		default:
			break;
		}		
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
