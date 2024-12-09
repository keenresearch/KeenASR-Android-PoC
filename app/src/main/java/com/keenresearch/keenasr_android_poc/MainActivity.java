package com.keenresearch.keenasr_android_poc;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.keenresearch.keenasr.KASRAlternativePronunciation;

import java.io.File;
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
    private BluetoothManager btManager;

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

        btManager = BluetoothManager.getInstance(context);
        btManager.initBluetooth();

        if (KASRRecognizer.sharedInstance() == null) {
            Log.i(TAG, "Initializing KeenASR recognizer");
            KASRRecognizer.setLogLevel(KASRRecognizer.KASRRecognizerLogLevel.KASRRecognizerLogLevelDebug);
            asyncASRInitializerTask = new ASyncASRInitializerTask(context);
            asyncASRInitializerTask.execute();
        } else {
            startButton.setEnabled(true);
        }

        MainActivity.instance = this;


        ((Button) findViewById(R.id.startListening)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.i(TAG, "Starting to listen...");
                System.gc();
                final KASRRecognizer recognizer = KASRRecognizer.sharedInstance();

//                view.setEnabled(false);
                TextView resultText = (TextView)findViewById(R.id.resultText);
                resultText.setText("");
                recognizer.startListening();
            }
        });
    }

    // Partial result callback is called every ~180ms. Here, we just log the result and update the
    // text field, so the user is aware something is happenning
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
    // final recognition result, which contains a lot more detail than the partial result, as well
    // as some additional information about the "response" (the most recent interaction), including
    // access to audio recording and json metadata for the response.
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
        File dir = this.getApplication().getApplicationContext().getCacheDir();
        response.saveAudio(dir);
        response.saveJson(dir);

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
            String asrBundleName = "keenAK3m-nnet3chain-en-us";

            assets.add(asrBundleName + "/decode.conf");
            assets.add(asrBundleName + "/final.dubm");
            assets.add(asrBundleName + "/final.ie");
            assets.add(asrBundleName + "/final.mat");
            assets.add(asrBundleName + "/final.mdl");
            assets.add(asrBundleName + "/global_cmvn.stats");
            assets.add(asrBundleName + "/ivector_extractor.conf");
            assets.add(asrBundleName + "/mfcc.conf");
            assets.add(asrBundleName + "/online_cmvn.conf");
            assets.add(asrBundleName + "/splice.conf");
            assets.add(asrBundleName + "/splice_opts");
            assets.add(asrBundleName + "/wordBoundaries.int");
            assets.add(asrBundleName + "/words.txt");
            assets.add(asrBundleName + "/lang/lexicon.txt");
            assets.add(asrBundleName + "/lang/phones.txt");
            assets.add(asrBundleName + "/lang/tree");
            assets.add(asrBundleName + "/lang/unk_inv.fst");
            assets.add(asrBundleName + "/gop_assets/HCLG.fst");
            assets.add(asrBundleName + "/gop_assets/decode.conf");
            assets.add(asrBundleName + "/gop_assets/phone-to-pure-phone.int");
            assets.add(asrBundleName + "/gop_assets/phones-pure.txt");
            assets.add(asrBundleName + "/gop_assets/pronunciation_model_batch.ort");

            String asrBundleRootPath = getApplicationInfo().dataDir;
            String asrBundlePath = new String(asrBundleRootPath + "/" + asrBundleName);

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
                // so we can return without initializing the SDK
            }
            Log.i(TAG, "Microphone permission is granted");
            Log.i(TAG, "Initializing with bundle at path: " + asrBundlePath);
            KASRRecognizer.initWithASRBundleAtPath(asrBundlePath, getApplicationContext());

            Log.i(TAG, "Version: " + KASRRecognizer.version());

            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();

            if (recognizer != null) {
               String[] phrases = MainActivity.getPhrases();
               String dgName = "words";

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
              if ( ! KASRDecodingGraph.createDecodingGraphFromPhrases(phrases, recognizer, dgName)) {
                  Log.w(TAG, "Unable to create decoding graph " + dgName);
                  return 0l;
              } else {
                  Log.i(TAG, "Done creating decoding graph");
              }

              // we now use the graph we created to prepare the recognizer for listening
              if ( ! recognizer.prepareForListeningWithDecodingGraphWithName(dgName, true)) {
                  Log.w(TAG, "Unable to prepare for listening with graph " + dgName);
                  return 0l;
              }
              Log.i(TAG, "Recognizer prepared with graph: " + recognizer.getDecodingGraphName());
              // alternatively, if you were using contextual graph, you would also need to set the context
              // and switch it as necessary. Here, we set it to context 0
              // recognizer.prepareForListeningWithContextualDecodingGraphWithName(dgName, 0);
            } else {
                Log.e(TAG, "Unable to retrieve recognizer");
                return 0l;
            }
            return 1l;
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
            } else {
                Log.e(TAG, "Recognizer wasn't initialized properly");
            }
        }
    }

    // You can change this method to your liking, to define the phrases that will be used to build
    // a decoding graph. If you expect individual words, you should specify them as such; if you
    // expect phrases, you should specify them as phrases (not as individual words).
    private static String[] getPhrases() {
        String[] phrases = {
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

//        String[] phrases = {
//                "once upon a time there were three little pigs",
//                "one pig built a house of straw"
//        };
        return phrases;
    }
}


