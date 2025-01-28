**WE ARE HIRING:** https://keenresearch.com/careers.html

# KeenASR Android Proof of Concept App
This proof-of-concept app ships with a trial version of KeenASR SDK, which will exit (crash) the app 15min after the app starts. If you would like to obtain a version of the framework without this limitation, contact us at info@keenresearch.com.

**NOTE:** when deploying the APK file from Android Studio, make sure your phone is not locked. Android Studio will push and start the app even if the phone is locked, but SDK won't be initialized properly because of restrictions on using the microphone while the app is not in the background. (in real-life scenario, app will be started on an unlocked phone when user taps on the app icon)

By cloning this repository and downloading the trial KeenASR SDK or ASR Bundle you agree to the [Trial SDK Licensing Agreement](https://keenresearch.com/keenasr-docs/keenasr-trial-sdk-licensing-agreement.html)

For more details about the SDK see: http://keenresearch.com/keenasr-docs

For information about licensing: info@keenresearch.com

## KeenASR Proof-of-Concept App
Proof-of-concept app defines several randomly chosen phrases that will be used to create language model and the corresponding decoding graph. You can experiment with the list of phases (in MainActivity.java file); the main limit in terms of number of phrases you could use is the amount of time necessary to create the decoding graph (note that this is typically a one-time process).

For large vocabulary dictation-type tasks  decoding graph can be created ahead of time and bundled with the app.
