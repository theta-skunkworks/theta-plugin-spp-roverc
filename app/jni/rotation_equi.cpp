/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <cv.hpp>
#include <cmath>
#include <omp.h>

#define RAD(x)      M_PI*(x)/180.0
#define DEGREE(x)   180.0*(x)/M_PI

using namespace std;
using namespace cv;

extern "C"
{
    JNIEXPORT jstring JNICALL
    Java_com_theta360_spproverc_MainActivity_version(
            JNIEnv *env,
            jobject) {
        std::string version = cv::getVersionString();
        return env->NewStringUTF(version.c_str());
    }

    // Determine rotation matrix
    Mat eular2rot(jboolean reverseOrder, Vec3d theta)
    {
        // Calculate rotation about Roll axis
        double m_x[3][3] = {
                {1,       0,              0                 },
                {0,       cos(theta[0]),   -sin(theta[0])   },
                {0,       sin(theta[0]),   cos(theta[0])    }
        };
        Mat R_roll = Mat(3, 3, CV_64F, m_x);

        // Calculate rotation about Pitch axis
        double m_y[3][3] = {
                {cos(theta[1]),     0,      sin(theta[1])   },
                {0,                 1,      0               },
                {-sin(theta[1]),    0,      cos(theta[1])   }
        };
        Mat R_pitch = Mat(3, 3, CV_64F, m_y);

        // Calculate rotation about Yaw axis
        double m_z[3][3] = {
                {cos(theta[2]),     -sin(theta[2]),     0   },
                {sin(theta[2]),     cos(theta[2]),      0   },
                {0,                 0,                  1   }
        };
        Mat R_yaw = Mat(3, 3, CV_64F, m_z);

        // Combined rotation matrix
        Mat R;
        if (reverseOrder) {
            R = R_roll * R_pitch * R_yaw;    // Roll->Pitcj->Yaw order
        } else {
            R = R_yaw * R_pitch * R_roll;    // Yaw->Pitch->Roll order
        }

        return R;
    }

    // rotate pixel, in_vec as input(row, col)
    Vec2i rotate_pixel(const Vec2i& in_vec, Mat& rot_mat, int width, int height)
    {
        Vec2d vec_rad = Vec2d(M_PI*in_vec[0]/height, 2*M_PI*in_vec[1]/width);

        Vec3d vec_cartesian;
        vec_cartesian[0] = sin(vec_rad[0])*cos(vec_rad[1]);
        vec_cartesian[1] = sin(vec_rad[0])*sin(vec_rad[1]);
        vec_cartesian[2] = cos(vec_rad[0]);

        double* rot_mat_data = (double*)rot_mat.data;
        Vec3d vec_cartesian_rot;
        vec_cartesian_rot[0] = rot_mat_data[0]*vec_cartesian[0] + rot_mat_data[1]*vec_cartesian[1] + rot_mat_data[2]*vec_cartesian[2];
        vec_cartesian_rot[1] = rot_mat_data[3]*vec_cartesian[0] + rot_mat_data[4]*vec_cartesian[1] + rot_mat_data[5]*vec_cartesian[2];
        vec_cartesian_rot[2] = rot_mat_data[6]*vec_cartesian[0] + rot_mat_data[7]*vec_cartesian[1] + rot_mat_data[8]*vec_cartesian[2];

        Vec2d vec_rot;
        vec_rot[0] = acos(vec_cartesian_rot[2]);
        vec_rot[1] = atan2(vec_cartesian_rot[1], vec_cartesian_rot[0]);
        if(vec_rot[1] < 0)
            vec_rot[1] += M_PI*2;

        Vec2i vec_pixel;
        vec_pixel[0] = height*vec_rot[0]/M_PI;
        vec_pixel[1] = width*vec_rot[1]/(2*M_PI);

        return vec_pixel;
    }

    void rotate_rgb(Mat inRotMat, int im_width, int im_height, Vec3b* im_data, Vec3b* im_out_data )
    {
        #pragma omp parallel for    //Use OpenMP : Specify "-fopenmp -static-openmp" for the build option.
        for(int i = 0; i < static_cast<int>(im_height); i++) {
            for (int j = 0; j < static_cast<int>(im_width); j++) {
                // inverse warping
                Vec2i vec_pixel = rotate_pixel( Vec2i(i, j)
                        , inRotMat  //Special orthogonal matrix: inverse matrix equals original matrix.
                        , im_width, im_height);
                int origin_i = (int) (vec_pixel[0] + 0.5);
                int origin_j = (int) (vec_pixel[1] + 0.5);
                if ( (origin_i >= 0) && (origin_j >= 0) && (origin_i < im_height) && (origin_j < im_width) ) {
                    im_out_data[i * im_width + j] = im_data[origin_i * im_width + origin_j];
                }
            }
        }

        return;
    }

    JNIEXPORT jbyteArray
    JNICALL Java_com_theta360_spproverc_MainActivity_rotateEqui
            (
                    JNIEnv *env,
                    jobject obj,
                    jboolean reverseOrder,
                    jdouble yaw,
                    jdouble pitch,
                    jdouble roll,
                    jint w,
                    jint h,
                    jbyteArray src
            ) {
        // 要素列の取得
        // 最後に開放する必要がある
        jbyte *p_src = env->GetByteArrayElements(src, NULL);
        if (p_src == NULL) {
            return NULL;
        }


        // 配列をcv::Matに変換する
        cv::Mat m_src(h, w, CV_8UC4, (u_char *) p_src);
        cv::Mat m_src_rgb(h, w, CV_8UC4);

        // OpenCV process : ARGB(ABGR)->RGB
        cv::cvtColor(m_src, m_src_rgb, CV_RGBA2RGB);

        //rotateEqui
        Mat im_out(m_src_rgb.rows, m_src_rgb.cols, m_src_rgb.type());
        Mat rot_mat = eular2rot(reverseOrder, Vec3f( RAD(roll), -RAD(pitch), RAD(yaw) ) ).t();
        rotate_rgb(rot_mat, m_src_rgb.cols, m_src_rgb.rows, (Vec3b*)m_src_rgb.data, (Vec3b*)im_out.data);

        //RGB->ARGB
        cv::Mat im_out_rgba(h, w, CV_8UC4);
        cv::cvtColor(im_out, im_out_rgba, CV_RGB2RGBA);

        // 配列をcv::Matから取り出す
        u_char *p_dst = im_out_rgba.data;

        // 戻り値用に要素を割り当てる
        jbyteArray dst = env->NewByteArray(w * h * 4);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(src, p_src, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 4, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(src, p_src, 0);

        return dst;
    }

    JNIEXPORT jbyteArray
    JNICALL Java_com_theta360_spproverc_MainActivity_rotateEqui2
            (
                    JNIEnv *env,
                    jobject obj,

                    jboolean reverseOrder1,
                    jdouble yaw1,
                    jdouble pitch1,
                    jdouble roll1,

                    jboolean reverseOrder2,
                    jdouble yaw2,
                    jdouble pitch2,
                    jdouble roll2,

                    jint w,
                    jint h,
                    jbyteArray src
            ) {
        // 要素列の取得
        // 最後に開放する必要がある
        jbyte *p_src = env->GetByteArrayElements(src, NULL);
        if (p_src == NULL) {
            return NULL;
        }


        // 配列をcv::Matに変換する
        cv::Mat m_src(h, w, CV_8UC4, (u_char *) p_src);
        cv::Mat m_src_rgb(h, w, CV_8UC4);

        // OpenCV process : ARGB(ABGR)->RGB
        cv::cvtColor(m_src, m_src_rgb, CV_RGBA2RGB);

        //rotateEqui
        Mat im_out(m_src_rgb.rows, m_src_rgb.cols, m_src_rgb.type());
        Mat rot_mat1 = eular2rot(reverseOrder1, Vec3f( RAD(roll1), -RAD(pitch1), RAD(yaw1) ) ).t();
        Mat rot_mat2 = eular2rot(reverseOrder2, Vec3f( RAD(roll2), -RAD(pitch2), RAD(yaw2) ) ).t();
        Mat rot_mat = rot_mat1 * rot_mat2;
        rotate_rgb(rot_mat, m_src_rgb.cols, m_src_rgb.rows, (Vec3b*)m_src_rgb.data, (Vec3b*)im_out.data);

        //RGB->ARGB
        cv::Mat im_out_rgba(h, w, CV_8UC4);
        cv::cvtColor(im_out, im_out_rgba, CV_RGB2RGBA);

        // 配列をcv::Matから取り出す
        u_char *p_dst = im_out_rgba.data;

        // 戻り値用に要素を割り当てる
        jbyteArray dst = env->NewByteArray(w * h * 4);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(src, p_src, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 4, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(src, p_src, 0);

        return dst;
    }

}