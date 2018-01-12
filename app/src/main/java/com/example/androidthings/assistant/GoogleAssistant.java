package com.example.androidthings.assistant;

import android.content.Context;
import android.util.Log;

import com.google.assistant.embedded.v1alpha1.ConverseConfig;
import com.google.assistant.embedded.v1alpha1.ConverseRequest;
import com.google.assistant.embedded.v1alpha1.ConverseResponse;
import com.google.assistant.embedded.v1alpha1.EmbeddedAssistantGrpc;
import com.google.protobuf.ByteString;

import org.json.JSONException;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;

/**
 * Created by daniel_shi on 2017/6/16.
 */

public class GoogleAssistant {
    private static final String TAG = "GoogleAssistant";

    // Google Assistant API constants.
    private static final String ASSISTANT_ENDPOINT = "embeddedassistant.googleapis.com";

    // gRPC client and stream observers.
    private EmbeddedAssistantGrpc.EmbeddedAssistantStub mAssistantService;
    private StreamObserver<ConverseRequest> mAssistantRequestObserver;
    private StreamObserver<ConverseResponse> mAssistantResponseObserver =
            new StreamObserver<ConverseResponse>() {
                @Override
                public void onNext(ConverseResponse value) {
                    switch (value.getConverseResponseCase()) {
                        case EVENT_TYPE:
                            Log.d(TAG, "converse response event: " + value.getEventType());
                            mCallback.onRecognizeSuccessed();
                            break;
                        case RESULT:
                            final String spokenRequestText = value.getResult().getSpokenRequestText();
                            if (!spokenRequestText.isEmpty()) {
                                Log.i(TAG, "assistant request text: " + spokenRequestText);
                                mCallback.onGetResult(spokenRequestText);
                            }

                            final String spokenResponseText = value.getResult().getSpokenResponseText();
                            Log.i(TAG, "assistant response text: " + spokenResponseText);

                            break;
                        case AUDIO_OUT:

                            final ByteBuffer audioData =
                                    ByteBuffer.wrap(value.getAudioOut().getAudioData().toByteArray());
                            mCallback.onSendAudioResult(audioData);
                            break;
                        case ERROR:
                            Log.e(TAG, "converse response error: " + value.getError());
                            break;
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "converse error:", t);
                }

                @Override
                public void onCompleted() {
                    Log.i(TAG, "assistant response finished");
                    mCallback.onResponseComplete();
                }
            };

    private GoogleAssistantCallback mCallback;
    public static abstract class GoogleAssistantCallback {
        public void onRecognizeSuccessed() {

        }

        public void onGetResult(String s){

        }

        public void onSendAudioResult(ByteBuffer audioData){

        }

        public void onResponseComplete(){

        }
    }

    public GoogleAssistant(Context context, GoogleAssistantCallback callback) {
        mCallback = callback;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(ASSISTANT_ENDPOINT).build();
        try {
            mAssistantService = EmbeddedAssistantGrpc.newStub(channel)
                    .withCallCredentials(MoreCallCredentials.from(
                            Credentials.fromResource(context, R.raw.credentials)
                    ));
        } catch (IOException |JSONException e) {
            Log.e(TAG, "error creating assistant service:", e);
        }
    }

    public void startAssistantRequest() {
        mAssistantRequestObserver = mAssistantService.converse(mAssistantResponseObserver);
        mAssistantRequestObserver.onNext(ConverseRequest.newBuilder().setConfig(
                ConverseConfig.newBuilder()
                        .setAudioInConfig(AudioControl.getAudioInConfig())
                        .setAudioOutConfig(AudioControl.getAudioOutConfig())
                        .build()).build());
        Log.d(TAG, "Assistant init Finish.");
    }

    public void streamAssistantRequest(ByteBuffer audioData) {
        Log.d(TAG, "streaming ConverseRequest: " + audioData.array().length);
        mAssistantRequestObserver.onNext(ConverseRequest.newBuilder()
                .setAudioIn(ByteString.copyFrom(audioData, 1024))
                .build());
    }

    public void stopAssistantRequest() {
        if (mAssistantRequestObserver != null) {
            mAssistantRequestObserver.onCompleted();
            mAssistantRequestObserver = null;
        }
    }

}
