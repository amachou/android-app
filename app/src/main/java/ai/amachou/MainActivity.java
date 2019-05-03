package ai.amachou;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


public class MainActivity extends AppCompatActivity implements SpeechDelegate {

    private final int PERMISSIONS_REQUEST = 1;

    private ImageButton microphone;
    private SpeechProgressView progress;
    private LinearLayout linearLayout;

    private boolean firstTime;
    private Speech assistant;
    private JSONObject decisionTree;
    private JSONObject initSymptoms;
    private JSONObject initSymptomNode = null;

    private String left, right;

    private static final String DECISION_TREE_PATH = "tree.json";
    private static final String BOOT_SYNONYMS_PATH = "boot_symptoms.json";

    TextView text, textUser;

    boolean conversationStarted = false;

    String[] questions = new String[]{
            "Are you suffering from",
            "Please give us further informations. Do you have",
            "Do you have",
            "We are going to ask you some more questions. Are you noticing "
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text = findViewById(R.id.text);
        textUser = findViewById(R.id.textUser);

        this.decisionTree = Helper.loadJSONFile(getApplicationContext(), DECISION_TREE_PATH);
        this.initSymptoms = Helper.loadJSONFile(getApplicationContext(), BOOT_SYNONYMS_PATH);

        this.assistant = Speech.init(this, getPackageName());
        this.firstTime = true;

        linearLayout = findViewById(R.id.linearLayout);

        microphone = findViewById(R.id.button);
        microphone.setOnClickListener(view -> onMicroPhoneClicked());

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
    protected void onStart() {
        super.onStart();
        Handler h = new Handler();
        h.postDelayed(() -> {
            this.assistant.say("Hello dear. I am a virtuel assistant for medical prediction. What is wrong?");
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.assistant.shutdown();
    }

    @Override
    public void onStartOfSpeech() {}

    private void onMicroPhoneClicked() {
        if (this.assistant.isListening()) {
            this.assistant.stopListening();
        } else {
            int recordAudioPermission = ContextCompat.checkSelfPermission(
                    getApplicationContext(),
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
                if (this.firstTime) {
                    this.assistant.say("Hello dear. What is wrong ?");
                    this.firstTime = false;
                }
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

    public void toggleMicroPhone(boolean talking) {
        microphone.setVisibility(talking ? View.GONE : View.VISIBLE);
        linearLayout.setVisibility(talking ? View.VISIBLE : View.GONE);
    }

    private void onRecordAudioPermissionGranted() {
        this.toggleMicroPhone(true);
        try {
            this.assistant.startListening(progress, MainActivity.this);
            if (this.initSymptomNode != null) Log.i("TEST", this.initSymptomNode.toString());
        } catch (SpeechRecognitionNotAvailable exc) {
            showSpeechNotSupportedDialog();
        } catch (GoogleVoiceTypingDisabledException exc) {
            showEnableGoogleVoiceTyping();
        }
    }

    @Override
    public void onSpeechRmsChanged(float value) { }

    public String getInitSymptomFromSpeech(JSONObject symptoms, String speech) {
        try {
            return findSymptomFromUserSpeech(symptoms, speech);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject findSymptomInTree(String symptom, JSONObject tree) {
        Iterator<String> jsonKeys = tree.keys();
        while (jsonKeys.hasNext()) {
            String v = jsonKeys.next().toLowerCase().trim();
            if (v.contains(symptom.toLowerCase().trim())) {
                try {
                    return tree.getJSONObject(v);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    public void showPredictions(JSONObject pred) {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    Intent i = new Intent(getApplicationContext(), MapsActivity.class);
                    startActivity(i);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        try {
            builder.setMessage(
                    "There are " + (pred.getDouble("_p") * 100) + " chances that you are suffering from " + pred.getString("_n") +
                            ". Do you want me to show you the nearest hospital ?"
            ).setPositiveButton("Yes, I want", dialogClickListener)
                    .setNegativeButton("No, there is no need", dialogClickListener)
                    .setCancelable(true).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSpeechResult(String speech) {
        this.toggleMicroPhone(false);

        textUser.setText(speech);

        Log.i("SPEECH", speech);
        if (left != null && right != null) {
            Log.i("SPEECH", left);
            Log.i("SPEECH", right);
        }

        JSONObject symptomNode = null;

        if (!conversationStarted) {
            String symptom = this.getInitSymptomFromSpeech(this.initSymptoms, speech);
            if (symptom == null) {
                this.assistant.say(getResources().getString(R.string.repeat));
                text.setText(getResources().getString(R.string.repeat));
                return;
            }
            symptomNode = findSymptomInTree(symptom, this.decisionTree);
            if (symptomNode == null) {
                this.assistant.say(getResources().getString(R.string.repeat));
                text.setText(getResources().getString(R.string.repeat));
                return;
            }
        } else {
            if (speech.contains("yes") ||
                    speech.contains("maybe") ||
                    speech.contains("perhaps") ||
                    speech.contains("obviously")
            ) {
                symptomNode = findSymptomInTree(left, decisionTree);
            } else {
                Log.i("IN NO", right);
                symptomNode = findSymptomInTree(right, decisionTree);
            }
        }

        this.traverseTree(symptomNode);
        this.conversationStarted = true;

    }

    private void traverseTree(JSONObject symptomNode) {
        int questionIndice = new Random().nextInt(questions.length);
        String question = questions[questionIndice];
        try {
            JSONObject l = symptomNode.getJSONObject("left");
            JSONObject r = symptomNode.getJSONObject("right");
            double leftP = Double.parseDouble(l.getString("_p"));
            double rightP = Double.parseDouble(r.getString("_p"));
            String leftN = l.getString("_n");
            String rightN = r.getString("_n");
            if (leftP > rightP) {
                if (l.getBoolean("is_leaf")) {
                    String msg = "I am " + String.format("%.2f", leftP * 100) + " percent sure that you have " + leftN;
                    this.assistant.say(msg);
                    text.setText(msg);
                    showPredictions(l);
                    this.conversationStarted = false;
                } else {
                    this.assistant.say(question + " " + leftN);
                    text.setText(question + " " + leftN);
                    this.left = leftN;
                    this.right = rightN;
                }
            } else {
                if (r.getBoolean("is_leaf")) {
                    String msg = "I have found a probability of " + String.format("%.2f", rightP * 100) + "  that you have " + rightN;
                    this.assistant.say(msg);
                    text.setText(msg);
                    showPredictions(r);
                    this.conversationStarted = false;
                } else {
                    this.assistant.say(question + " " + rightN);
                    text.setText(question + " " + rightN);
                    this.right = rightN;
                    this.left = leftN;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Takes the user's speech and the map of symptoms and returns the corresponding symptom or null
     * if no symptom found for that speech.
     *
     * @param symptomsSynonyms the map of symptoms => synonyms
     * @param speech the user's speech
     * @return the found symptom | null
     */
    private String findSymptomFromUserSpeech(JSONObject symptomsSynonyms, String speech) throws JSONException {
        Iterator<String> iterator = symptomsSynonyms.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            JSONArray values = symptomsSynonyms.getJSONArray(key);
            for (int i = 0; i < values.length(); i++) {
                if (speech.contains(values.getString(i))) {
                    return key;
                }
            }
        }
        return null;
    }

    /**
     * Triggered whenever the user says something
     *
     * @param results the user's speech partials
     */
    @Override
    public void onSpeechPartialResults(List<String> results) {
        String msg = "";
        for (String result: results) {
            msg += result + " ";
        }
        textUser.setText(textUser.getText().toString() + msg);
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
                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {})
                .show();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) { }
}
