package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.ByteBuffer;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private static final int REQUEST_CONTACTS = 1;
    Context mContext;
    //Component
    private TextView    mTextState;
    private TextView    mTextSpeech;
    private TextView    mTextResponse;
    private EditText    mEditInput;
    private Button      mBtnRecord;
    private Button      mBtnSend;
    Handler mHandleForStateText;

    //Audio
    private AudioControl mAudioControl;
    private GoogleAssistant mGoogleAssistant;
    public TextToSpeak mTts;
    private final AudioControl.AudioCallback mAudioCallback = new AudioControl.AudioCallback() {

        @Override
        public void onStartRecord() {
            updateText();
            mGoogleAssistant.startAssistantRequest();
//            showStatus(true);
//            if (mSpeechService != null) {
//                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
//            }
        }

        @Override
        public void onRecording(ByteBuffer data, int size) {
            mGoogleAssistant.streamAssistantRequest(data);
        }

        @Override
        public void onEndRecord() {
            updateText();
            mGoogleAssistant.stopAssistantRequest();
        }

    };

    private GoogleAssistant.GoogleAssistantCallback mGoogleAssistantCallback = new GoogleAssistant.GoogleAssistantCallback() {
        @Override
        public void onRecognizeSuccessed() {
            mAudioControl.endRecording();
        }

        @Override
        public void onGetResult(String s) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextSpeech.setText(s);
                }
            });
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(AudioControl.getAudioOutConfig().getSampleRateHertz());
            }
        }

        @Override
        public void onSendAudioResult(ByteBuffer audioData) {
            if (mSpeechService != null) {
                mSpeechService.recognize(audioData.array(), audioData.array().length);
            }
            mAudioControl.playAudio(audioData);
        }

        @Override
        public void onResponseComplete() {
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }
    };

    private TextToSpeak.TtsCallback mTtsCallback = new  TextToSpeak.TtsCallback() {
        public void onStartSendAudio() {
            mGoogleAssistant.startAssistantRequest();
        }

        public void onSending(ByteBuffer data, int size) {
            mGoogleAssistant.streamAssistantRequest(data);
        }

        public void onEndSend() {
            mGoogleAssistant.stopAssistantRequest();
        }
    };

    //STT
    private SpeechService mSpeechService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            Log.d(TAG, "Speech Service connect successed.");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };
    private final SpeechService.Listener mSpeechServiceListener =
            new SpeechService.Listener() {
                @Override
                public void onSpeechRecognized(final String text, final boolean isFinal) {
                    if (isFinal) {
                        //get final voice to stt
                    }
                    if (mTextResponse != null && !TextUtils.isEmpty(text)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isFinal) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mTextResponse.setText(text + ".");
                                        }
                                    });
                                } else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mTextResponse.setText(text);
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Prepare Cloud Speech API
        bindService(new Intent(this, SpeechService.class), mServiceConnection, BIND_AUTO_CREATE);

        String permissionItem[] = {INTERNET, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE};
        for(String s:permissionItem) {
            int permission = ActivityCompat.checkSelfPermission(this, s);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions( this, permissionItem,
                        REQUEST_CONTACTS );
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(grantResults.length <= 0) {
            return;
        }
        for(int i = 0;i < grantResults.length;i++) {
           if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
               Log.e(TAG, "onRequestPermissionsResult: " + permissions[i]);
                   new AlertDialog.Builder(MainActivity.this)
                           .setTitle("Permissions Request")
                           .setMessage("Allow all permission to use the app.")
                           .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog, int which) {
                                   ((Activity)mContext).finish();
                               }
                           }).show();
           }
        }
    }

    private void initComponent() {
        mContext = this;
        //TTS
        mTts = new TextToSpeak(mContext, mTtsCallback);

        //Record
        mAudioControl = new AudioControl(mAudioCallback);
        mTextState = (TextView)findViewById(R.id.textRecordState);
        mTextSpeech = (TextView)findViewById(R.id.textSpeech);
        mTextResponse = (TextView)findViewById(R.id.textResponse);
        mBtnRecord = (Button)findViewById(R.id.btnRecord);
        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditInput.clearFocus();
                if (!mAudioControl.getIsRecording()) {
                    mAudioControl.startRecording();
                } else {
                    mAudioControl.endRecording();
                }
            }
        });
        mHandleForStateText = new Handler(mContext.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                if (!mAudioControl.getIsRecording() ) {
                    mTextState.setText("Click buttin to record.");
                } else if (mAudioControl.getIsRecording()) {
                    String c = (String) msg.obj;
                    if (c.equalsIgnoreCase("Recording...")) {
                        c = "Recording";
                    } else {
                        c = c + ".";
                    }
                    mTextState.setText(c);
                    mHandleForStateText.sendMessageDelayed(mHandleForStateText.obtainMessage(0, c), 300);
                }
            }
        };

        //Type
        mEditInput = (EditText)findViewById(R.id.editInput);
        mEditInput.clearFocus();
        mBtnSend = (Button)findViewById(R.id.btnSend);
        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = mEditInput.getText().toString();
                if(s != null && !s.equalsIgnoreCase("")) {
                    mTts.speakText(s);
                }
            }
        });

        //Google Assistant
        mGoogleAssistant = new GoogleAssistant(mContext, mGoogleAssistantCallback);


    }

    private void updateText() {
        Log.d(TAG, "updateText: " + mAudioControl.getIsRecording());
        if (mAudioControl.getIsRecording()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnRecord.setText("Stop");
                }
            });
            mHandleForStateText.removeMessages(0);
            mHandleForStateText.sendMessage(mHandleForStateText.obtainMessage(0, "Recording"));
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnRecord.setText("Record");
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

//        mSpeechService.removeListener(mSpeechServiceListener);
//        unbindService(mServiceConnection);
//        mSpeechService = null;

//        Log.i(TAG, "destroying assistant demo");
//        if (mAudioRecord != null) {
//            mAudioRecord.stop();
//            mAudioRecord = null;
//        }
//        if (mAudioTrack != null) {
//            mAudioTrack.stop();
//            mAudioTrack = null;
//        }

//        // 釋放 TTS
//        if( tts != null ) tts.shutdown();
//
//        super.onDestroy();
    }

}
