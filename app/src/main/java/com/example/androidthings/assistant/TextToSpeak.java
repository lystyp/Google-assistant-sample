package com.example.androidthings.assistant;

import android.content.Context;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import static android.content.ContentValues.TAG;

/**
 * Created by daniel_shi on 2017/6/19.
 */

public class TextToSpeak {
    private Context mContext;
    private TextToSpeech mTts;

    private TtsCallback mCallback;

    public static abstract class TtsCallback {

        public void onStartSendAudio() {
        }

        public void onSending(ByteBuffer data, int size) {
        }

        public void onEndSend() {
        }
    }

    public TextToSpeak(Context context, TtsCallback callback) {
        mContext = context;
        mCallback = callback;
        createLanguageTTS();


    }

    public void speakText(String text) {
        File file = new File(getRecordFilename());
        if (file.exists()) {
            file.delete();
        }
//        mTts.speak( text, TextToSpeech.QUEUE_FLUSH, null );
//        mTts.getVoice();

        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {

            }

            @Override
            public void onDone(String utteranceId) {
                Thread thread = new Thread(new RunnableSending());
                thread.start();
            }

            @Override
            public void onError(String utteranceId) {

            }
        });
        mTts.synthesizeToFile(text, null, file, "Daniel");
    }

    private void createLanguageTTS() {
        if (mTts == null) {
            mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int arg0) {
                    // TTS 初始化成功
                    if (arg0 == TextToSpeech.SUCCESS) {
                        // 指定的語系: 英文(美國)
                        Locale l = Locale.US;  // 不要用 Locale.ENGLISH, 會預設用英文(印度)

                        // 目前指定的【語系+國家】TTS, 已下載離線語音檔, 可以離線發音
                        if (mTts.isLanguageAvailable(l) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                            mTts.setLanguage(l);
                        }
                    }
                }
            }
            );
        }
    }

    private String getRecordFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath + "/TestRecord");

        if (!file.exists()) {
            Log.d(TAG, "Create a folder " + file.getAbsolutePath());
            file.mkdirs();
        }
        return (file.getAbsolutePath() + "/" + "Test.txt");
    }

    private FileInputStream getFileInputStream() {
        FileInputStream is = null;
        try {
            is = new FileInputStream(getRecordFilename());
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return is;
    }

    private class RunnableSending implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Start recording thread.");
            mCallback.onStartSendAudio();
            FileInputStream input = getFileInputStream();
            int bytesPlay = AudioControl.SAMPLE_BLOCK_SIZE;
            while (bytesPlay > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                ByteBuffer playBuffer = ByteBuffer.allocateDirect(AudioControl.SAMPLE_BLOCK_SIZE);
                try {
                    bytesPlay = input.read(playBuffer.array(), 0, AudioControl.SAMPLE_BLOCK_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mCallback.onSending(playBuffer, bytesPlay);
                if (bytesPlay < 0) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mCallback.onEndSend();
                    Thread.currentThread().interrupt();

                }
            }
        }
    }

}
