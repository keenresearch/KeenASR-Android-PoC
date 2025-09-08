package com.keenresearch.keenasr_android_poc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import com.keenresearch.keenasr.KASRResponse;
import com.keenresearch.keenasr.KASRResult;
import com.keenresearch.keenasr.KASRWord;
import com.keenresearch.keenasr.KASRPhone;
import com.keenresearch.keenasr.KASRRecognizerListener;
import com.keenresearch.keenasr.KASRBundle;

import java.io.File;
import java.io.IOException;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements KASRRecognizerListener {
    protected static final String TAG =MainActivity.class.getSimpleName();
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private TimerTask levelUpdateTask;
    private Timer levelUpdateTimer;

    public static MainActivity instance;
    private Boolean micPermissionGranted = false;
//    private BluetoothManager btManager;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // disable start button until initialization is completed
        final Button startButton = (Button)findViewById(R.id.startListening);
        startButton.setEnabled(false);
        // we need to make sure audio permission is granted before initializing KeenASR SDK
        requestAudioPermissions();

        Context context = this.getApplication().getApplicationContext();

//        btManager = BluetoothManager.getInstance(context);
//        btManager.initBluetooth();

        if (KASRRecognizer.sharedInstance() == null) {
            Log.i(TAG, "Initializing KeenASR recognizer");
            // set to KASRRecognizerLogLevelDebug for more detailed logs
            KASRRecognizer.setLogLevel(KASRRecognizer.KASRRecognizerLogLevel.KASRRecognizerLogLevelInfo);
            ASyncASRInitializerTask asyncASRInitializerTask = new ASyncASRInitializerTask(context);
            asyncASRInitializerTask.execute();
        } else {
            startButton.setEnabled(true);
        }

        MainActivity.instance = this;


        ((Button) findViewById(R.id.startListening)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.i(TAG, "Starting to listen...");
                final KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
                view.setEnabled(false);
                TextView resultText = (TextView)findViewById(R.id.resultText);
                resultText.setText("");
                recognizer.prepareForListeningWithDecodingGraphWithName("words", true);
                recognizer.startListening();
            }
        });
    }

    // Partial result callback is called periodically, as new text is recognized.
    // Here, we just show the result on the screen so the user is aware something
    // is happening. You could do other things with the result (using application
    // level logic, and/or update VAD parameters based on what has been recognized
    // so far.
    public void onPartialResult(KASRRecognizer recognizer, final KASRResult result) {
        Log.i(TAG, "   Partial result: " + result.getCleanText());

        final TextView resultText = (TextView)findViewById(R.id.resultText);
        resultText.post(new Runnable() {
            @Override
            public void run() {
                resultText.setTextColor(Color.LTGRAY);
                resultText.setText(result.getCleanText());
            }
        });
    }

    public void onTriggerPhrase(KASRRecognizer recognizer) {
        Log.i(TAG, "Trigger phrase occurred!");
    }

    // onFinalResponse callback is called when the recognizer stops listening. It provides the
    // final recognition result, which contains a lot more details than the partial result, as well
    // as some additional information about the "response" (the most recent interaction), including
    // access to audio recording and json metadata of the response and ability to schedule upload
    // of the response to Dashboard.
    public void onFinalResponse(final KASRRecognizer recognizer, final KASRResponse response) {
//    public void onFinalResult(final KASRRecognizer recognizer, final KASRResult result) {
        KASRResult result = response.getAsrResult();
        Log.i(TAG, "Final result: " + result);
        // we log word/phone info (in a real app you would do something else with this information)
        for (KASRWord w : result.getWords()) {
            Log.i(TAG, "  " + w.getText());
            for (KASRPhone p : w.getPhones()) {
              Log.i(TAG, "    " + p.getText() + "(" + p.getPronunciationScore() + ")");
            }
        }
        // we can (optionally save audio and json into the filesystem if they are needed for further
        // use in the app, or if you would like to push them to the back end
        // File dir = this.getApplication().getApplicationContext().getCacheDir();
        // response.saveAudio(dir);
        // response.saveJson(dir);

        // we can also queue the response for upload to Dashboard (Keen Research's backend) where
        // you can access audio, ASR result, etc. for further analysis. This code is commented out
        // since we don't want to fill up the disk space because KASRUploader needs to be created
        // with an app key, in order to start sending the data
//        if (! response.queueForUpload()) {
//            Log.w(TAG, "Unable to queue response for upload");
//        }

        // some additional metadata in the response
        Log.i(TAG, "audioFilepath:" + response.getAudioFilename());
        Log.i(TAG, "jsonFilepath:" + response.getJsonFilename());
        Log.i(TAG, "decoding graph: "  + response.getDecodingGraphName());
        Log.i(TAG, "asr bundle: "  + response.getAsrBundleName());
        Log.i(TAG, "version: "  + response.getSdkVersion());
        Log.i(TAG, "start time: "  + response.getStartTime());
//        Log.i(TAG, "Final result JSON: " + result.toJSON());

        final TextView resultText = (TextView)findViewById(R.id.resultText);
        final Button startButton = (Button)findViewById(R.id.startListening);

        if (levelUpdateTimer!=null)
            levelUpdateTimer.cancel();

        boolean status = resultText.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Updating UI after receiving final result");
                resultText.setTextColor(Color.GRAY);
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
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay!
                micPermissionGranted = true;
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                Toast.makeText(this, "Permissions Denied to record audio. You will have to allow microphone access from the Settings->App->KeenASR->Permissions'", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "onRequestPermissions only handles record audio permissions");
        }
    }

    // TODO - replace with Executors
    private class ASyncASRInitializerTask extends AsyncTask<String, Integer, Long> {
        private Context context;

        public ASyncASRInitializerTask(Context context) {
            this.context = context;
        }

        protected Long doInBackground(String... params) {
            Log.i(TAG, "Installing ASR Bundle");
            KASRBundle asrBundle = new KASRBundle(this.context);
            String asrBundleName = "keenA1m-nnet3chain-en-us";


            String asrBundleRootPath = getApplicationInfo().dataDir;
            String asrBundlePath = asrBundleRootPath + "/" + asrBundleName;

            try {
                asrBundle.installASRBundle(asrBundleName, asrBundleRootPath);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when installing ASR bundle" + e);
                return 0L;
            }
            Log.i(TAG, "Waiting for microphone permission to be granted");
            while (!micPermissionGranted) {
                ;
                // TODO should handle the situation where user denied to grant access
                // so we can return without initializing the SDK
            }
            Log.i(TAG, "Microphone permission is granted");
            Log.i(TAG, "Initializing with bundle at path: " + asrBundlePath);
            KASRRecognizer.initWithASRBundleAtPath(asrBundlePath, getApplicationContext());

            Log.i(TAG, "Version: " + KASRRecognizer.version());

            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();

            if (recognizer != null) {
                // this is more relevant for always-on listening, no need for it in tap-to-talk setup
                // recognizer.setVADGating(true);
                String[] phrases = MainActivity.getPhrases();
                String dgName = "words";

                // This showcases how we can add alternative pronunciations with specific tags
//              KASRAlternativePronunciation altPron1 = new KASRAlternativePronunciation("HOUSE", "HH AH0 Z", "BAD");
//              KASRAlternativePronunciation altPron2 = new KASRAlternativePronunciation("BOOK", "B AH0 Z", "BAD");
//              KASRAlternativePronunciation altPron3 = new KASRAlternativePronunciation("PAGE", "P AH0 Z", "BAD");
//
//              ArrayList<KASRAlternativePronunciation> altProns = new ArrayList<KASRAlternativePronunciation>();
//              altProns.add(altPron1);
//              altProns.add(altPron2);
//              altProns.add(altPron3);
//              Log.i("", "Setup with " + altProns.size() + " alternative pronunciations");

                // example of creating decoding graph with more control
                // phrases here would correspond to the content user is supposed to read
//              KASRDecodingGraph.createDecodingGraphFromPhrases(phrases, recognizer, altProns,
//                      KASRDecodingGraph.KASRSpeakingTask.KASRSpeakingTaskOralReading, 0.5, dgName);


                // example of creating contextual decoding graph (you will also need to call
                // corresponding prepareForListening method
//              ArrayList<ArrayList<String>> contextualPhrases = new ArrayList<ArrayList<String>>();
//              ArrayList<String> page1 = new ArrayList<String>();
//              page1.add("page one");
//              page1.add("page two");
//              contextualPhrases.add(page1);
//              ArrayList<String> page2 = new ArrayList<String>();
//              page2.add("book three");
//              page2.add("book four");
//              contextualPhrases.add(page2);
//
//              KASRDecodingGraph.createContextualDecodingGraphFromSentences(contextualPhrases, recognizer, altProns,
//                        KASRDecodingGraph.KASRSpeakingTask.KASRSpeakingTaskOralReading, dgName);

              // We don't have to recreate the decoding graph every time since they persist in the
              // file system when created, but during the development this could be a problem if th
              // list of phrases (or any other input parameters) is changed resulting in new
              // decoding graph not being re-created), so we opt to create it every time in this PoC.
              // In a real app you might want to encode some additional information in the decoding graph
              // name (e.g. your app version), to enforce decoding graphs are built only when needed
//            if (KASRDecodingGraph.decodingGraphWithNameExists(dgName, recognizer)) {
//              Log.i(TAG, "Decoding graph " + dgName + " already exists. IT WON'T BE RECREATED");
//            } else {

              Log.i(TAG, "Creating decoding graph");
              if ( ! KASRDecodingGraph.createDecodingGraphFromPhrases(phrases, recognizer,
                      null,
                      KASRDecodingGraph.KASRSpeakingTask.KASRSpeakingTaskDefault,
                      // we are using a bit more aggressive spokenNoise probability
                      0.8f, dgName)) {
                  Log.w(TAG, "Unable to create decoding graph " + dgName);
                  return 0L;
              } else {
                  Log.i(TAG, "Done creating decoding graph");
              }

              // we now use the graph we created to prepare the recognizer for listening
              if ( ! recognizer.prepareForListeningWithDecodingGraphWithName(dgName, true)) {
                  Log.w(TAG, "Unable to prepare for listening with graph " + dgName);
                  return 0L;
              }
              Log.i(TAG, "Recognizer prepared with graph: " + recognizer.getDecodingGraphName());
              // alternatively, if you were using contextual graph, you would also need to set the context
              // and switch it as necessary. Here, we set it to context 0
              // recognizer.prepareForListeningWithContextualDecodingGraphWithName(dgName, 0);
            } else {
                Log.e(TAG, "Unable to retrieve recognizer");
                return 0L;
            }
            return 1L;
        }

        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            Log.i(TAG, "Initialized KeenASR in the background");
            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
            if (recognizer!=null) {
                Log.i(TAG, "Adding listener");
                recognizer.addListener(MainActivity.instance);
                // for more details on trade-offs when settings end-silence parameters, please see
                // this page: https://keenresearch.com/keenasr-docs/keenasr-getting-started.html#start-and-stop-listening
                recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForGoodMatch, 1f);
                recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForAnyMatch, 1f);
                recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutMaxDuration, 20.0f);
                recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutForNoSpeech, 5.0f);

                final Button startButton = (Button) findViewById(R.id.startListening);
                startButton.setEnabled(true);
//                if (! KASRUploader.createDataUploader(recognizer, "YOUR_APP_DASHBOARD_KEY")) {
//                    Log.w(TAG, "Unable to create KASRUploader");
//                }
            } else {
                Log.e(TAG, "Recognizer wasn't initialized properly");
            }
        }
    }

    // You can change this method to your liking, to define the phrases that will be used to build
    // a decoding graph. If you expect individual words, you should specify them as such; if you
    // expect phrases, you should specify them as phrases (not as individual words).
    private static String[] getPhrases() {
        return new String[]{
                "I don't know",
                "yes",
                "no",
                "I love you",
                "I hate you",
                "how are you",
                "I am good",
                "I'm good",
                "I feel good",
                "I don't feel good",
                "I'm sick",
                "I am sick",
                "What's up",
                "How are things",
                "How is life",
                "How's life",
                "Let's go",
                "Let's dance",
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
                "ten",
                "I love how tall I am.",
                "I love to play ball."
        };
    }
}


