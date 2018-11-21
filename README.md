# KeenASR Android Proof of Concept App
This proof-of-concept app ships with a trial version of KeenASR SDK, which will exit (crash) the app 15min after the app starts. If you would like to obtain a version of the framework without this limitation, contact us at info@keenresearch.com.

By cloning this repository and downloading the trial KeenASR SDK or ASR Bundle you agree to the [Trial SDK Licensing Agreement](https://keenresearch.com/keenasr-docs/keenasr-trial-sdk-licensing-agreement.html)

For more details about the SDK see: http://keenresearch.com/keenasr-docs

We can provide smaller ASR Bundles that will work in real-time on older Android devices.

For information about licensing: info@keenresearch.com

## KeenASR Proof-of-Concept App
Proof-of-concept app defines several randomly chosen phrases that will be used to create language model and the corresponding decoding graph. You can experiment with the list of phases (in MainActivity.java file); the main limit in terms of number of phrases you could use is the amount of time necessary to create the decoding graph (note that this is typically a one-time process).

For large vocabulary dictation-type tasks, due to memory constraints it's not feasible to create decoding graph on the device; it can be created ahead of time and bundled with the app.

**WARNING: When checking out project on Windows**, Git will modify configuration (text) files in the ASR Bundle and change line endings from LF to CRLF, which will prevent SDK from properly initializing (the app will just crash). For ways to deal with line endings and Git, please check https://help.github.com/articles/dealing-with-line-endings/. For the SDK to function properly, **all of the text file in the ASR Bundle (e.g. app/src/main/assets/keenB2mQT-nnet3chain-en-us) need to be checked out with LF line endings**.
