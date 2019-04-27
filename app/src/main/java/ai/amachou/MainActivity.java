package ai.amachou;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.SpeechUtil;
import net.gotev.speech.ui.SpeechProgressView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SpeechDelegate {

    private final int PERMISSIONS_REQUEST = 1;

    private ImageButton microphone;
    private TextView text;
    private SpeechProgressView progress;
    private LinearLayout linearLayout;

    private String userSpeech;
    private boolean firstTime;

    private Map<String, List<String>> symptomsSynonyms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Speech.init(this, getPackageName());

        this.firstTime = true;
        this.symptomsSynonyms = new HashMap<String, List<String>>() {{
            put("headache", Arrays.asList(
                    "headache",
                    "head hurting",
                    "head pain",
                    "migraine",
                    "cephalalgia",
                    "hemicrania",
                    "tourmoil"
            ));
            put("stomachache", Arrays.asList(
                    "stomachache",
                    "stomach pain",
                    "stomach burn",
                    "gastritis",
                    "dyspepsia",
                    "gastropathies")
            );
            put("nausea", Arrays.asList(
                    "nausea",
                    "distate",
                    "heave",
                    "retch",
                    "eructation",
                    "seasickness"
            ));
            put("rash", Arrays.asList(
                    "rash",
                    "irritation",
                    "skin inflammation",
                    "erythema",
                    "exanthema"
            ));
            put("fever", Arrays.asList(
                    "fever",
                    "burning up",
                    "heat",
                    "ague"
            ));
            put("fatigue", Arrays.asList(
                    "fatigue",
                    "exhaustion",
                    "weariness",
                    "wearing out",
                    "overwrought",
                    "knackered"
            ));
            put("diarrhea", Arrays.asList("diarrhea","stools","poop"));
        }};

        linearLayout = findViewById(R.id.linearLayout);

        microphone = findViewById(R.id.button);
        microphone.setOnClickListener(view -> onMicroPhoneClicked());

        text = findViewById(R.id.text);
        progress = findViewById(R.id.progress);

        int[] colors = {
                ContextCompat.getColor(this, android.R.color.black),
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.black),
                ContextCompat.getColor(this, android.R.color.holo_orange_dark),
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
        };
        progress.setColors(colors);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Speech.getInstance().shutdown();
    }

    private void onMicroPhoneClicked() {
        if (Speech.getInstance().isListening()) {
            Speech.getInstance().stopListening();
        } else {
            int recordAudioPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
            );
            if (recordAudioPermission == PackageManager.PERMISSION_GRANTED) {
                onRecordAudioPermissionGranted();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{ Manifest.permission.RECORD_AUDIO },
                        PERMISSIONS_REQUEST
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        if (requestCode != PERMISSIONS_REQUEST) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        } else {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onRecordAudioPermissionGranted();
            } else {
                Toast.makeText(
                        MainActivity.this,
                        R.string.permission_required,
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void onRecordAudioPermissionGranted() {
        microphone.setVisibility(View.GONE);
        linearLayout.setVisibility(View.VISIBLE);
        try {
            if (firstTime) {
                Speech.getInstance().say("Hello dear. What is wrong ?");
                firstTime = false;
            }
            Speech.getInstance().startListening(progress, MainActivity.this);
        } catch (SpeechRecognitionNotAvailable exc) {
            showSpeechNotSupportedDialog();
        } catch (GoogleVoiceTypingDisabledException exc) {
            showEnableGoogleVoiceTyping();
        }
    }

    @Override
    public void onStartOfSpeech() {
        Log.i("START", "SPEECH STARTED");
    }

    @Override
    public void onSpeechRmsChanged(float value) {
        //Log.d(getClass().getSimpleName(), "Speech recognition rms is now " + value +  "dB");
    }

    @Override
    public void onSpeechResult(String result) {
        microphone.setVisibility(View.VISIBLE);
        linearLayout.setVisibility(View.GONE);
        this.userSpeech = result;
        String answer = "Ohh Sorry I didn't hear very well, repeat please !";
        for (Map.Entry<String, List<String>> entry: this.symptomsSynonyms.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            boolean found = false;
            for (String v: values) {
                if (this.userSpeech.contains(v)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                answer = "Did you say " + key + " ?";
                break;
            }
        }
        Speech.getInstance().say(answer);
//        try {
//            JSONObject jsonObject = new JSONObject(loadJSONFromAsset(getApplicationContext()));
//            Log.i("   JSONObject", jsonObject.toString());
//            Iterator<String> keys = jsonObject.keys();
//            while (keys.hasNext()) {
//                Log.i("JSONEXT", keys.next());
//                Speech.getInstance().say(keys.next());
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        if (result.isEmpty()) {
//            Speech.getInstance().say(answer);
//        } else {
//            Speech.getInstance().say(result);
//        }
    }

    public String loadJSONFromAsset(Context context) {
        String json = null;
        try {
            InputStream is = context.getAssets().open("tree.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }

    @Override
    public void onSpeechPartialResults(List<String> results) {
        text.setText("");
        for (String partial: results) {
            text.append(partial + " ");
        }
    }

    private void showSpeechNotSupportedDialog() {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    SpeechUtil.redirectUserToGoogleAppOnPlayStore(MainActivity.this);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.speech_not_available)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, dialogClickListener)
                .setNegativeButton(R.string.no, dialogClickListener)
                .show();
    }

    private void showEnableGoogleVoiceTyping() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.enable_google_voice_typing)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                    // do nothing
                })
                .show();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) { }
}
