package it.lorenzopantano.texttospeech;

/*
* ESONERO MOBILE PROGRAMMING 2020
*
* Text To Speech
* (Demo app)
*
* Gruppo Daloma:
* Lorenzo Pantano 0240471
* Matteo D'Alessandro
* Davide Palleschi
* */

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.textservice.SpellCheckerService;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class TextToSpeechActivity extends AppCompatActivity implements TextToSpeech.OnInitListener, SpellCheckerSession.SpellCheckerSessionListener {

    private static TextToSpeech textToSpeech;
    private static final String TAG = "mTTS";
    private static final int CLANGACT = 101;
    private static final int SPLCHK = 102;
    private static final Locale DEFAULT_LANG = Locale.ITALIAN;
    private EditText etInputText;
    private TextView tvLang;
    private String editTextInput;
    private Locale locale;
    private Locale lastLang = DEFAULT_LANG;

    private SpellCheckerSession spellCheckerSession;

    //Lingue
    ArrayList<Locale> languagesList = new ArrayList<Locale>();
    Set<Locale> languagesSet = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tts_layout);
        etInputText = findViewById(R.id.etInputText);
        tvLang = findViewById(R.id.tvLang);

        new Holder();

        if (savedInstanceState != null) {
            etInputText.setText(savedInstanceState.get("editText").toString());
            locale = Locale.forLanguageTag(savedInstanceState.getString("lang"));
            tvLang.setText("Language: " +locale.getDisplayLanguage().toUpperCase());
        }

        //Inizializza il tts, primo this è il context, il secondo l'onInitListener
        textToSpeech = new TextToSpeech(getApplicationContext(), this);

    }


    /*Inizializza il tts:
     * status può essere:
     * TextToSpeech.SUCCESS oppure TextToSpeech.ERROR
     * https://developer.android.com/reference/android/speech/tts/TextToSpeech.OnInitListener
     */

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "onInit: INITIALIZATION SUCCESS");
            int result;
            if (locale == null) {
                //locale == null significa che viene usata la lingua di default
                result = textToSpeech.setLanguage(DEFAULT_LANG); //Default Language is Italian
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "onInit: LANGUAGE NOT AVAILABLE");
                } else tvLang.setText("Language: "+DEFAULT_LANG.getDisplayLanguage().substring(0,1).toUpperCase() + DEFAULT_LANG.getDisplayLanguage().substring(1));
            }
            else {
                //locale != null significa che + stata selezionata una lingua
                result = textToSpeech.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "onInit: LANGUAGE NOT AVAILABLE");
                }
                else tvLang.setText("Language: "+locale.getDisplayLanguage().substring(0,1).toUpperCase() + locale.getDisplayLanguage().substring(1));
            }

        } else {
            Log.e(TAG, "onInit: INITIALIZATION FAILED");
        }

        //Thread per scaricare l'elenco delle lingue disponibili
        ttsTh.start();

        //Vedi classe sotto UtteranceProgList()
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgList());
    }

    /*
    * Le lingue e le voci disponibili vengono salvate in due array
    * diversi, in un thread diverso da quello prinicipale
    *  */
    Thread ttsTh = new Thread(new Runnable() {
        @Override
        public void run() {
            //Tutte le lingue disponibili
            languagesSet = textToSpeech.getAvailableLanguages();
            languagesList.addAll(languagesSet);
            Log.d(TAG, "run: LANGUAGES OKAY");
            Log.d(TAG, "run: INTERRUPTING THREAD");
            ttsTh.interrupt();
            Log.d(TAG, "run: THREAD STOPPED");
        }
    });

    //Salva la stringa nella EditText
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("editText", etInputText.getText().toString());
        outState.putString("lang", textToSpeech.getVoice().getLocale().toLanguageTag());
    }

    @Override
    protected void onPause() {
        super.onPause();
        editTextInput = etInputText.getText().toString();
        if (spellCheckerSession != null) spellCheckerSession.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editTextInput != null) etInputText.setText(editTextInput);

        //Inizializza lo Spell Check Service

        Log.d(TAG, "onResume: SPELL CHECKER INIT WITH LANG: "+ (lastLang == null ? DEFAULT_LANG : lastLang).getDisplayLanguage());
        final TextServicesManager textServicesManager = (TextServicesManager) getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        spellCheckerSession = textServicesManager.newSpellCheckerSession(null, lastLang == null ? DEFAULT_LANG : lastLang, this, true);
    }

    /*
    * Alla chiusura (o rotazione dell'app ???) distruggere l'engine del tts
    * il metodo stop() ferma se stava parlando,
    * il metodo shutdown() distrugge l'oggetto TextToSpeechEngine rendendolo inutilizzabile.
    * */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (spellCheckerSession != null) spellCheckerSession.close();
    }

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {

    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        //TODO: Display Suggestions
        Log.d(TAG, "onGetSentenceSuggestions: " + results[0].getSuggestionsInfoAt(0));
    }

    /*
    * Serve a gestire quello che succede metre il tts parla
    * E' un thread apparte quindi non può interagire con la UI, per questo motivo creiamo un handler.
    * */
    class UtteranceProgList extends UtteranceProgressListener {

        @Override
        public void onStart(String utteranceId) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(TextToSpeechActivity.this, "Staring...", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }

        @Override
        public void onDone(String utteranceId) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(TextToSpeechActivity.this, "Done", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }

        @Override
        public void onError(String utteranceId) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(TextToSpeechActivity.this, "An error occurred, try again later and check you internet connection", Toast.LENGTH_LONG);
                    toast.show();
                }
            });
        }
    }


    /*
    * Holder per UI
    * */

    class Holder implements View.OnClickListener{

        private SeekBar seekBarSpeed, seekBarPitch;
        private Button btnPlay, btnStop, btnLanguage, btnSpellCheck;

        Holder () {

            //Altre view
            seekBarPitch = findViewById(R.id.seekBarPitch);
            seekBarSpeed = findViewById(R.id.seekBarSpeed);
            btnPlay = findViewById(R.id.btnPlay);
            btnStop = findViewById(R.id.btnStop);
            btnLanguage = findViewById(R.id.btnLanguage);
            btnSpellCheck = findViewById(R.id.btnSpellCheck);

            btnLanguage.setOnClickListener(this);
            btnPlay.setOnClickListener(this);
            btnStop.setOnClickListener(this);
            btnSpellCheck.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {

            switch (v.getId()) {

                //Play Button
                case R.id.btnPlay:
                    /*
                     * Speed e pitch "normali" sono pari a 1.0
                     * 2.0 significa il doppio e 0.5 la metà
                     * 0.0 non esiste, dobbiamo fare in modo che non venga selezionato.
                     * Dividiamo per 50 perché all'inizio il progress delle due seekBar
                     * è impostato a 50 per cui il valore iniziale è 1.0
                     * Se arriviamo al massimo cioè 100 abbiamo 2.0
                     * */

                    float speed = (float) seekBarSpeed.getProgress() / 50;
                    float pitch = (float) seekBarPitch.getProgress() / 50;
                    if (speed < 0.1) speed = 0.1f;
                    if (pitch < 0.1) pitch = 0.1f;

                    textToSpeech.setPitch(pitch);
                    textToSpeech.setSpeechRate(speed);

                    /*
                     * Il metodo speak prende 4 parametri:
                     * 1- Il testo da riprodurre
                     * 2- Con che modalità riprodurlo: QUEUE_FLUSH significa che nella coda delle 'cose da riprodurre' viene cancellato tutto
                     *    e viene riprodotto il testo corrente; QUEUE_ADD significa che il testo viene aggiunto alla coda
                     * 3- Parametri aggiuntivi che specificano ad esempio il volume, può essere null
                     * 4- utteranceId che è l'id univoco della richiesta, verrà passato all'UtteranceProgressListener
                     * */

                    /*
                     * Possiamo ad esempio impostare il volume al minimo creando un Bundle per i parametri,
                     * e metterci dentro la costante relativa al volume (ce ne sono diverse di costanti per il TTS)
                     * KEY_PARAM_VOLUME che va da 0.0 a 1.0 dove 0.0 è silenzio e 1.0 è il massimo (default)
                     *
                     * Bundle params = new Bundle();
                     * params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.1f);
                     * E poi passare params come terzo parametro al metodo speak()
                     * */

                    if (etInputText.getText().length() > textToSpeech.getMaxSpeechInputLength()) {
                        Toast.makeText(TextToSpeechActivity.this, "Text is too long!", Toast.LENGTH_SHORT).show();
                    }
                    textToSpeech.speak(etInputText.getText().toString(), TextToSpeech.QUEUE_FLUSH, null, "textToSpeechUtteranceID");
                    break;


                    //Stop button
                case R.id.btnStop:
                    if (textToSpeech.isSpeaking()) {
                        textToSpeech.stop();
                    }
                    break;

                    //Change language button
                case R.id.btnLanguage:
                    if (textToSpeech.isSpeaking()) textToSpeech.stop();
                    Intent langIntent = new Intent(TextToSpeechActivity.this, ChangeLanguageActivity.class);
                    langIntent.putExtra("avLanguages", languagesList);
                    startActivityForResult(langIntent, CLANGACT);
                    break;

                    //Perform Spell Check Button
                case R.id.btnSpellCheck:
                    TextInfo textInfo = new TextInfo(etInputText.getText().toString());
                    TextInfo[] textInfos = {textInfo};
                    spellCheckerSession.getSentenceSuggestions(textInfos, 3);
                    Log.d(TAG, "onClick: SPELL CHECK CLICKED");
            }


        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CLANGACT) {
            if (resultCode == RESULT_OK) {
                int result = data.getIntExtra("selectedLang", -1);
                if (result == -1) {
                    textToSpeech.setLanguage(lastLang);
                    tvLang.setText("Language: "+lastLang.getDisplayLanguage().substring(0,1).toUpperCase() + lastLang.getDisplayLanguage().substring(1));
                    Toast.makeText(this, "Language has not been changed", Toast.LENGTH_SHORT).show();
                } else {
                    Locale selectedLoc = languagesList.get(result);
                    textToSpeech.setLanguage(selectedLoc);
                    lastLang = selectedLoc;
                    tvLang.setText("Language: "+selectedLoc.getDisplayLanguage().substring(0,1).toUpperCase() + selectedLoc.getDisplayLanguage().substring(1));
                    Toast.makeText(this, "LANGUAGE: "+selectedLoc.getDisplayLanguage().toUpperCase(), Toast.LENGTH_SHORT).show();
                }
            }
            else if (resultCode == RESULT_CANCELED) {
                textToSpeech.setLanguage(lastLang);
                tvLang.setText("Language: "+lastLang.getDisplayLanguage().substring(0,1).toUpperCase() + lastLang.getDisplayLanguage().substring(1));
            }
        }

    }
}
