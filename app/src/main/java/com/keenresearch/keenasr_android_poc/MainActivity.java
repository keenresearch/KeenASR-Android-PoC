package com.keenresearch.keenasr_android_poc;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.Toast;


import com.keenresearch.keenasr.KASRDecodingGraph;
import com.keenresearch.keenasr.KASRRecognizer;
import com.keenresearch.keenasr.KASRResult;
import com.keenresearch.keenasr.KASRRecognizerListener;
import com.keenresearch.keenasr.KASRBundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements KASRRecognizerListener {
    protected static final String TAG =MainActivity.class.getSimpleName();
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private TimerTask levelUpdateTask;
    private Timer levelUpdateTimer;

    private ASyncASRInitializerTask asyncASRInitializerTask;
    public static MainActivity instance;
    private Boolean micPermissionGranted = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // disable start button until initialization is completed
        final Button startButton = (Button)findViewById(R.id.startListening);
        startButton.setEnabled(false);

        // we need to make sure audio permission is granted before initializing KeenASR SDK
        requestAudioPermissions();

        if (KASRRecognizer.sharedInstance() == null) {
            Log.i(TAG, "Initializing KeenASR recognizer");
            KASRRecognizer.setLogLevel(KASRRecognizer.KASRRecognizerLogLevel.KASRRecognizerLogLevelInfo);
            Context context = this.getApplication().getApplicationContext();
            asyncASRInitializerTask = new ASyncASRInitializerTask(context);
            asyncASRInitializerTask.execute();
        } else {
            startButton.setEnabled(true);
        }

        MainActivity.instance = this;

        ((Button) findViewById(R.id.startListening)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.i(TAG, "Starting to listen...");
                final KASRRecognizer recognizer = KASRRecognizer.sharedInstance();

                levelUpdateTimer = new Timer();
                levelUpdateTask = new TimerTask() {
                    public void run() {
//                        Log.i(TAG, "     " + recognizer.getInputLevel());
                    }
                };
                levelUpdateTimer.schedule(levelUpdateTask, 0, 80); // ~12 updates/sec

                view.setEnabled(false);
                TextView resultText = (TextView)findViewById(R.id.resultText);
                resultText.setText("");
                recognizer.startListening();
            }
        });


    }

    public void onPartialResult(KASRRecognizer recognizer, final KASRResult result) {
        Log.i(TAG, "   Partial result: " + result);

        final TextView resultText = (TextView)findViewById(R.id.resultText);
        //resultText.setText(text);
        resultText.post(new Runnable() {
            @Override
            public void run() {
                resultText.setTextColor(Color.LTGRAY);
                resultText.setText(result.getCleanText());
            }
        });
    }

    public void onFinalResult(KASRRecognizer recognizer, final KASRResult result) {
        Log.i(TAG, "Final result: " + result);
        final TextView resultText = (TextView)findViewById(R.id.resultText);
        final Button startButton = (Button)findViewById(R.id.startListening);
        Log.i(TAG, "resultText: " + resultText);
        if (levelUpdateTimer!=null)
            levelUpdateTimer.cancel();

        Log.i(TAG, "audioFile is in " + recognizer.getLastRecordingFilename());

        boolean status = resultText.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Updating UI after receiving final result");
                if (result.getConfidence() > 0.8)
                    resultText.setTextColor(Color.GRAY);
                else
                    resultText.setTextColor(Color.argb(90, 200, 0, 0));

                resultText.setText(result.getCleanText());
                startButton.setEnabled(true);
            }
        });
        if (!status) {
            Log.w(TAG, "Unable to post runnable to the UI queue");
        }


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Log.i(TAG, "Requesting mic permission from the users");
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();
                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
                Log.i(TAG, "Requesting mic permission from the users");
            }
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Microphone permission has already been granted");
            micPermissionGranted = true;
        }
    }

    //Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    micPermissionGranted = true;
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio. You will have to allow microphone access from the Settings->App->KeenASR->Permissions'", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    private class ASyncASRInitializerTask extends AsyncTask<String, Integer, Long> {
        private Context context;

        public ASyncASRInitializerTask(Context context) {
            this.context = context;
        }

        protected Long doInBackground(String... params) {
            Log.i(TAG, "Installing ASR Bundle");
            KASRBundle asrBundle = new KASRBundle(this.context);
            ArrayList<String> assets = new ArrayList<String>();
            assets.add("librispeech-nnet2-en-us/decode.conf");
            assets.add("librispeech-nnet2-en-us/final.dubm");
            assets.add("librispeech-nnet2-en-us/final.ie");
            assets.add("librispeech-nnet2-en-us/final.mat");
            assets.add("librispeech-nnet2-en-us/final.mdl");
            assets.add("librispeech-nnet2-en-us/global_cmvn.stats");
            assets.add("librispeech-nnet2-en-us/ivector_extractor.conf");
            assets.add("librispeech-nnet2-en-us/mfcc.conf");
            assets.add("librispeech-nnet2-en-us/online_cmvn.conf");
            assets.add("librispeech-nnet2-en-us/splice.conf");
            assets.add("librispeech-nnet2-en-us/splice_opts");
            assets.add("librispeech-nnet2-en-us/wordBoundaries.int");
            assets.add("librispeech-nnet2-en-us/words.txt");

            assets.add("librispeech-nnet2-en-us/lang/lexicon.txt");
            assets.add("librispeech-nnet2-en-us/lang/phones.txt");
            assets.add("librispeech-nnet2-en-us/lang/tree");


            String asrBundleRootPath = getApplicationInfo().dataDir;
            String asrBundlePath = new String(asrBundleRootPath + "/librispeech-nnet2-en-us");
//            String asrBundlePath = new String(asrBundleRootPath + "/aspire-nnet3chain-en-us");
            try {
                asrBundle.installASRBundle(assets, asrBundleRootPath);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when installing ASR bundle" + e);
                return 0l;
            }
            Log.i(TAG, "Waiting for microphone permission to be granted");
            while (!micPermissionGranted) {
                ;
                // TODO should handle the situation where user denied to grant access
                // so we can return without initailizing the SDK
            }
            Log.i(TAG, "Microphone permission is granted");
            Log.i(TAG, "Initializing with bundle at path: " + asrBundlePath);
            KASRRecognizer.initWithASRBundleAtPath(asrBundlePath, getApplicationContext());
            String[] sentences = MainActivity.getSentences();

            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
            if (recognizer != null) {
               String dgName = "words";
                KASRDecodingGraph.createDecodingGraphFromSentences(sentences, recognizer, dgName); // TODO check return code
                recognizer.prepareForListeningWithCustomDecodingGraphWithName("words");
            } else {
                Log.e(TAG, "Unable to retrieve recognizer");
            }
            return 1l;
        }



        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            Log.i(TAG, "Initialized KeenASR in the background");
            Log.i(TAG, "Adding listener");
            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
            recognizer.addListener(MainActivity.instance);
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForGoodMatch, 1.0f);
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForAnyMatch, 1.0f);
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutMaxDuration, 10.0f);
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutForNoSpeech, 3.0f);

            recognizer.setCreateAudioRecordings(true);

            final Button startButton = (Button)findViewById(R.id.startListening);
            startButton.setEnabled(true);
        }
    }

    static {
        Log.i(TAG, "Loading shared libraries");
        System.loadLibrary("c++_shared");
        System.loadLibrary("keenasr-jni");
        Log.i(TAG, "Loaded shared libraries");
    }



    private static String[] getSentences() {
        String[] sentences = {
                "zero",
                "one",
                "two",
                "three",
                "four",
                "five",
                "six",
                "seven",
                "eight",
                "nine",
                "ten"
        };
        return sentences;
    }
}
