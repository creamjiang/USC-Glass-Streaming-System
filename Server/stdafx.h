/*
    Common include header

    This file is part of PWICE project.

    Created by:     Xinyu Chen
    Contributors:
    - Wayne Huang
    - Xu Qiu
    - Brian Lan
*/

#ifndef STDAFX_H_INCLUDED
#define STDAFX_H_INCLUDED

// std headers
#include <cstdio>
#include <iostream>
#include <mutex>
#include <thread>
#include <string>
#include <vector>
// OpenCV headers
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/imgproc/imgproc.hpp>
// Other headers
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
// Types and structs

typedef std::vector<cv::Rect> FaceResult;

typedef struct tagTextInfo
{
    cv::Rect box;
    std::string text;
} TextInfo;

typedef std::vector<TextInfo> TextResult;
#endif // STDAFX_H_INCLUDED
