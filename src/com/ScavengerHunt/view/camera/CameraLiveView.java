package com.ScavengerHunt.view.camera;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraLiveView extends CameraLiveViewBase {
	private static final String TAG = "CameraLiveView";

	// TODO fix orientation of app

	public enum ViewMode {
		IDLE, TAKE_PIC, PAINTING, FIND_FEATURES, TRACK, TEST_DETECT
	}

	// Java side enum of native processFrame() results
	// These MUST be exactly the same as the enum in native code
	private static final int DETECTED = 0;
	private static final int DETECT_FAILED = -1;
	private static final int TRACKING_GOOD = 1;
	private static final int TRACKING_LOST = -2;
	private static final int INVALID_RET = -3;
	//NOTE this is a special value that is not equal to any of the above
	
	// limit for how many DETECT_FAILED to wait before notifying user
	// TODO: this should probably be specified by caller
	private static final int MAX_DETECT_FAILED = 4;
	
	private ViewMode mViewMode = ViewMode.IDLE;

	private Mat mYuv;
	private Mat mRgba;
	private Mat mGraySubmat;
	private Mat mAugmentImg;
	private Mat mWarpedImg;

	private Mat mPicFrame; // temp Mat used to store TAKE_PIC frame

	private TrackingCallback trackingCallback;
	private int numDetectFailed = 0;
	private TakePicCallback takePicCallback;
	private boolean tookPic = false;
	
	//TODO: cleanup.
	private long overallTime = 0;
	private long numFrames = 0;
	
	public CameraLiveView(Context context) {
		super(context);
		if (isInEditMode()) {
			return;
		}
		mViewMode = ViewMode.IDLE;

		Log.d(TAG, "in CameraLiveView(Context), width x height = " + this.getWidth()
				+ " x " + this.getHeight());
	}

	@Override
	public void surfaceChanged(SurfaceHolder _holder, int format, int width,
			int height) {
		super.surfaceChanged(_holder, format, width, height);
		Log.d(TAG, "surface changed");

		synchronized (this) {
			// initialize Mats before usage
			// this is in synchronized block since these Mat's are interacted
			// with by processFrame() thread (processFrame() called in
			// CameraLiveViewBase within synchronized block)
			mYuv = new Mat(getFrameHeight() + getFrameHeight() / 2,
					getFrameWidth(), CvType.CV_8UC1);
			mGraySubmat = mYuv.submat(0, getFrameHeight(), 0, getFrameWidth());

			mRgba = new Mat(getFrameHeight(), getFrameWidth(), CvType.CV_8UC4);
			
			mAugmentImg = new Mat(getFrameHeight(), getFrameWidth(), CvType.CV_8UC4);
			mWarpedImg = new Mat(mAugmentImg.rows(), mAugmentImg.cols(),
					mAugmentImg.type());
			
			this.notifyAll();
		}

		// TODO: should this be moved into sync block?
		initProcessing(getFrameHeight(), getFrameWidth());
		Log.d(TAG, "surface changed done");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		super.surfaceDestroyed(holder);
		// some data structures which are allocated in native code can be
		// deallocated
		deinitProcessing();
	}

	@Override
	protected Bitmap processFrame(byte[] data) {
		//TODO: cleanup
		long start = SystemClock.uptimeMillis();
		boolean fromTest = false;
		
		mYuv.put(0, 0, data);
		int res = INVALID_RET;
		boolean fromTrack = false;
		switch (mViewMode) {
		case IDLE:
			Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
			break;
		case TAKE_PIC:
			// assume mRgba has previous frame and preserve this frame
			// taking a picture equates to freezing the frame i.e. not drawing
			// anything
			if (takePicCallback != null) {
				Log.v(TAG, "TAKE_PIC mode, freezing frame");
				// draw detected features on frame, notify callback of number of
				// features
				mPicFrame = mGraySubmat.clone();
				// save copy of frame if user wants to save it
				Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
				// do conversion so mRgba matches mPicFrame
				int n = detectFeatures(mPicFrame.getNativeObjAddr(), mRgba.getNativeObjAddr());
				new TakePicServicer(takePicCallback, n).start();
				takePicCallback = null;
				// NOTE don't set mode back to IDLE yet, let caller do it so frame stays frozen
				break;
				// break so can draw features on screen
				// TODO: fix flow of code so screen drawn definitively before
				// user knows how many features detected
			}
			return null;
		case TRACK:
			fromTrack = true;
			Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
			Log.v(TAG, "Going to process video frame for trackable image");
			res = processFrame(mGraySubmat.getNativeObjAddr(),
					mRgba.getNativeObjAddr());
			if (trackingCallback != null) {
				if (res == DETECT_FAILED) {
					numDetectFailed++;
					if (numDetectFailed > MAX_DETECT_FAILED) {
						numDetectFailed = 0;
						// reached threshold, deactivate AR & notify caller failed to detect
						mViewMode = ViewMode.IDLE;
						new TrackingCallbackServicer(trackingCallback, res).start();
						trackingCallback = null;
						//TODO: if these situations result in mode going to IDLE, just do it here
					}
				} else {
					numDetectFailed = 0;
					if (res != TRACKING_GOOD) {
						if (res == TRACKING_LOST) {
							// deactivate AR
							mViewMode = ViewMode.IDLE;
						}
						// notify callback "immediately"
						new TrackingCallbackServicer(trackingCallback, res).start();
						trackingCallback = null;
					}
				}
			}
			Log.d(TAG, "TRACK: processFrame() returned " + res);
			break;
		case TEST_DETECT:
			fromTest = true;
			//TODO: try running object detection on frame, if successful draw detected outline
			// also, communicate results back to caller (using trackingCallback)
			if (trackingCallback != null) {
				Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
				Log.v(TAG, "Going to test video frame for trackable image");
				res = tryObjDetect(mGraySubmat.getNativeObjAddr(), mRgba.getNativeObjAddr());
				// service callback and draw this result on preview
				new TrackingCallbackServicer(trackingCallback, res).start();
				trackingCallback = null;
				// NOTE: let caller decide when to set mode back to IDLE
				break;
			}
			return null;
		}

		Bitmap bmp = Bitmap.createBitmap(getFrameWidth(), getFrameHeight(),
				Bitmap.Config.ARGB_8888);

		if (Utils.matToBitmap(mRgba, bmp)) {
			if (fromTrack && (res == DETECTED || res == TRACKING_GOOD)) {
				// from TRACK and good ret val, so draw augmented image as well
				Bitmap warped = Bitmap.createBitmap(getFrameWidth(),
						getFrameHeight(), Bitmap.Config.ARGB_8888);
				if (Utils.matToBitmap(mWarpedImg, warped)) {
					// draw warped image on top of bitmap
					Canvas canvas = new Canvas(bmp);
					Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
					canvas.drawBitmap(warped, 0.0f, 0.0f, paint);
				}
				Log.w(TAG, "Failed to convert mWarpedImg to bitmap..");
				warped.recycle();
			}
			//TODO:cleanup.
			if (fromTest) {
				overallTime += SystemClock.uptimeMillis() - start;
				numFrames++;
				Log.v(TAG, "TRACKING_TIME = " + overallTime + " / " + numFrames + " = " + ((double)overallTime)/numFrames + " millis/frame");
			}
			return bmp;
		}
		Log.v(TAG, "Failed to convert mRgba to bitmap..");

		bmp.recycle();
		return null;
	}

	@Override
	public void run() {
		super.run();
		Log.d(TAG, "run");

		synchronized (this) {
			// Explicitly deallocate Mats
			if (mYuv != null)
				mYuv.release();
			if (mRgba != null)
				mRgba.release();
			if (mGraySubmat != null)
				mGraySubmat.release();
			if (mAugmentImg != null)
				mAugmentImg.release();
			if (mWarpedImg != null)
				mWarpedImg.release();

			mYuv = null;
			mRgba = null;
			mGraySubmat = null;
			mAugmentImg = null;
			mWarpedImg = null;
		}
	}

	// change camera live view mode
	public void setMode(ViewMode m) {
		Log.d(TAG, "setMode(" + m + ")");
		if (m == ViewMode.TAKE_PIC && takePicCallback == null)
			throw new IllegalStateException(
					"Must set takePicCallback before TAKE_PIC");
		mViewMode = m;
	}

	public ViewMode getMode() {
		return mViewMode;
	}

	// saves picture if in TAKE_PIC mode
	// TODO: make this synchronized?
	public boolean savePic(String filepath) {
		if (mViewMode == ViewMode.TAKE_PIC && mPicFrame != null) {
			Log.v(TAG, "Trying to save picture to " + filepath);
			boolean success = Highgui.imwrite(filepath, mPicFrame);
			mPicFrame.release();
			mPicFrame = null;
			return success;
		}
		return false;
	}

	/**
	 * This function should be called before changing the view mode to TRACK.
	 * This sets up the view to track the given object and overlay the given
	 * augmentation.
	 * 
	 * @param trackableFile
	 * @param augFile
	 * @param flipTrackable
	 */
	public void setupAR(String trackableFile, String augFile,
			boolean flipTrackable) {
		// this cond var is to catch the case when setupAR() is called before surfaceChanged()
		// this typically occurs when trying to setup the first clue in the hunt
		synchronized (this) {
			while (mWarpedImg == null) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		Log.v(TAG, "setupAR(" + trackableFile + ", " + augFile + ")");
		//TODO: release/clear previous structures properly when setting up for new clue
		// NOTE: assumes that augFile is stored as a .png with alpha info
		Bitmap augBmp = BitmapFactory.decodeFile(augFile);
		if (augBmp == null)
			Log.e(TAG, "augBmp couldn't be loaded! " + augFile);
		Mat tmp = Utils.bitmapToMat(augBmp);
		// make a clone so when augBmp goes out of scope, mAugmentImg won't break
		mAugmentImg = tmp.clone();
		augBmp.recycle();
		//TODO: is it safe to release Mat as well?
		tmp.release();
		// imread doesn't read alpha info, add alpha channel to mAugmentImg
		Log.d(TAG,
				"mAugmentImg type = " + CvType.typeToString(mAugmentImg.type()));
		setAugmentImg(mAugmentImg.getNativeObjAddr(),
				mWarpedImg.getNativeObjAddr());
		setupTrackable(trackableFile, getFrameHeight(), getFrameWidth(),
				flipTrackable);
		// TODO change to false/based on pic
		// TODO: this assumes all clue images will be taken with camera in
		// correct orientation
	}

	private native int processFrame(long matAddrGray, long matAddrRgba);

	// initialize data structures which can be preallocated for object
	// detection/tracking
	private native void initProcessing(int frameHeight, int frameWidth);

	// release data structures which allocated in native code
	private native void deinitProcessing();

	private native int setupTrackable(String filename, int width, int height,
			boolean flip);

	private native void setAugmentImg(long augmentMatAddrRgba,
			long warpedMatAddr);

	// detects/draws features in given frame and returns number detected
	private native int detectFeatures(long grayFrameMatAddr, long rgbaMatAddr);
	
	// performs object detection on given frame & draws outline if detection successful
	private native int tryObjDetect(long grayFrameMatAddr, long rgbaMatAddr);

	static {
		System.loadLibrary("mixed_sample");
	}

	public void setTrackingCallback(TrackingCallback c) {
		trackingCallback = c;
	}

	// this fxn should be called before setting the mode to take a picture
	public void setTakePicCallback(TakePicCallback c) {
		takePicCallback = c;
	}

	public static abstract class TrackingCallback {
		// override these for TRACK or TEST_DETECT
		public abstract void onDetected();
		public abstract void onFailedDetection();
		public void onLostTracking(){}
	}

	public static interface TakePicCallback {
		public void onFeaturesDetected(int n);
	}

	private class TakePicServicer extends Thread {
		private TakePicCallback callback;
		private int n;

		public TakePicServicer(TakePicCallback c, int n) {
			super();
			callback = c;
			this.n = n;
		}

		@Override
		public void run() {
			callback.onFeaturesDetected(n);
		}
	}
	
	private class TrackingCallbackServicer extends Thread {
		private TrackingCallback callback;
		int retVal;
		
		public TrackingCallbackServicer(TrackingCallback c, int r) {
			callback = c;
			retVal = r;
		}
		
		public void run() {
			switch (retVal) {
			case DETECTED:
				callback.onDetected();
				break;
			case DETECT_FAILED:
				callback.onFailedDetection();
				break;
			case TRACKING_GOOD:
				//TODO: add callback method for good tracking?
				break;
			case TRACKING_LOST:
				callback.onLostTracking();
				break;
			default:
				throw new IllegalStateException("Invalid processFrame() return value!");
			}
		}
	}
}
