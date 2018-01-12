package com.example.androidthings.assistant;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.google.assistant.embedded.v1alpha1.AudioInConfig;
import com.google.assistant.embedded.v1alpha1.AudioOutConfig;

import java.nio.ByteBuffer;

/**
 * Created by daniel_shi on 2017/6/16.
 */

public class AudioControl {
    private final String TAG = "AudioControl";

    //Audio
    private AudioRecord mAudioRecord = null;
    private AudioTrack mAudioTrack = null;
    private Thread mThreadRecord = null;

    //Audio Setting
//    static private int frequency = 16000;
//    private static int SAMPLE_BLOCK_SIZE = 1024;
//    static private int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
//    static private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
//    static private int recBufSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
//    static private int playBufSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

    // Audio constants.
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static AudioInConfig.Encoding ENCODING_INPUT = AudioInConfig.Encoding.LINEAR16;
    private static AudioOutConfig.Encoding ENCODING_OUTPUT = AudioOutConfig.Encoding.LINEAR16;
    private static final AudioInConfig ASSISTANT_AUDIO_REQUEST_CONFIG =
            AudioInConfig.newBuilder()
                    .setEncoding(ENCODING_INPUT)
                    .setSampleRateHertz(SAMPLE_RATE)
                    .build();
    private static final AudioOutConfig ASSISTANT_AUDIO_RESPONSE_CONFIG =
            AudioOutConfig.newBuilder()
                    .setEncoding(ENCODING_OUTPUT)
                    .setSampleRateHertz(SAMPLE_RATE)
                    .build();
    private static final AudioFormat AUDIO_FORMAT_STEREO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    private static final AudioFormat AUDIO_FORMAT_OUT_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    private static final AudioFormat AUDIO_FORMAT_IN_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    public static final int SAMPLE_BLOCK_SIZE = 1024;


    //State
    boolean isRecording = false;

    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    private long mVoiceStartedMillis;

    private AudioCallback mCallback;
    public static abstract class AudioCallback {

        /**
         * Called when the recorder starts hearing voice.
         */
        public void onStartRecord() {
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The size of the actual data in {@code data}.
         */
        public void onRecording(ByteBuffer data, int size) {
        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onEndRecord() {
        }
    }

    public AudioControl(AudioCallback callback) {
        mCallback = callback;
    }

    public void startRecording() {
        if (isRecording == false) {
            setIsRecording(true);
            mThreadRecord = new Thread(new RunnableRecord());
            mThreadRecord.start();
        }
    }

    public void endRecording() {
        if (isRecording) {
            setIsRecording(false);
            stopRecordThread();
            mCallback.onEndRecord();
        }
    }

    private void stopRecordThread() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
        if (mThreadRecord != null) {
            mThreadRecord.interrupt();
            mThreadRecord = null;
        }
        mLastVoiceHeardMillis = Long.MAX_VALUE;
    }

    private AudioRecord createAudioRecord() {
        int inputBufferSize = AudioRecord.getMinBufferSize(AUDIO_FORMAT_IN_MONO.getSampleRate(),
                AUDIO_FORMAT_IN_MONO.getChannelMask(),
                AUDIO_FORMAT_IN_MONO.getEncoding());
        AudioRecord record = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AUDIO_FORMAT_IN_MONO)
                .setBufferSizeInBytes(inputBufferSize)
                .build();
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "mAudioRecord initial fail" + record.getState());
            return null;
        }
        return record;
    }

    private class RunnableRecord implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Start recording thread.");

            mAudioRecord = createAudioRecord();
            if (mAudioRecord == null) {
                Log.e(TAG, "AudioRecord initial fail.");
                stopRecordThread();
            } else {
                mAudioRecord.startRecording();
            }
            while (isRecording) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                ByteBuffer audioData = ByteBuffer.allocateDirect(SAMPLE_BLOCK_SIZE);
                int bytesRecord = mAudioRecord.read(audioData, audioData.capacity(), AudioRecord.READ_BLOCKING);
                long now = System.currentTimeMillis();
                if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                    mVoiceStartedMillis = now;
                    mCallback.onStartRecord();
                }

                mCallback.onRecording(audioData, bytesRecord);
                mLastVoiceHeardMillis = now;
                if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                    Log.d(TAG, "Time's up! Stop Recording.");
                    endRecording();
                }
            }
        }
    }

    public void setIsRecording(boolean b) {
        isRecording = b;
    }

    public boolean getIsRecording() {
        return isRecording;
    }

    public void playAudio(ByteBuffer audioData) {
        if(mAudioTrack == null) {
            int outputBufferSize = AudioTrack.getMinBufferSize(AUDIO_FORMAT_OUT_MONO.getSampleRate(),
                    AUDIO_FORMAT_OUT_MONO.getChannelMask(),
                    AUDIO_FORMAT_OUT_MONO.getEncoding());
            mAudioTrack = new AudioTrack.Builder()
                    .setAudioFormat(AUDIO_FORMAT_OUT_MONO)
                    .setBufferSizeInBytes(outputBufferSize)
                    .build();
            mAudioTrack.play();
        }
        mAudioTrack.write(audioData, audioData.remaining(), AudioTrack.WRITE_BLOCKING);

    }

    //static function
    public static AudioInConfig getAudioInConfig() {
        return ASSISTANT_AUDIO_REQUEST_CONFIG;
    }

    public static AudioOutConfig getAudioOutConfig() {
        return ASSISTANT_AUDIO_RESPONSE_CONFIG;
    }


}
