package com.ScavengerHunt.screen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.ScavengerHunt.R;
import com.ScavengerHunt.Utils;

public class PlayHomeScreen extends Activity {
	private static final int CHOOSE_HUNT_DIALOG = 0;

	/** Called when the activity is first created. */
	private RadioButton createHuntButton;
	private RadioButton joinHuntButton;
	private Button goButton;
	private TextView playerDescription;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.play_home_screen_layout);

		this.findAllViewsById();

		createHuntButton.setOnClickListener(buttonListener);
		joinHuntButton.setOnClickListener(buttonListener);
		goButton.setOnClickListener(buttonListener);

		// playerDescription.setText(" ");

	}

	private void findAllViewsById() {
		createHuntButton = (RadioButton) findViewById(R.id.create_hunt_button);
		joinHuntButton = (RadioButton) findViewById(R.id.join_hunt_button);
		goButton = (Button) findViewById(R.id.go_button);
		playerDescription = (TextView) findViewById(R.id.player_description);
	}

	private OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View v) {

			if (v.getId() == createHuntButton.getId()) {
				playerDescription
						.setText("Create a new hunt!");
			} else if (v.getId() == joinHuntButton.getId()) {
				playerDescription
						.setText("Join the hunt! Follow clues to solve the scavenger hunt and find the treasure");
			}

			else if (v.getId() == goButton.getId()) {
				if (createHuntButton.isChecked()) {
					Intent i = new Intent(v.getContext(),
							CreateHuntScreen.class);
					startActivity(i);
				} else if (joinHuntButton.isChecked()) {
					// bring up hunt selection dialog
					// TODO: make this into a full-fledged preview screen for
					// available hunts
					showDialog(CHOOSE_HUNT_DIALOG);
				}
			}

		}

	};
	
	//TODO: this is deprecated
	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == CHOOSE_HUNT_DIALOG) {
			final String[] huntDirs = Utils.listHuntDirs();
			AlertDialog.Builder builder = new AlertDialog.Builder(
					PlayHomeScreen.this);
			builder.setTitle("Choose a hunt").setItems(huntDirs,
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog,
								int which) {
							//TODO: dismiss alert dialog?
							Intent i = new Intent(
									getApplicationContext(),
									HuntActivity.class);
							i.putExtra(HuntActivity.HUNT_FOLDER_KEY,
									huntDirs[which]);
							startActivity(i);
						}
					});
			return builder.create();			
		}
		return null;
	}

}
