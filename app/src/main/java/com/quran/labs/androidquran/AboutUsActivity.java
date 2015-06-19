package com.quran.labs.androidquran;

import com.quran.labs.androidquran.ui.QuranActionBarActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AboutUsActivity extends QuranActionBarActivity {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.about_us);
        TextView txtAbout = (TextView) findViewById(R.id.txtAbout);
        txtAbout.setVerticalScrollBarEnabled(true);
        txtAbout.setText(Html.fromHtml(
            String.format(getString(R.string.aboutUs), getString(R.string.credits))));
        txtAbout.setMovementMethod(LinkMovementMethod.getInstance());

        Button button = (Button)findViewById(R.id.btnQuestions);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faqIntent = new Intent(view.getContext(), HelpActivity.class);
                startActivity(faqIntent);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
   }
}
