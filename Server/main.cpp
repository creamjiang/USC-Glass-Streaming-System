/*
    Video Content Analysis

    This file is part of PWICE project.

    Contributors:
    - Wayne Huang
    - Xu Qiu
    - Brian Lan
    - Xinyu Chen
*/
#include "stdafx.h"

using namespace std;
using namespace cv;

int main(int argc, char** argv)
{
    if (argc < 2)   // We need two parameters, one for input file, and the other one for output file.
    {
        cout << "Usage: " << argv[0] << " input-file" << endl;
        cout << "Input can be either a file on the disk or a URL to a video stream." << endl;
        return 1;
    }
    cout << "Please wait for several seconds until the video is shown." << endl;
    VideoCapture vc(argv[1]);
    if (!vc.isOpened())
    {
        cerr << "Failed to open the input file!" << endl;
        return 1;
    }
    Mat frame;
    namedWindow("Stream");

    while (vc.read(frame))
    {
        imshow("Stream", frame);
        if (waitKey(40) >= 0) break;
    }
	return 0;
}
