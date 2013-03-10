package com.ScavengerHunt.screen;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.ScavengerHunt.R;
import com.ScavengerHunt.view.AugmentClueView;

public class AugmentClueScreen extends Activity {
	private static final String TAG = "AugmentClueScreen";
	static final String CLUE_FP_KEY = "clue_path";
	static final String CLUE_AUG_FP_KEY = "clue_aug_path";
	private static final int MAX_BITMAP_QUALITY = 100;

	private Button addTextButton;
	private EditText addTextEdit;
	private Button addGraffitiButton;
	private Button doneButton;
	private ImageView augImgView;
	private AugmentClueView overlay;

	private String augFilepath;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.augment_clue_screen);

		this.findAllViewsById();

		addTextButton.setOnClickListener(buttonListener);
		addGraffitiButton.setOnClickListener(buttonListener);
		doneButton.setOnClickListener(buttonListener);
		addTextEdit.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View view, int keyCode, KeyEvent event) {
				Log.v(TAG, "onKey(): edittext=" + addTextEdit.getText());
				// TODO: event triggered only when ENTER or delete pressed
				if (event.getAction() == KeyEvent.ACTION_DOWN
						&& keyCode == KeyEvent.KEYCODE_ENTER) {
					// TODO: should this be when key is released?
					// update overlay with text
					Log.v(TAG, "adding text to overlay!");
					overlay.addText(addTextEdit.getText().toString());
					return true;
				}
				return false;
			}
		});
		// TODO: add DONE button for edittext
		// TODO: change different augmentation buttons to a context drop down
		// which shows additional options depending on selection

		// load clue image
		Intent i = getIntent();
		String clueFilepath = i.getStringExtra(CLUE_FP_KEY);
		// TODO: RGB seems to be reversed...
		augImgView.setImageBitmap(BitmapFactory.decodeFile(clueFilepath));
		// TODO: an alternative to loading from file is CreateHuntScreen has
		// package accessible bitmap which is current clue & don't save anything
		// until clue itself is saved
		String prefix = clueFilepath
				.substring(0, clueFilepath.lastIndexOf('.'));
		augFilepath = prefix + "_aug.png";
		// TODO: note file extension should be same as one passed to compress
		// below when saving aug img
	}

	private void findAllViewsById() {
		addTextButton = (Button) findViewById(R.id.add_text_button);
		addTextEdit = (EditText) findViewById(R.id.add_text_edit);
		addGraffitiButton = (Button) findViewById(R.id.add_graffiti_button);
		doneButton = (Button) findViewById(R.id.done_button);
		augImgView = (ImageView) findViewById(R.id.aug_image_view);
		overlay = (AugmentClueView) findViewById(R.id.aug_image_overlay);
	}

	private OnClickListener buttonListener = new OnClickListener() {
		public void onClick(final View v) {

			if (v.getId() == addTextButton.getId()) {
				// show edit text to get user input
				addTextEdit.setVisibility(View.VISIBLE);
			} else if (v.getId() == addGraffitiButton.getId()) {
				Toast toast = Toast.makeText(getApplicationContext(),
						"Add Graffiti!", Toast.LENGTH_SHORT);
				toast.show();
				// TODO
			} else if (v.getId() == doneButton.getId()) {
				// save overlay to file and
				// return to create hunt screen, with additional clue data
				Bitmap aug = overlay.getOverlayBitmap();
				// TODO: check if user made an augmentation?

				try {
					OutputStream out = new BufferedOutputStream(
							new FileOutputStream(augFilepath));
					aug.compress(CompressFormat.PNG, MAX_BITMAP_QUALITY, out);
					out.close(); // NOTE close() is necessary so don't wait for
									// JVM to eventually get to flushing stream
				} catch (IOException e) {
					// file could not be opened for writing...
					Toast.makeText(getApplicationContext(),
							"Couldn't save clue augmentation",
							Toast.LENGTH_SHORT).show();
					return;
				}

				// TODO: JPEG doesn't support alpha, will this be a problem?
				Intent res = new Intent();
				res.putExtra(CLUE_AUG_FP_KEY, augFilepath);
				setResult(RESULT_OK, res);
				finish();

			}
		}
	};

}
