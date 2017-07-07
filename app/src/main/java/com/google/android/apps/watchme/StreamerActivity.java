/*
 * Copyright (c) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.watchme;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.google.android.apps.watchme.util.EventData;
import com.google.android.apps.watchme.util.Utils;
import com.google.android.apps.watchme.util.YouTubeApi;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;

import net.ossrs.yasea.SrsCameraView;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsPublisher;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;

import timber.log.Timber;

import static com.google.android.apps.watchme.MainActivity.APP_NAME;


public class StreamerActivity extends Activity implements SrsEncodeHandler.SrsEncodeListener, RtmpHandler.RtmpListener, SrsRecordHandler.SrsRecordListener {

    private static final String LOG_TAG = StreamerActivity.class.getSimpleName();

    private String rtmpUrl;

    private String broadcastId;

    private SrsPublisher mPublisher;
    private Button btnPublish;
    private Button btnSwitchCamera;
    private Button btnEndEvent;
    private GoogleAccountCredential credential;
    boolean one = false;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(APP_NAME, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.streamer);

        broadcastId = getIntent().getStringExtra(YouTubeApi.BROADCAST_ID_KEY);
        //Log.v(MainActivity.APP_NAME, broadcastId);

        btnPublish = (Button) this.findViewById(R.id.publish);
        btnSwitchCamera = (Button) this.findViewById(R.id.swCam);
        btnEndEvent = (Button) this.findViewById(R.id.end_event);
        rtmpUrl = getIntent().getStringExtra(YouTubeApi.RTMP_URL_KEY);

        if (rtmpUrl == null) {
            Log.w(APP_NAME, "No RTMP URL was passed in; bailing.");
            finish();
        }
        Log.i(APP_NAME, String.format("Got RTMP URL '%s' from calling activity.", rtmpUrl));

        mPublisher = new SrsPublisher((SrsCameraView) findViewById(R.id.glsurfaceview_camera));
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        mPublisher.setPreviewResolution(640, 360);
        mPublisher.setOutputResolution(360, 640);
        mPublisher.setVideoHDMode();
        mPublisher.startCamera();

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPublish.getText().toString().contentEquals("Go Live!")) {
                    mPublisher.startPublish(rtmpUrl);
                    mPublisher.startCamera();
                    btnPublish.setText("Stop");
                } else if (btnPublish.getText().toString().contentEquals("Stop")) {
                    mPublisher.stopPublish();
                    mPublisher.stopRecord();
                    btnPublish.setText("Go Live!");
                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPublisher.switchCameraFace((mPublisher.getCamraId() + 1) % Camera.getNumberOfCameras());
            }
        });

        btnEndEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endEvent(v);
            }
        });

        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(Utils.SCOPES));
        // set exponential backoff policy
        credential.setBackOff(new ExponentialBackOff());

        //start live
        btnPublish.performClick();
    }

    @Override
    protected void onResume() {
        Log.d(APP_NAME, "onResume");

        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
//        mPublisher.resumeRecord();
    }

    @Override
    protected void onPause() {
        Log.d(APP_NAME, "onPause");

        super.onPause();
        mPublisher.pauseRecord();
    }

    @Override
    protected void onDestroy() {
        Log.d(APP_NAME, "onDestroy");

        super.onDestroy();
        mPublisher.stopPublish();
        mPublisher.stopRecord();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPublisher.stopEncode();
        mPublisher.stopRecord();
        mPublisher.setScreenOrientation(newConfig.orientation);
        if (btnPublish.getText().toString().contentEquals("Stop")) {
            mPublisher.startEncode();
        }
        mPublisher.startCamera();
    }

    public void endEvent(View view) {
        Intent data = new Intent();
        data.putExtra(YouTubeApi.BROADCAST_ID_KEY, broadcastId);
        if (getParent() == null) {
            setResult(Activity.RESULT_OK, data);
        } else {
            getParent().setResult(Activity.RESULT_OK, data);
        }
        finish();
    }

    @Override
    public void onNetworkWeak() {
        Toast.makeText(getApplicationContext(), "Network weak", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNetworkResume() {
        Toast.makeText(getApplicationContext(), "Network resume", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpConnecting(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpConnected(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoStreaming() {

    }

    @Override
    public void onRtmpAudioStreaming() {

    }

    @Override
    public void onRtmpStopped() {
        Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpDisconnected() {
        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {
        Log.i(LOG_TAG, String.format("Output Fps: %f", fps));
    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(LOG_TAG, String.format("Video bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(LOG_TAG, String.format("Video bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(LOG_TAG, String.format("Audio bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(LOG_TAG, String.format("Audio bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        handleException(e);
    }

    @Override
    public void onRecordPause() {
        Toast.makeText(getApplicationContext(), "Record paused", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordResume() {
        Toast.makeText(getApplicationContext(), "Record resumed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordStarted(String msg) {
        Toast.makeText(getApplicationContext(), "Recording file: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordFinished(String msg) {
        Toast.makeText(getApplicationContext(), "MP4 file saved: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRecordIOException(IOException e) {
        handleException(e);
    }

    private void handleException(Exception e) {
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText("Go Live!");
        } catch (Exception e1) {
            //
        }
    }

    public void startStreaming(String broadcastId) {

        new StartEventTask().execute(broadcastId);

    }

    private class StartEventTask extends AsyncTask<String, Void, Void> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(StreamerActivity.this, null,
                    getResources().getText(R.string.startingEvent), true);
        }

        final HttpTransport transport = AndroidHttp.newCompatibleTransport();
        final JsonFactory jsonFactory = new GsonFactory();

        @Override
        protected Void doInBackground(String... params) {
            YouTube youtube = new YouTube.Builder(transport, jsonFactory,
                    credential).setApplicationName("Watchme")
                    .build();
            try {
                YouTubeApi.startEvent(youtube, params[0]);
            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), 3);
            } catch (IOException e) {
                Log.e(APP_NAME, "", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            progressDialog.dismiss();
            mPublisher.startPublish(rtmpUrl);
            mPublisher.startCamera();
            btnPublish.setText("Stop");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 3:
                if (resultCode != Activity.RESULT_OK) {
                    chooseAccount();
                }
                break;
        }
    }

    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(),
                2);
    }
}
