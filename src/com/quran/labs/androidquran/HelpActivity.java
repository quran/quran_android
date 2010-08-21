package com.quran.labs.androidquran;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class HelpActivity extends Activity implements OnClickListener {
	protected Button btnBack;
	protected TextView txtHelp;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help);
        btnBack = (Button)findViewById(R.id.btnHelpBack); 
        btnBack.setOnClickListener(this);
        
        txtHelp = (TextView)findViewById(R.id.txtHelp);
        txtHelp.setMovementMethod(new ScrollingMovementMethod());
        
        if (getWindowManager().getDefaultDisplay().getHeight() < 
        		getWindowManager().getDefaultDisplay().getWidth())
        	txtHelp.setMaxLines(6);
        else
        	txtHelp.setMaxLines(16);
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnHelpBack:
			finish();
			break;
		default:
			break;
		}		
	}
}
