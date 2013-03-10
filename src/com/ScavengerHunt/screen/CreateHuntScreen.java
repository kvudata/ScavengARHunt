package com.ScavengerHunt.screen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.JsonWriter;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ScavengerHunt.R;
import com.ScavengerHunt.Utils;
import com.ScavengerHunt.game.Clue;
import com.ScavengerHunt.game.Clue.Location;
import com.ScavengerHunt.game.HuntData;
import com.ScavengerHunt.view.camera.CameraLiveView;
import com.ScavengerHunt.view.camera.CameraLiveView.TakePicCallback;

public class CreateHuntScreen extends Activity {
	private static final String TAG = "CreateHuntScreen";
	private static final int AUGMENT_CLUE_REQ_CODE = 0;
	private static final int MIN_FEATURES = 200; // TODO: set this value to
													// something supported by
													// research

	private Button takePictureButton;
	private Button discardButton;
	private Button loadClueButton;
	private TextView picFeedback;
	private Button augmentButton;
	private Button doneButton;
	private FrameLayout camFrameLayout;
	private CameraLiveView camLiveView;

	private String huntDataDir;
	private HuntData huntData;
	// intermediate structure to hold clue info while it is being constructed
	private Clue currClue;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_hunt_screen);

		this.findAllViewsById();

		takePictureButton.setOnClickListener(buttonListener);
		discardButton.setOnClickListener(buttonListener);
		loadClueButton.setOnClickListener(buttonListener);
		augmentButton.setOnClickListener(buttonListener);
		doneButton.setOnClickListener(buttonListener);

		// add camera preview to screen
		camLiveView = new CameraLiveView(this);
		camFrameLayout.addView(camLiveView);

		// initialize hunt data
		huntDataDir = Utils.getNewHuntDataDir();
		// create huntDataDir to store data in
		File dataDir = new File(huntDataDir);
		if (!dataDir.exists() && !dataDir.mkdirs())
			throw new IllegalStateException("Failed to create " + huntDataDir);
		huntData = new HuntData();
		currClue = new Clue();
	}

	private void findAllViewsById() {
		takePictureButton = (Button) findViewById(R.id.take_picture_button);
		discardButton = (Button) findViewById(R.id.discard_button);
		loadClueButton = (Button) findViewById(R.id.load_clue_button);
		picFeedback = (TextView) findViewById(R.id.pic_feedback);
		augmentButton = (Button) findViewById(R.id.augment_button);
		doneButton = (Button) findViewById(R.id.done_button);
		camFrameLayout = (FrameLayout) findViewById(R.id.cam_frame_layout);
	}

	private OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View v) {

			if (v.getId() == takePictureButton.getId()) {
				// TODO: this is still freezing UI when it takes a while to
				// detect...
				camLiveView.setTakePicCallback(new TakePicCallback() {
					public void onFeaturesDetected(int n) {
						// this callback will be called by a non-UI thread, so
						// View.post() to update UI
						Log.v(TAG, "onFeaturesDetected(): n = " + n);
						if (n < MIN_FEATURES) {
							picFeedback.setText("Only detected " + n
									+ " features in this picture, "
									+ "this object will not perform "
									+ "well as a clue");
						} else {
							picFeedback
									.setText("Detected "
											+ n
											+ " features "
											+ "in this picture, this is a good picture "
											+ "to use as a clue");
						}
						// TODO: is it safe to set text when not in UI-thread?
						picFeedback.post(new Runnable() {
							public void run() {
								picFeedback.setVisibility(View.VISIBLE);
								augmentButton.setClickable(true);
							}
						});
					}
				});
				camLiveView.setMode(CameraLiveView.ViewMode.TAKE_PIC);
				// TODO: shutter sound?

				discardButton.setVisibility(View.VISIBLE);
				//TODO: don't show discard button until frame processed
			} else if (v.getId() == discardButton.getId()) {
				resetForTakePic();
			} else if (v.getId() == loadClueButton.getId()) {
				Toast toast = Toast.makeText(getApplicationContext(),
						"Loads Pic into Image View", Toast.LENGTH_SHORT);
				toast.show();
			} else if (v.getId() == augmentButton.getId()) {
				String trackableFilepath = huntDataDir + "/clue"
						+ huntData.getNumClues() + ".jpg";
				currClue.setTrackableFilepath(trackableFilepath);
				if (camLiveView.savePic(trackableFilepath)) {
					Intent i = new Intent(v.getContext(),
							AugmentClueScreen.class);
					i.putExtra(AugmentClueScreen.CLUE_FP_KEY, trackableFilepath);
					startActivityForResult(i, AUGMENT_CLUE_REQ_CODE);
				} else {
					Toast.makeText(getApplicationContext(),
							"Failed to save clue!", Toast.LENGTH_SHORT).show();
				}
			} else if (v.getId() == doneButton.getId()) {
				// TODO Save this hunt data to file,
				// and bring up option to play this hunt or go back to play home
				// screen
				try {
					Log.d(TAG,
							"Trying to save hunt data, huntData num clues = "
									+ huntData.getNumClues());
					StringWriter str = new StringWriter();
					JsonWriter test = new JsonWriter(str);
					huntData.writeJson(test);
					Log.d(TAG, "writeJson result=" + str.toString());
					JsonWriter writer = new JsonWriter(new BufferedWriter(
							new FileWriter(huntDataDir + "/hunt.json")));
					huntData.writeJson(writer);
				} catch (IOException e) {
					// TODO proper error handling
					Toast.makeText(getApplicationContext(),
							"Failed to save hunt data as JSON!",
							Toast.LENGTH_SHORT).show();
				}
				Toast.makeText(getApplicationContext(), "Saved hunt!",
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	};

	protected void onActivityResult(int reqCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (reqCode == AUGMENT_CLUE_REQ_CODE) {
				String augmentFilepath = data
						.getStringExtra(AugmentClueScreen.CLUE_AUG_FP_KEY);
				currClue.setGraphicFilepath(augmentFilepath);
				currClue.setLocation(getCurrentLoc());
				// add new clue to current hunt
				huntData.addClue(currClue);
				currClue = new Clue();
				// TODO: show summary info of current hunt being constructed
				Toast.makeText(getApplicationContext(), "Saved clue!",
						Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private void resetForTakePic() {
		discardButton.setVisibility(View.GONE);
		picFeedback.setVisibility(View.GONE);
		augmentButton.setClickable(false);
		// TODO: can change to IDLE at inopportune time mess
		// things up in CameraLiveView?
		camLiveView.setMode(CameraLiveView.ViewMode.IDLE);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// this is good place to reset take picture view elements since there
		// are multiple ways this activity can be navigated away from
		resetForTakePic();
		//TODO: do this onResume() so smoother when leaving?
		//TODO: save huntdata when this activity is destroyed?
	}

	private Location getCurrentLoc() {
		// TODO: implement!
		// this will probably involve starting location listener when this
		// activity starts and keeping a last known location var which is
		// accessed here
		return new Location();
	}
}
