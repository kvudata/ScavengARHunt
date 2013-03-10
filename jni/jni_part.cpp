#include <jni.h>
#include <android/log.h>

#include <stdio.h>
#include <iostream>
#include <ctime>
#include "opencv2/opencv.hpp"
#include "opencv2/core/core.hpp"
#include "opencv2/features2d/features2d.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"

#define DEBUG(FMT, ARGS...) __android_log_print(ANDROID_LOG_DEBUG, "CameraLiveView", FMT, ## ARGS)

#define TICKS_TO_MS(ticks) ((ticks)/(CLOCKS_PER_SEC/1000))
#define TIME_FUNC(cmd) \
    start = clock(); cmd; end = clock(); DEBUG(#cmd "; took %d ms", TICKS_TO_MS(end-start))

#define USE_FAST_FEATURES 0
#define USE_GOOD_FEATURES 0
#define USE_MSER_FEATURES 0
#define USE_STAR_FEATURES 0
#define USE_SIFT_FEATURES 0
#define USE_SURF_FEATURES 1

#define FAST_THRESHOLD 20

// currently only used for goodfeaturestotrack
#define MAX_KPTS 1000

#define USE_SIFT_DESC  0
#define USE_SURF_DESC  1
#define USE_BRIEF_DESC 0
#define BRIEF_DESC_LEN 64


#define USE_BRUTE_MATCHER 0
#define USE_FLANN_MATCHER 1
#define USE_EUCL_MATCH    0
#define USE_HAMMING_MATCH 1
#define DO_DEFAULT_MATCH  0
#define DO_KNN_MATCH      1
#define DO_RADIUS_MATCH   0

#define MIN_MATCHES 4
#define MIN_TRACK_POINTS 4

#define RANSAC_REPROJ_DIST 10   /* Allowed projection error distance in pixels NOTE ARmsk uses 10 */
#define TRACKING_MATCH_PCT_THRESHOLD 65

using namespace cv;
using namespace std;

void initTrackable(char* filename);
int processFrame(Mat& bgr, Mat& gray);
void getKeyPoints(Mat& img, vector<KeyPoint>& kpts);
void getDescriptors(Mat& img, vector<KeyPoint>& kpts, Mat& desc);
void matchDescriptors(Mat& queryDesc, Mat& trainDesc, vector<DMatch>& matches);

/**
 * @enum ProcessFrameResult
 * @brief return values of processFrame()
 */
enum ProcessFrameResult {
    DETECTED = 0,
    DETECT_FAILED = -1,
    TRACKING_GOOD = 1,
    TRACKING_LOST = -2
};


static Mat prev_frame;

/*
 * Structures to store data of object being detected
 */
static std::vector<Point2f> obj_corners(4);
static vector<KeyPoint> obj_kpts;
static Mat obj_desc;

static Mat* augment_img = NULL;
static Mat* warped_img = NULL;
static Mat overallH(3, 3, CV_64FC1);


/**
 * @brief Initialize data structures for detecting a given object
 * This should be called once before processFrame() for each new object
 * to track.
 * @param filename The filename/path of the image to detection for
 * @param width    width to resize the image to
 * @param height   height to resize the image to
 * @param needToFlip - Specify if the image should be flipped diagonally before
 * 					training on or not
 */
void setupTrackable(const char* filename, size_t width, size_t height,
		bool needToFlip) {
    Mat train_img = imread( filename, CV_LOAD_IMAGE_GRAYSCALE );
    // scale image to match video size for convenience and faster processing
    resize(train_img, train_img, Size(height, width));
    if (needToFlip) {
        transpose(train_img, train_img);
        flip(train_img, train_img, 1);
    }
    DEBUG("%s: resized image to %d x %d", filename, train_img.cols,
                        train_img.rows);
    // compute descriptors for training img

    obj_kpts.clear();
    getKeyPoints(train_img, obj_kpts);
    DEBUG("# of keypoints = %d", obj_kpts.size());

	obj_desc.release();
    getDescriptors(train_img, obj_kpts, obj_desc);
    DEBUG("# of descripts = %d", obj_desc.rows);
    //-- Set the corners from this image ( for rendering the rectangle later )

    obj_corners[0] = cvPoint(0,0);
    obj_corners[1] = cvPoint( train_img.cols, 0 );
    obj_corners[2] = cvPoint( train_img.cols, train_img.rows );
    obj_corners[3] = cvPoint( 0, train_img.rows );

    imwrite("/sdcard/bluetooth/flipped.jpg", train_img);
}

/**
 * Detects and draws features in given frame.
 * Assumes frame is RGBA.
 */
int detectFeatures(Mat& frame, Mat& drawFrame) {
    vector<KeyPoint> kpts;
    getKeyPoints(frame, kpts);
    Mat desc;
    getDescriptors(frame, kpts, desc);
    for (size_t i = 0, len = kpts.size(); i < len; i++) {
        circle(drawFrame, kpts[i].pt, 3, Scalar(0,255,0,255));
    }
    return kpts.size();
}

/**
 * Initialize overall data structures used when processing frames,
 * i.e. this is to preallocate the frame matrix.
 */
void initProcessing(size_t fheight, size_t fwidth) {
	prev_frame = Mat(fheight, fwidth, CV_8UC1);
}

/**
 * Release data structures allocated in native code
 */
void deinitProcessing() {
	prev_frame.release();
	//TODO: release other data structures
}

/**
 * @brief Tries to detect the current trackable/object in given frame
 * Has same return values as processFrame().
 * Essentially the same as processFrame minus tracking.
 */
int tryObjDetect(Mat& frame, Mat& colorFrame) {
    DEBUG("tryObjDetect()");
	DEBUG("Detecting keypts in video frame");

    // Step 1: detect keypoints in frame
    vector<KeyPoint> kpts;
    getKeyPoints(frame, kpts);
	DEBUG("Found %d keypts in video frame", kpts.size());

    // Step 2: compute descriptors for keypoints
    Mat frame_desc;
    getDescriptors(frame, kpts, frame_desc);
	DEBUG("Computed %d descriptors in frame", frame_desc.rows);
	
    // Step 3: match training img descriptors with frame descriptors to see if match exists
    vector<DMatch> matches;
    matchDescriptors(obj_desc, frame_desc, matches);
	DEBUG("Found %d matches", matches.size());
	if (matches.size() < MIN_MATCHES) {
		return DETECT_FAILED;
	}
    // Step 4: depending on matches, grade whether object has been detected
    // OpenCV tutorials do this by first filtering matches by distance,
    // keeping those with < {2,3}*min_dist distance & then computing homography
    double max_dist = 0;
    double min_dist = 100;

    //-- Quick calculation of max and min distances between keypoints
    for( int i = 0; i < obj_desc.rows; i++ )
    {
        double dist = matches[i].distance;
        if( dist < min_dist ) min_dist = dist;
        if( dist > max_dist ) max_dist = dist;
    }

    DEBUG("-- Max dist : %f \n", max_dist );
    DEBUG("-- Min dist : %f \n", min_dist );

    vector<DMatch> good_matches = matches;
	//TODO also filter matches by 2*min_dist metric?

    // kpts -> pts for computing homography
    std::vector<Point2f> obj;
    std::vector<Point2f> scene;

    for( int i = 0; i < good_matches.size(); i++ )
    {
        obj.push_back( obj_kpts[ good_matches[i].queryIdx ].pt );
        scene.push_back( kpts[ good_matches[i].trainIdx ].pt );
    }

    vector<unsigned char> homog_matches;
    Mat H = findHomography( obj, scene, CV_RANSAC, RANSAC_REPROJ_DIST,
				homog_matches );
    size_t num_homog_matches = countNonZero(Mat(homog_matches));
	DEBUG("# of homography matches = %d out of %d", num_homog_matches,
			homog_matches.size());
    float percent_good = (num_homog_matches+0.0)/good_matches.size()*100.0;
	DEBUG("%% good matches according to homog error = %f", percent_good);

    std::vector<Point2f> scene_corners(4);
    perspectiveTransform( obj_corners, scene_corners, H);
    //TODO as another check to see if homography calculation was good, try to see if scene_corners make a quadrilateral shape
    //TODO throw away points not inside corners

    if (percent_good > TRACKING_MATCH_PCT_THRESHOLD)
    {
        // probably found good match, return "found match"

        //-- Draw lines between the detected corners
        line( colorFrame, scene_corners[0], scene_corners[1],
            Scalar( 0, 255, 0, 255), 4 );
        line( colorFrame, scene_corners[1], scene_corners[2],
            Scalar( 0, 255, 0, 255), 4 );
        line( colorFrame, scene_corners[2], scene_corners[3],
            Scalar( 0, 255, 0, 255), 4 );
        line( colorFrame, scene_corners[3], scene_corners[0],
            Scalar( 0, 255, 0, 255), 4 );
        line( colorFrame, (scene_corners[0]+scene_corners[1])*.5,
            (scene_corners[2]+scene_corners[3])*.5, Scalar(0,255,0,255),4);
        line( colorFrame, (scene_corners[1]+scene_corners[2])*.5,
            (scene_corners[3]+scene_corners[0])*.5, Scalar(0,255,0,255),4);

        return DETECTED;
    }
    return DETECT_FAILED;
}

int processFrame(Mat& bgr_frame, Mat& frame) {
    static bool do_tracking = false;
    static vector<Point2f> prev_pts;
    static vector<Point2f> prev_corners;

    if (do_tracking)
    {
        // Start tracking from prev_frame to this frame
        vector<Point2f> pts;
        vector<unsigned char> status;
        vector<float> err;
        calcOpticalFlowPyrLK(prev_frame, frame, prev_pts, pts, status, err);

        // Update points
        size_t i, k;
        for( i = k = 0; i < pts.size(); i++ )
        {
            if( !status[i] )
                continue;

            pts[k] = pts[i];
			prev_pts[k] = prev_pts[i];
            k++;
circle(bgr_frame, pts[i], 3, Scalar(0,255,0,255), -1, 8);
// draw valid tracked points
        }
        pts.resize(k);
		prev_pts.resize(k);
        //TODO filter out points which have drifted outside of quadrilateral defined by corners

		DEBUG("Succeeded in tracking %d pts", k);
        if (k < MIN_TRACK_POINTS)
        {
            // failed to track well..
            do_tracking = false;
			//TODO is clearing prev_corners & prev_pts necessary?
            prev_corners.clear();
            prev_pts.clear();
            return TRACKING_LOST;
        } else {
            // draw new outline

			// compute homography to tracked points in this frame
            vector<unsigned char> homog_matches;
            Mat H = findHomography( prev_pts, pts, CV_RANSAC,
						RANSAC_REPROJ_DIST, homog_matches );
            size_t num_homog_matches = countNonZero(Mat(homog_matches));
			DEBUG("# of homography matches = %d out of %d", num_homog_matches,
                    homog_matches.size());
			float percent_good =
                (num_homog_matches+0.0)/homog_matches.size()*100.0;
			DEBUG("%% good matches according to homog error= %f", percent_good);
            //TODO cancel tracking if homography is bad

			std::vector<Point2f> scene_corners(4);
			perspectiveTransform( prev_corners, scene_corners, H);

            line( bgr_frame, scene_corners[0], scene_corners[1],
				Scalar( 0, 255, 0, 255), 4 );
            line( bgr_frame, scene_corners[1], scene_corners[2],
				Scalar( 0, 255, 0, 255), 4 );
            line( bgr_frame, scene_corners[2], scene_corners[3],
				Scalar( 0, 255, 0, 255), 4 );
            line( bgr_frame, scene_corners[3], scene_corners[0],
				Scalar( 0, 255, 0, 255), 4 );
            line( bgr_frame, (scene_corners[0]+scene_corners[1])*.5,
				(scene_corners[2]+scene_corners[3])*.5, Scalar(0,255,0,255),4);
            line( bgr_frame, (scene_corners[1]+scene_corners[2])*.5,
				(scene_corners[3]+scene_corners[0])*.5, Scalar(0,255,0,255),4);

            // warp augment image
            overallH = H * overallH;
            warpPerspective(*augment_img, *warped_img, overallH, augment_img->size());
            // NOTE this assumes that augment_img->size() == frame size

            // set prev state for next frame
			frame.copyTo(prev_frame);
            prev_pts = pts;
			prev_corners = scene_corners;
            return TRACKING_GOOD;
        }
    }

	DEBUG("Detecting keypts in video frame");
    // Step 1: detect keypoints in frame
    vector<KeyPoint> kpts;
    getKeyPoints(frame, kpts);
	DEBUG("Found %d keypts in video frame", kpts.size());

    // Step 2: compute descriptors for keypoints
    Mat frame_desc;
    getDescriptors(frame, kpts, frame_desc);
	DEBUG("Computed %d descriptors in frame", frame_desc.rows);
	
    // Step 3: match training img descriptors with frame descriptors to see if match exists
    vector<DMatch> matches;
    matchDescriptors(obj_desc, frame_desc, matches);
	DEBUG("Found %d matches", matches.size());
	if (matches.size() < MIN_MATCHES) {
		return DETECT_FAILED;
	}
    // Step 4: depending on matches, grade whether object has been detected
    // OpenCV tutorials do this by first filtering matches by distance,
    // keeping those with < {2,3}*min_dist distance & then computing homography
    double max_dist = 0;
    double min_dist = 100;

    //-- Quick calculation of max and min distances between keypoints
    for( int i = 0; i < obj_desc.rows; i++ )
    {
        double dist = matches[i].distance;
        if( dist < min_dist ) min_dist = dist;
        if( dist > max_dist ) max_dist = dist;
    }

    DEBUG("-- Max dist : %f \n", max_dist );
    DEBUG("-- Min dist : %f \n", min_dist );

    vector<DMatch> good_matches = matches;
	//TODO also filter matches by 2*min_dist metric?

    // kpts -> pt for computing homography
    std::vector<Point2f> obj;
    std::vector<Point2f> scene;

    for( int i = 0; i < good_matches.size(); i++ )
    {
        obj.push_back( obj_kpts[ good_matches[i].queryIdx ].pt );
        scene.push_back( kpts[ good_matches[i].trainIdx ].pt );
    }

    vector<unsigned char> homog_matches;
    Mat H = findHomography( obj, scene, CV_RANSAC, RANSAC_REPROJ_DIST,
				homog_matches );
    size_t num_homog_matches = countNonZero(Mat(homog_matches));
	DEBUG("# of homography matches = %d out of %d", num_homog_matches,
			homog_matches.size());
    float percent_good = (num_homog_matches+0.0)/good_matches.size()*100.0;
	DEBUG("%% good matches according to homog error = %f", percent_good);

    std::vector<Point2f> scene_corners(4);
    perspectiveTransform( obj_corners, scene_corners, H);
    //TODO as another check to see if homography calculation was good, try to see if scene_corners make a quadrilateral shape
    //TODO filter out points not inside corners

    if (percent_good > TRACKING_MATCH_PCT_THRESHOLD)
    {
        // probably found good match, start tracking these keypoints
        do_tracking = true;
        prev_corners = scene_corners;
		frame.copyTo(prev_frame);
        prev_pts = vector<Point2f>(num_homog_matches);
        for (size_t i = 0, k = 0; i < homog_matches.size(); i++) {
            if (homog_matches[i]) {
                prev_pts[k] = scene[i];
                k++;
            }
        }

        //-- Draw lines between the detected corners
        line( bgr_frame, scene_corners[0], scene_corners[1],
            Scalar( 0, 255, 0, 255), 4 );
        line( bgr_frame, scene_corners[1], scene_corners[2],
            Scalar( 0, 255, 0, 255), 4 );
        line( bgr_frame, scene_corners[2], scene_corners[3],
            Scalar( 0, 255, 0, 255), 4 );
        line( bgr_frame, scene_corners[3], scene_corners[0],
            Scalar( 0, 255, 0, 255), 4 );
        line( bgr_frame, (scene_corners[0]+scene_corners[1])*.5,
            (scene_corners[2]+scene_corners[3])*.5, Scalar(0,255,0,255),4);
        line( bgr_frame, (scene_corners[1]+scene_corners[2])*.5,
            (scene_corners[3]+scene_corners[0])*.5, Scalar(0,255,0,255),4);

        // found a good match for object, so augment it
        overallH = H;
        warpPerspective(*augment_img, *warped_img, overallH, augment_img->size());
        return DETECTED;
    }
    return DETECT_FAILED;
}

void getKeyPoints(Mat& img, vector<KeyPoint>& kpts)
{
#if USE_FAST_FEATURES
    FastFeatureDetector detector(FAST_THRESHOLD/*threshold*/, true/*nonmaxSuppression*/);
#elif USE_GOOD_FEATURES
    GoodFeaturesToTrackDetector detector(MAX_KPTS/*maxCorners*/, 0.01/*qualityLevel*/,
                                         1./*minDistance*/, 3/*blockSize*/, false/*useHarrisDetector*/, 0.04/*k*/);
#elif USE_MSER_FEATURES
    MserFeatureDetector detector(5/*delta*/, 60/*minArea*/, 14400/*maxArea*/, .25f/*maxVariation*/,
                                 .2f/*minDiversity*/, 200/*maxEvolution*/, 1.01/*areaThreshold*/,
                                 .003/*minMargin*/, 5/*edgeBlurSize*/);
#elif USE_STAR_FEATURES
    StarFeatureDetector detector(16/*maxSize*/, 30/*responseThreshold*/, 10/*lineThresholdProjected*/,
                                 8/*lineThresholdBinarized*/, 5/*suppressNonMaxSize*/);
#elif USE_SIFT_FEATURES
    SiftFeatureDetector detector(0.04/*threshold*/, 10.0/*edgeThreshold*/,
                                 4/*nOctaves*/,
                                 3/*nOctaveLayers*/,
                                 -1/*firstOctave*/,
                                 0/*angleMode*/);
#elif USE_SURF_FEATURES
    SurfFeatureDetector detector(400./*hessian threshold*/, 3/*octaves*/, 4/*octave layers*/);
#endif
    detector.detect(img, kpts);
}

void getDescriptors(Mat& img, vector<KeyPoint>& kpts, Mat& descriptors)
{
#if USE_SIFT_DESC
    SiftDescriptorExtractor extractor(3.0/*magnification*/, true/*isNormalize*/,
                                      true/*recalculateAngles*/, 4/*nOctaves*/,
                                      3/*nOctaveLayers*/, -1/*firstOctave*/, 0/*angleMode*/);
#elif USE_SURF_DESC
    SurfDescriptorExtractor extractor(4/*nOctaves*/, 2/*nOctaveLayers*/, false/*extended*/);
#elif USE_BRIEF_DESC
    BriefDescriptorExtractor extractor(BRIEF_DESC_LEN/*descriptor length in bytes*/);
#endif
    extractor.compute(img, kpts, descriptors);
}

/* query is what you are looking for/the template, train is where to look/the frame */
void matchDescriptors(Mat& queryDescriptors, Mat& trainDescriptors, vector<DMatch>& matches)
{
#if USE_BRUTE_MATCHER
#if USE_EUCL_MATCH
    BruteForceMatcher< L2<float> > matcher;
#elif USE_HAMMING_MATCH
    //TODO: is there a difference between Hamming and HammingLUT?
    BruteForceMatcher<HammingLUT> matcher;
#endif
#elif USE_FLANN_MATCHER
    const Ptr<flann::IndexParams>& idxParams = new flann::KDTreeIndexParams(4/*trees*/);
    const Ptr<flann::SearchParams>& searchParams = new flann::SearchParams(32/*checks (how many leaves to visit when searching for neighbors)*/,
            0/*eps (search for eps-approximate neighbors)*/,
            true/*sorted (only for radius search, require neighbors sorted by distance)*/);
    FlannBasedMatcher matcher(idxParams, searchParams);
#endif

#if DO_DEFAULT_MATCH
    matcher.match(queryDescriptors, trainDescriptors, matches);
#elif DO_KNN_MATCH
    vector<vector<DMatch> > knnMatches;
    int k = 2;
    matcher.knnMatch(queryDescriptors, trainDescriptors, knnMatches, k);
	for (size_t i = 0, len = knnMatches.size(); i < len; i++) {
		if (knnMatches[i][0].distance < 0.6*knnMatches[i][1].distance) {
			matches.push_back(knnMatches[i][0]);
		}
	}
#elif DO_RADIUS_MATCH
    vector<vector<DMatch> > radMatches;
    float maxDistance = 10.0;
    matcher.radiusMatch(queryDescriptors, trainDescriptors, radMatches, maxDistance);
    //TODO: transform radMatches to vector<DMatch>
#endif
}

extern "C" {
JNIEXPORT int JNICALL Java_com_ScavengerHunt_view_camera_CameraLiveView_processFrame(
	JNIEnv* env, jobject thiz, jlong matGrayAddr, jlong matRgbaAddr)
{
    Mat* frame = (Mat*) matGrayAddr;
    Mat* rgbFrame = (Mat*) matRgbaAddr;

    processFrame(*rgbFrame, *frame);
}

JNIEXPORT void JNICALL Java_com_ScavengerHunt_view_camera_CameraLiveView_initProcessing(JNIEnv* env, jobject thiz,
                    jint fheight, jint fwidth)
{
    initProcessing(fheight, fwidth);
}

JNIEXPORT void JNICALL Java_com_ScavengerHunt_view_camera_CameraLiveView_deinitProcessing(JNIEnv* env, jobject thiz)
{
    deinitProcessing();
}

JNIEXPORT void JNICALL Java_com_ScavengerHunt_view_camera_CameraLiveView_setupTrackable(JNIEnv* env, jobject thiz,
                    jstring fname, jint width, jint height, jboolean flip)
{
    const char* filename = env->GetStringUTFChars(fname, NULL);

    setupTrackable(filename, width, height, flip);

    env->ReleaseStringUTFChars(fname, filename);
}

JNIEXPORT void JNICALL Java_com_ScavengerHunt_view_camera_CameraLiveView_setAugmentImg(JNIEnv* env, jobject thiz,
                    jlong augImgMatAddr, jlong warpedImgMatAddr)
{
    augment_img = (Mat*) augImgMatAddr;
    warped_img = (Mat*) warpedImgMatAddr;
}

JNIEXPORT int JNICALL
        Java_com_ScavengerHunt_view_camera_CameraLiveView_detectFeatures(
        JNIEnv* env, jobject thiz, jlong frameMatAddr, jlong colorMatAddr) {
    Mat* frame = (Mat*) frameMatAddr;
    Mat* colorFrame = (Mat*) colorMatAddr;
    return detectFeatures(*frame, *colorFrame);
}

JNIEXPORT int JNICALL
        Java_com_ScavengerHunt_view_camera_CameraLiveView_tryObjDetect(
        JNIEnv* env, jobject thiz, jlong frameMatAddr, jlong colorMatAddr) {
    Mat* frame = (Mat*) frameMatAddr;
    Mat* colorFrame = (Mat*) colorMatAddr;
    return tryObjDetect(*frame, *colorFrame);
}
}

