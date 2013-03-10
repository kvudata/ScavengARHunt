package com.ScavengerHunt.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class AugmentClueView extends View {
	private static final String TAG = "AugmentClueView";
	
	private Bitmap mOverlay;
	private Canvas mCanvas;
	
	public AugmentClueView(Context context) {
		super(context);
		initVars();
	}

	public AugmentClueView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initVars();
	}
	
	private void initVars() {
		mOverlay = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
		mCanvas = new Canvas(mOverlay);
	}

	@Override
	protected void onDraw(Canvas c) {
		Paint paint = new Paint();
		c.drawBitmap(mOverlay, 0.0f, 0.0f, paint);
	}
	
	// each time this is called, resets overlay to contain just given text
	public void addText(String text) {
		mOverlay.eraseColor(Color.TRANSPARENT);
		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setTextSize(50.0f);
		paint.setTypeface(Typeface.DEFAULT_BOLD);
		mCanvas.drawText(text, 30.0f, 50.0f, paint);
		//TODO: text doesn't go to "next" line in bitmap when too long
		Log.v(TAG, "adding text/invalidating");
		// update view with text
		invalidate();
	}
	
	public Bitmap getOverlayBitmap() {
		return mOverlay;
	}

}
