**WE ARE HIRING:** https://keenresearch.com/careers.html

# KeenASR Android Proof of Concept App
This proof-of-concept app ships with a trial version of KeenASR SDK, which will exit (crash) the app 15min after the app starts. If you would like to obtain a version of the framework without this limitation, contact us at info@keenresearch.com.

By cloning this repository and downloading the trial KeenASR SDK or ASR Bundle you agree to the [Trial SDK Licensing Agreement](https://keenresearch.com/keenasr-docs/keenasr-trial-sdk-licensing-agreement.html)

For more details about the SDK see: http://keenresearch.com/keenasr-docs

For information about licensing: info@keenresearch.com

## KeenASR Proof-of-Concept App
Proof-of-concept app defines several randomly chosen phrases that will be used to create language model and the corresponding decoding graph. You can experiment with the list of phases (in MainActivity.java file); the main limit in terms of number of phrases you could use is the amount of time necessary to create the decoding graph (note that this is typically a one-time process).

For large vocabulary dictation-type tasks  decoding graph can be created ahead of time and bundled with the app.
