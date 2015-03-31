package edu.usc.xinyu.telescope;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ShortBuffer;

import static com.googlecode.javacv.cpp.opencv_core.*;

public class MainActivity extends Activity {

    private final static String LOG_TAG = "MainActivity";

    private PowerManager.WakeLock mWakeLock;
    private static final String CS_SERVER = "http://cs-server.usc.edu:33253/getaddr.php";
    private String serverAddress = "http://192.168.0.9:8090/webcam.ffm";

    private volatile FFmpegFrameRecorder recorder;
    boolean recording = false;
    long startTime = 0;

    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 320;
    private int imageHeight = 240;

    private int frameRate = 25;

    private Thread audioThread;
    volatile boolean runAudioThread = true;

    private AudioRecordRunnable audioRecordRunnable;

    private CameraView cameraView;
    private IplImage yuvIplimage = null;

    private LinearLayout mainLayout;

    AddressRetriever addressRetriever;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        initLayout();
        addressRetriever = new AddressRetriever();
        addressRetriever.execute(CS_SERVER);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, LOG_TAG);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
        //return super.onCreateOptionsMenu(main_menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.start_streaming:
                if (!recording) {
                    startRecording();
                    Log.i(LOG_TAG, "Start recording pressed");
                }
                break;
            case R.id.stop_streaming:
                if (recording) {
                    stopRecording();
                    Log.i(LOG_TAG, "Stop recording pressed");
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            openOptionsMenu();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initLayout() {

        mainLayout = (LinearLayout) this.findViewById(R.id.camera_layout);

        cameraView = new CameraView(this);

        LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mainLayout.addView(cameraView, layoutParam);
        Log.v(LOG_TAG, "added cameraView to mainLayout");
    }

    private void initRecorder() {
        Log.w(LOG_TAG,"initRecorder");

        if (yuvIplimage == null) {
            // Recreated after frame size is set in surface change method
            yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
            //yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_32S, 2);

            Log.i(LOG_TAG, "IplImage.create");
        }

        recorder = new FFmpegFrameRecorder(serverAddress, imageWidth, imageHeight, 1);
        Log.i(LOG_TAG, "FFmpegFrameRecorder: " + serverAddress + " imageWidth: " + imageWidth + " imageHeight " + imageHeight);

        recorder.setFormat("ffm");
        Log.i(LOG_TAG, "recorder.setFormat(ffm)");

        recorder.setSampleRate(sampleAudioRateInHz);
        Log.i(LOG_TAG, "recorder.setSampleRate(sampleAudioRateInHz)");

        // re-set in the surface changed method as well
        recorder.setFrameRate(frameRate);
        Log.i(LOG_TAG, "recorder.setFrameRate(frameRate)");

        // Create audio recording thread
        audioRecordRunnable = new AudioRecordRunnable(sampleAudioRateInHz, recorder);
        audioThread = new Thread(audioRecordRunnable);
    }

    // Start the capture
    public void startRecording() {
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (!wifi.isConnected()) {
            // TODO Add text prompt to user that wifi is not connected.
            return;
        }
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        // This should stop the audio thread from running
        runAudioThread = false;

        if (recorder != null && recording) {
            recording = false;
            Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
                audioRecordRunnable.StopRecording();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

        private boolean previewRunning = false;

        private SurfaceHolder holder;
        private Camera camera;

        private byte[] previewBuffer;

        long videoTimestamp = 0;

        Bitmap bitmap;

        public CameraView(Context _context) {
            super(_context);

            holder = this.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open();

            try {
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);

                Camera.Parameters currentParams = camera.getParameters();
                //Log.i(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
                Log.i(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);

                // Use these values
                imageWidth = currentParams.getPreviewSize().width;
                imageHeight = currentParams.getPreviewSize().height;
                //frameRate = 30; //currentParams.getPreviewFrameRate();

                bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ALPHA_8);

                camera.startPreview();
                previewRunning = true;
            }
            catch (IOException e) {
                Log.i(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(LOG_TAG,"Surface Changed: width " + width + " height: " + height);

            // Get the current parameters
            Camera.Parameters currentParams = camera.getParameters();
            //Log.i(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
            Log.i(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);

            // Use these values
            imageWidth = currentParams.getPreviewSize().width;
            imageHeight = currentParams.getPreviewSize().height;
            //frameRate = 30; //currentParams.getPreviewFrameRate();

            // Create the yuvIplimage if needed
            yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
            //yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_32S, 2);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                camera.setPreviewCallback(null);

                previewRunning = false;
                camera.release();

            } catch (RuntimeException e) {
                Log.i(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (yuvIplimage != null && recording) {
                videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);

                // Put the camera preview frame right into the yuvIplimage object
                yuvIplimage.getByteBuffer().put(data);
                Log.i(LOG_TAG, "onPreviewFrame: Data size=" + String.valueOf(data.length));

                try {
                    // Get the correct time
                    recorder.setTimestamp(videoTimestamp);
                    Log.i(LOG_TAG, "yuvIplimage info:" + String.valueOf(yuvIplimage.imageSize()));
                    // Record the image into FFmpegFrameRecorrder
                    recorder.record(yuvIplimage);

                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.i(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    class AddressRetriever extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String serverAddress = params[0];
            try {
                URL url = new URL(serverAddress);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
                InputStreamReader isr = new InputStreamReader(inputStream);
                BufferedReader br = new BufferedReader(isr);
                String response = br.readLine();
                Log.d(LOG_TAG, "JSON=" + response);
                return response;
            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String s) {
            try {
                JSONObject jsonobj = new JSONObject(s);
                serverAddress = jsonobj.getString("address");
                Log.d(LOG_TAG, "Server=" + serverAddress);
                initRecorder();
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }

        }
    }
}
