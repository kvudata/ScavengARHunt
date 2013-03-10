package com.ScavengerHunt.screen;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ScavengerHunt.R;
import com.ScavengerHunt.Utils;
import com.ScavengerHunt.game.Clue;
import com.ScavengerHunt.game.Hunt;
import com.ScavengerHunt.view.camera.CameraLiveView;
import com.ScavengerHunt.view.camera.CameraLiveView.TrackingCallback;
import com.ScavengerHunt.view.camera.CameraLiveView.ViewMode;

public class HuntActivity extends Activity {
	private static final String TAG = "HuntActivity";
	static final String HUNT_FOLDER_KEY = "hunt_folder";

	private FrameLayout previewFrameLayout;
	private CameraLiveView camView;
	private Button submitGuessButton;
	private LinearLayout notificationArea;
	private TextView notificationText;
	private Button yesButton;
	private Button noButton;

	private Hunt hunt;
	private boolean readyForNextClue = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.game_screen);
		findAllViewsById();

		// setup click actions in UI
		submitGuessButton.setOnClickListener(buttonListener);
		submitGuessButton.setClickable(false);
		yesButton.setOnClickListener(buttonListener);
		noButton.setOnClickListener(buttonListener);

		// add customized camera view to frame layout
		camView = new CameraLiveView(this);
		Log.d(TAG, "camView height = " + camView.getHeight()
				+ "; camView width = " + camView.getWidth());
		previewFrameLayout.addView(camView);

		// TODO: load hunt data based on intent
		// TODO: need to create intermediate screen where user chooses a hunt
		Intent i = getIntent();
		String huntDirName = i.getStringExtra(HUNT_FOLDER_KEY);
		String huntFile = Utils.getHuntsBaseDir() + huntDirName + "/hunt.json";
		Log.v(TAG, "Opening " + huntFile);
		try {
			hunt = new Hunt(huntFile);
		} catch (IOException e) {
			Log.e(TAG, "Failed to load " + huntFile);
			setResult(RESULT_CANCELED);
			finish();
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//TODO: corresponding onStop()/onPause() deallocate data?
		// setup first clue
		Clue clue = hunt.getCurrClue();
		updatePreviewClue(clue);
	}

	private void findAllViewsById() {
		previewFrameLayout = (FrameLayout) findViewById(R.id.preview_layout);
		submitGuessButton = (Button) findViewById(R.id.submit_guess);
		notificationArea = (LinearLayout) findViewById(R.id.notification_area);
		notificationText = (TextView) findViewById(R.id.notification_text);
		yesButton = (Button) findViewById(R.id.yes_btn);
		noButton = (Button) findViewById(R.id.no_btn);
	}

	private void updatePreviewClue(Clue c) {
		// TODO: do this in separate thread, setupAR is long running
		// TODO: why does this freeze preview updating?
		// TODO: add spinning progress wheel
		submitGuessButton.setClickable(false);
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... args) {
				camView.setupAR(args[0], args[1], false);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				// notify user that next clue is ready to be searched for
				submitGuessButton.setClickable(true);
			}
		}.execute(c.getTrackableFilepath(), c.getGraphicFilepath());
	}

	// TODO: move notification update code to a function which sets based on
	// enum passed which specifies state of AR/game

	private OnClickListener buttonListener = new OnClickListener() {
		public void onClick(View v) {
			if (v.getId() == submitGuessButton.getId()) {
				// clear previous notifications
				notificationArea.setVisibility(View.INVISIBLE);
				// TODO: possibly just make submit start detection on feed?
				// register callback so know when user is able to find the clue
				// & can progress to next clue
				camView.setTrackingCallback(testDetectCallback);
				// TODO: freeze preview frame and run object detection on it
				// start object detection/tracking
				camView.setMode(CameraLiveView.ViewMode.TEST_DETECT);
				//TODO: disable submit guess button until guess evaluated
			} else if (v.getId() == yesButton.getId()) {
				// clear notification area
				notificationArea.setVisibility(View.INVISIBLE);
				if (readyForNextClue) {
					// this is confirmation to load next clue
					// set preview back to normal & start loading next clue
					readyForNextClue = false;
					camView.setMode(ViewMode.IDLE);
					Clue next = hunt.progressToNextClue();
					updatePreviewClue(next);
				} else {
					// start augmented reality
					//TODO: provide functionality to cancel augmentation, sometimes there is a false positive here
					camView.setTrackingCallback(trackingCallback);
					camView.setMode(CameraLiveView.ViewMode.TRACK);
				}
			} else if (v.getId() == noButton.getId()) {
				// clear notification area
				// TODO: instead of clearing everything, reset notification text
				// to submit guess instructions
				notificationArea.setVisibility(View.INVISIBLE);
				// whether user wants to resume finding current clue
				// (readyForNextClue == true),
				// or is rejecting false positive by object detector, action is
				// the same
				camView.setMode(ViewMode.IDLE);
			}
		}
	};

	private TrackingCallback testDetectCallback = new TrackingCallback() {
		@Override
		public void onDetected() {
			// guess was presumably good, ask user to confirm
			yesButton.post(new Runnable() {
				public void run() {
					// TODO: add nice check/X graphic indicating
					// success
					notificationText
							.setText("Good guess, looks like you found the"
									+ " clue! Start augmentation?");
					notificationArea.setVisibility(View.VISIBLE);
					yesButton.setVisibility(View.VISIBLE);
					noButton.setVisibility(View.VISIBLE);
				}
			});
		}

		@Override
		public void onFailedDetection() {
			// couldn't find trackable in guess, notify user
			notificationText.post(new Runnable() {
				public void run() {
					notificationText
							.setText("Too bad, couldn't find clue in frame");
					yesButton.setVisibility(View.INVISIBLE);
					noButton.setVisibility(View.INVISIBLE);
					notificationArea.setVisibility(View.VISIBLE);
					camView.setMode(ViewMode.IDLE);
					submitGuessButton.setClickable(true);
				}
			});
		}
	};

	private TrackingCallback trackingCallback = new TrackingCallback() {
		@Override
		public void onDetected() {
			// detection successful, starting tracking + augmentation
			// ask user if ready for next clue
			// TODO: check for case when done with hunt
			readyForNextClue = true;
			if (hunt.onLastClue()) {
				// TODO: go to winner screen
				submitGuessButton.post(new Runnable() {
					public void run() {
						// remove preview view
						previewFrameLayout.removeAllViews();
						// TODO show cool congrats found golden idol image
						previewFrameLayout
								.setBackgroundResource(R.drawable.medium_thumbs_up);
						notificationText
								.setText("Congratulations! You completed the scavenger hunt!");
						notificationText.setVisibility(View.VISIBLE);
						yesButton.setVisibility(View.INVISIBLE);
						noButton.setVisibility(View.INVISIBLE);
						notificationArea.setVisibility(View.VISIBLE);
						// TODO provide some way to navigate back to home
						// screen/play again
					}
				});
			}
			notificationArea.post(new Runnable() {
				public void run() {
					notificationText.setText("You've successfully "
							+ "found the clue, are you ready for "
							+ "the next clue?");
					yesButton.setVisibility(View.VISIBLE);
					noButton.setVisibility(View.VISIBLE);
					notificationArea.setVisibility(View.VISIBLE);
				}
			});
		}

		@Override
		public void onFailedDetection() {
			// failed to detect within X number of frames once
			// started...
			notificationArea.post(new Runnable() {
				public void run() {
					notificationText.setText("You're guess was "
							+ "good, but looks like we couldn't "
							+ "focus on the object. Please try "
							+ "submitting the clue guess again and "
							+ "try to keep the camera in the same "
							+ "position until the augmentation starts");
					// TODO: extract strings/messages to strings.xml
					notificationArea.setVisibility(View.VISIBLE);
				}
			});
		}

		@Override
		public void onLostTracking() {
			// preview has gone back to IDLE, user should still have option to
			// progress to next clue or redo, so nothing to change
			HuntActivity.this.runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(getApplicationContext(), "Lost trackable!",
							Toast.LENGTH_SHORT).show();	
				}
			});
		}
	};
}
