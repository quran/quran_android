package com.quran.labs.androidquran;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

public class QuranJumpDialog extends Dialog {

	private Integer page = null;
	
	public QuranJumpDialog(Context context){
		super(context);
	}
	
	@Override 
    public void onCreate(Bundle savedInstanceState) { 
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.jump_to);
        
        showJumpDialog();
	}
	
	public void showJumpDialog(){
		setTitle(R.string.jump_dialog_title);
		Button gotoButton = (Button)this.findViewById(R.id.jumpButton);
		gotoButton.setOnClickListener(new View.OnClickListener(){
			public void onClick(View v){ jump(); }
		});

		Button cancel = (Button)this.findViewById(R.id.cancelButton);
		cancel.setOnClickListener(new View.OnClickListener(){
			public void onClick(View v){
				page = null;
				leave();
			}
		});
		
		EditText pageField = (EditText)this.findViewById(R.id.page_field);
        InputMethodManager imm =
        	(InputMethodManager)pageField.getContext().
        	getSystemService(Context.INPUT_METHOD_SERVICE); 
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
	}
	
	public Integer getPage(){ return page; }
	
	public void jump(){
		EditText pageField = (EditText)this.findViewById(R.id.page_field);
		int page = 0;
		try {
			page = Integer.parseInt(pageField.getText().toString().trim());
		}
		catch (NumberFormatException nf){ page = 0; }

		if ((page > 0) && (page < 605)){
			this.page = page;
		}
		else this.page = null;
		leave();
	}
	
	// http://groups.google.com/group/android-developers/browse_frm/
	//    thread/17210d784766602d/d430c900a9c4019c?
	public void leave(){
		EditText pageField = (EditText)this.findViewById(R.id.page_field);
		InputMethodManager imm =
        	(InputMethodManager)pageField.getContext().
        	getSystemService(Context.INPUT_METHOD_SERVICE); 
		imm.hideSoftInputFromWindow(pageField.getWindowToken(), 0); 
		this.cancel();
	}
}
