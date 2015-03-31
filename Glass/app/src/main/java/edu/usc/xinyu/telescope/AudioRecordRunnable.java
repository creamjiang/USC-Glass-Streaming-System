package edu.usc.xinyu.telescope;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.googlecode.javacv.FFmpegFrameRecorder;

import java.nio.Buffer;
import java.nio.ShortBuffer;

class AudioRecordRunnable implements Runnable {
    private static final String LOG_TAG = AudioRecordRunnable.class.getSimpleName();

    private boolean runAudioThread = false;
    private int sampleRateInHz;
    private FFmpegFrameRecorder recorder;
    private AudioRecord audioRecord;
    public AudioRecordRunnable(int sampleRate, FFmpegFrameRecorder ffrecorder) {
        sampleRateInHz = sampleRate;
        recorder = ffrecorder;
    }

    @Override
    public void run() {
        // Set the thread priority
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        // Audio
        int bufferSize;
        short[] audioData;
        int bufferReadResult;

        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        audioData = new short[bufferSize];

        Log.d(LOG_TAG, "audioRecord.startRecording()");
        audioRecord.startRecording();

        // Audio Capture/Encoding Loop
        while (runAudioThread) {
            // Read from audioRecord
            bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
            if (bufferReadResult > 0) {
                //Log.i(LOG_TAG,"audioRecord bufferReadResult: " + bufferReadResult);

                // Changes in this variable may not be picked up despite it being "volatile"
                if (true) {
                    try {
                        // Write to FFmpegFrameRecorder
                        Buffer[] buffer = {ShortBuffer.wrap(audioData, 0, bufferReadResult)};
                        recorder.record(buffer);
                    } catch (FFmpegFrameRecorder.Exception e) {
                        Log.i(LOG_TAG,e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
        Log.i(LOG_TAG,"AudioThread Finished");

            /* Capture/Encoding finished, release recorder */
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Log.i(LOG_TAG,"audioRecord released");
        }
    }

    public void StopRecording() {
        if (runAudioThread) {
            runAudioThread = false;
        }
    }
}
