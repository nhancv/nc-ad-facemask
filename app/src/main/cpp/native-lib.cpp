//
// Created by iNhan Cao on 2019-06-18.
//

#include <jni.h>
#include <string>

#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <android/bitmap.h>
#include <android/log.h>

#define log_print __android_log_print

using namespace cv;

///
class PointState {
public:
    PointState(cv::Point2f point)
            :
            m_point(point),
            m_kalman(4, 2, 0, CV_64F) {
        Init();
    }

    void Update(cv::Point2f point) {
        cv::Mat measurement(2, 1, CV_64FC1);
        if (point.x < 0 || point.y < 0) {
            Predict();
            measurement.at<double>(0) = m_point.x;  //update using prediction
            measurement.at<double>(1) = m_point.y;

            m_isPredicted = true;
        } else {
            measurement.at<double>(0) = point.x;  //update using measurements
            measurement.at<double>(1) = point.y;

            m_isPredicted = false;
        }

        // Correction
        cv::Mat estimated = m_kalman.correct(measurement);
        m_point.x = static_cast<float>(estimated.at<double>(0));   //update using measurements
        m_point.y = static_cast<float>(estimated.at<double>(1));

        Predict();
    }

    cv::Point2f GetPoint() const {
        return m_point;
    }

    bool IsPredicted() const {
        return m_isPredicted;
    }

private:
    cv::Point2f m_point;
    cv::KalmanFilter m_kalman;

    double m_deltaTime = 0.2;
    double m_accelNoiseMag = 0.3;

    bool m_isPredicted = false;

    void Init() {
        m_kalman.transitionMatrix = (cv::Mat_<double>(4, 4) <<
                                                            1, 0, m_deltaTime, 0,
                0, 1, 0, m_deltaTime,
                0, 0, 1, 0,
                0, 0, 0, 1);

        m_kalman.statePre.at<double>(0) = m_point.x; // x
        m_kalman.statePre.at<double>(1) = m_point.y; // y

        m_kalman.statePre.at<double>(2) = 1; // init velocity x
        m_kalman.statePre.at<double>(3) = 1; // init velocity y

        m_kalman.statePost.at<double>(0) = m_point.x;
        m_kalman.statePost.at<double>(1) = m_point.y;

        cv::setIdentity(m_kalman.measurementMatrix);

        m_kalman.processNoiseCov = (cv::Mat_<double>(4, 4) <<
                                                           pow(m_deltaTime, 4.0) / 4.0, 0, pow(m_deltaTime, 3.0) /
                                                                                           2.0, 0,
                0, pow(m_deltaTime, 4.0) / 4.0, 0, pow(m_deltaTime, 3.0) / 2.0,
                pow(m_deltaTime, 3.0) / 2.0, 0, pow(m_deltaTime, 2.0), 0,
                0, pow(m_deltaTime, 3.0) / 2.0, 0, pow(m_deltaTime, 2.0));


        m_kalman.processNoiseCov *= m_accelNoiseMag;

        cv::setIdentity(m_kalman.measurementNoiseCov, cv::Scalar::all(0.1));

        cv::setIdentity(m_kalman.errorCovPost, cv::Scalar::all(.1));
    }

    cv::Point2f Predict() {
        cv::Mat prediction = m_kalman.predict();
        m_point.x = static_cast<float>(prediction.at<double>(0));
        m_point.y = static_cast<float>(prediction.at<double>(1));
        return m_point;
    }
};

///
void TrackPoints(cv::Mat prevFrame, cv::Mat currFrame,
                 const std::vector<cv::Point2f> &currLandmarks,
                 std::vector<PointState> &trackPoints) {
    // Lucas-Kanade
    cv::TermCriteria termcrit(cv::TermCriteria::COUNT | cv::TermCriteria::EPS, 30, 0.01);
    cv::Size winSize(7, 7);

    std::vector<uchar> status(trackPoints.size(), 0);
    std::vector<float> err;
    std::vector<cv::Point2f> newLandmarks;

    std::vector<cv::Point2f> prevLandmarks;
    std::for_each(trackPoints.begin(), trackPoints.end(),
                  [&](const PointState &pts) { prevLandmarks.push_back(pts.GetPoint()); });

    cv::calcOpticalFlowPyrLK(prevFrame, currFrame, prevLandmarks, newLandmarks, status, err, winSize, 3, termcrit, 0,
                             0.001);

    for (size_t i = 0; i < status.size(); ++i) {
        if (status[i]) {
            trackPoints[i].Update((newLandmarks[i] + currLandmarks[i]) / 2);
        } else {
            trackPoints[i].Update(currLandmarks[i]);
        }
    }
}

cv::Mat currGray;
cv::Mat prevGray;

std::vector<PointState> trackPoints;

extern "C" JNIEXPORT void JNICALL
Java_com_nhancv_facemask_tracking_PointState_init(
        JNIEnv *env,
        jobject /* this */) {
    trackPoints.reserve(120);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nhancv_facemask_tracking_PointState_stabilize(
        JNIEnv *env,
        jobject /* this */,
        jboolean has_face, jfloatArray landmark_points) {

    if (has_face) {
        jfloat *tempPointer = env->GetFloatArrayElements(landmark_points, JNI_FALSE);
        int dataSize = env->GetArrayLength(landmark_points);

        std::vector<cv::Point2f> landmarks;
        landmarks.reserve(static_cast<unsigned int>(dataSize));
        for (int i = 0; i < dataSize; i += 2) {
            landmarks.emplace_back(Point2f(*tempPointer, *(tempPointer + 1)));
        }

        if (prevGray.empty()) {
            trackPoints.clear();

            for (cv::Point2f lp : landmarks) {
                trackPoints.emplace_back(lp);
            }
        } else {
            if (trackPoints.empty()) {
                for (cv::Point2f lp : landmarks) {
                    trackPoints.emplace_back(lp);
                }
            } else {
                TrackPoints(prevGray, currGray, landmarks, trackPoints);
            }
        }

    }

    for (const PointState &tp : trackPoints) {
        log_print(ANDROID_LOG_VERBOSE, "stabilize", "Pointf %f %f", tp.GetPoint().x, tp.GetPoint().y);
    }

    prevGray = currGray;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhancv_facemask_tracking_PointState_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nhancv_facemask_tracking_PointState_canny(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap,
        jstring destination) {

    // Get information about format and size
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    // Get pointer to pixel buffer
    void *pixels = 0;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    {

        Mat input(info.height, info.width, CV_8UC4, pixels);
        Mat dst;
        // Convert to gray
        cvtColor(input, input, COLOR_BGR2GRAY);
        // Histogram equalization
        equalizeHist(input, dst);
        // Saturation by 10%
        float alpha = 1.1f;
        float beta = 12.75f;
        dst.convertTo(dst, -1, alpha, beta);

        // Save to destination
        const char *dest = env->GetStringUTFChars(destination, 0);
        imwrite(dest, dst);
        env->ReleaseStringUTFChars(destination, dest);
    }

    // Release the Bitmap buffer once we have it inside our Mat
    AndroidBitmap_unlockPixels(env, bitmap);
}