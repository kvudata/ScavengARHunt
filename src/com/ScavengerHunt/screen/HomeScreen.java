package com.ScavengerHunt.screen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.ScavengerHunt.R;

public class HomeScreen extends Activity {
    /** Called when the activity is first created. */
	private Button playButton;
	private Button helpButton;
	private Button aboutUsButton;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        this.findAllViewsById();
        
        playButton.setOnClickListener(buttonListener);
        helpButton.setOnClickListener(buttonListener);
        aboutUsButton.setOnClickListener(buttonListener);
        
               
    }
    
    private void findAllViewsById() {
    	playButton = (Button) findViewById(R.id.play_button);
    	helpButton = (Button) findViewById(R.id.help_button);
    	aboutUsButton = (Button) findViewById(R.id.aboutUs_button);
    }
    
    private OnClickListener buttonListener = new OnClickListener() {
    	public void onClick(View v){
    		Button button = (Button) v;
    		if (button.getId() == playButton.getId()){
    			//Toast toast = Toast.makeText(getApplicationContext(), "play!!!!!", Toast.LENGTH_SHORT);
    			//toast.show();
    			Intent i = new Intent(v.getContext(), PlayHomeScreen.class);
    			startActivity(i);
    		}
    	   		
    	}
	
    };
    
    
}