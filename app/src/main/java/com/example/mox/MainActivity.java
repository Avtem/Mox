package com.example.mox;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
{
    private EditText edtxtBreakEx;
    private EditText edtxtBreakMov;   // dint?
    private EditText edtxtMovemCount;   // 10 10 7 7 5 5 10
    private EditText edtxtMovemDur;  // first ever "delay"
    private EditText edtxtStartFrom;
    private TextView mTxtDebug;
    private EditText edtxtSequence;
    private TextView txtDuration;
    private SwitchCompat mSwitchFastMode;

    private static final String TAG = "Avtem.MainActivity";
    private SoundPool soundPool;
    private float getReadyDur = 2f; // 2 seconds
    private int sndEndOfExercise, sndFinish, sndTick, sndFadeIn, sndEndOfEverything, sndSilence;
    private int sndEndOfEverythingDurMs = 0;
    private PowerManager.WakeLock wakeLock = null;

    private String substringRegex(String srcString, String regex, int matchNumber) {
        String [] resultStr = new String[4];

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(srcString);
        byte i=0;
        while(matcher.find() && i !=4) {
            resultStr[i] = matcher.group(1);
            i++;
        }
        
        return resultStr[matchNumber -1];
    }

    class TimerThread extends Thread {

        @Override
        public void run()
        {
            String editText = edtxtSequence.getText().toString();   // get last exercise number
            final String sequence[] = editText.split("\n");   // this will give us "#12 [23.0] [2.23] [10] [3]"

            int startFrom = Integer.parseInt(edtxtStartFrom.getText().toString());

            for(int i=0; i < sequence.length; i++) {
                if(sequence[i].contains("#" + startFrom)) {
                    startFrom = i;
                    break;
                }
            }
            Log.d(TAG, "run: " + startFrom);
            boolean fastModeOn = mSwitchFastMode.isChecked();
            
            for(int ex = startFrom; ex < sequence.length; ex++) 
            {
                // break before exercise
                final float breakExercDur = Float.parseFloat(substringRegex(sequence[ex], "\\[(.*?)\\]", 1));
                // break before movement
                final float breakExercMov = Float.parseFloat(substringRegex(sequence[ex], "\\[(.*?)\\]", 2));
                // how many times repeat this exercise?
                final int repCount = Integer.parseInt(substringRegex(sequence[ex], "\\[(.*?)\\]", 3));
                // how long to hold head / leg / etc.
                final int repDuration = Integer.parseInt(substringRegex(sequence[ex], "\\[(.*?)\\]", 4));

                playBreak(breakExercDur);  // wait, until I'm ready...
                if(!threadIsRunning)
                    return;

                final int finalEx = ex; // just for setting text in TextView
                btnStart.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Pattern pattern = Pattern.compile("#(\\d+)");
                        Matcher matcher = pattern.matcher(sequence[finalEx]);
                        matcher.find();
                        String currExNumber = matcher.group(1);
                        MainActivity.this.setTitle("Mox (#" + (currExNumber) + " exercise)");
                    }
                });

                if(fastModeOn)
                    doFastModeLoop(repCount, repDuration);
                else
                    doSlowModeLoop(repCount, repDuration, breakExercMov);

                if(!threadIsRunning)
                    return; 
                
                MainActivity.this.runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mTxtDebug.setText("relax before next exercise");
                    }
                });
            }
            
            
            // end of everything!!!
            playSnd(sndEndOfEverything, sndEndOfEverythingDurMs +300);

            MainActivity.this.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    stopExercising();
                }
            });
        }
        
        private void doSlowModeLoop(final int repCount, final int repDuration, final float breakExercMov)
        {
            for (int rep = 0; rep < repCount; rep++) {  // working with head/leg/etc...
                playSnd(sndFadeIn, (int) (getReadyDur *1000));   // "ready?"
                if(!threadIsRunning)
                    return;

                for(int i = 0; i < repDuration; i++)   // tick
                {
                    final int finalI = i;
                    MainActivity.this.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mTxtDebug.setText(String.valueOf(finalI +1));
                        }
                    });

                    playSnd(sndTick, 1000);
                    if(!threadIsRunning)
                        return;
                }
                if(rep != repCount -1)  // is it last movement?
                {
                    MainActivity.this.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mTxtDebug.setText("relax for " + breakExercMov + " secs");
                        }
                    });
                    playSnd(sndFinish, 1000); // no, not last
                    if(!threadIsRunning)
                        return;
                    playBreak(breakExercMov);
                }
                else {
                    playSnd(sndEndOfExercise, 2000);
                    if(!threadIsRunning)
                        return;
                }
            }
        }

        private void doFastModeLoop(final int repCount, final int repDuration)
        {
            playSnd(sndFadeIn, (int) (getReadyDur *1000));   // "get ready"
            for (int rep = 0; rep < repCount; rep++) {       // 10 repetitions

                for(int i = 0; i < repDuration *2; i++)   // tick 1 2 3 + finish 1 2 3 
                {
                    if(!threadIsRunning)
                        return;            
                    if(rep == repCount -1 && i == repDuration)  // is it last movement?
                        break;   
                    
                    int snd = sndTick;
                    if( (i /repDuration) %2 == 1) // 3 times tick, 3 times finish
                        snd = sndFinish;
                    
                    final int finalI = i;
                    MainActivity.this.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            String text = String.valueOf(finalI +1);
                            if(finalI >= repDuration)
                                text = "break " + (finalI -repDuration +1);
                            mTxtDebug.setText(text);
                        }
                    });

                    playSnd(snd, 1000);
                    if(!threadIsRunning)
                        return;
                }
                
                if(rep == repCount -1)  // is it last movement?
                    playSnd(sndEndOfExercise, 2000);
            }
        }

        @Override
        public void interrupt()
        {
            threadIsRunning = false;
            soundPool.autoPause();
            super.interrupt();
        }

        private void playSnd(int soundpoolSoundId, int sndDurationMs)
        {
            if(threadIsRunning == false)
                return;

            if(soundPool == null)
                Log.e(TAG, "playSnd: soundPool is null!");

            soundPool.play(soundpoolSoundId,1,1, 0,
                    0, 1);

            try {
                Thread.sleep(sndDurationMs);
            } catch (InterruptedException e) {
                Log.i(TAG, "playSnd: thread was interrupted");
            }
        }

        private void playBreak(float secs) {  // wait x seconds
            // allow user to change the volume
            soundPool.play(sndSilence, 0,0,0,0,1);

            try {
                Thread.sleep((long) (secs *1000f));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            soundPool.autoPause();
        }
    }

    private short getLastExerciseNumber() {
        short lastExNumber = 1;

        String editText = edtxtSequence.getText().toString();   // get last exercise number
        String []lines = editText.split("\n");
        if(lines.length == 0) {
            return lastExNumber;
        }

        Pattern pattern = Pattern.compile("#(\\d+)");
        Matcher matcher = pattern.matcher(lines[lines.length -1]);
        if(matcher.find())
            lastExNumber = (short) (Short.parseShort(matcher.group(1)) +1);

        return lastExNumber;
    }

    
    
    public void addExercise(View v) {
        if(edtxtBreakEx.getText().length() == 0
        || edtxtBreakMov.getText().length() == 0
        || edtxtMovemCount.getText().length() == 0
        || edtxtMovemDur.getText().length() == 0)
        {
            Toast.makeText(this, "Please fill all fields!", Toast.LENGTH_SHORT).show();
            return;
        }

        String newExersiceStr = "";
        if(edtxtSequence.getText().toString().length() != 0)
            newExersiceStr = "\n";

        newExersiceStr += '#' + (getLastExerciseNumber() +"") + '\t'
                + '[' + edtxtBreakEx.getText().toString() + "] "
                + '[' + edtxtBreakMov.getText().toString() + "] "
                + '[' + edtxtMovemCount.getText().toString() + "] "
                + '[' + edtxtMovemDur.getText().toString() + "]";

        edtxtSequence.getText().append(newExersiceStr);
    }

    private Button btnStart;
    TimerThread thread = null;
    boolean threadIsRunning = false;

    public void stopExercising()
    {
        threadIsRunning = false;        // STOP EXERCISING!
        thread.interrupt();

        if(wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        if(soundPool != null) {
            soundPool.autoPause();
        }

        btnStart.setText("Start exercising");
        btnStart.setBackgroundColor(0xff388E3C);
        MainActivity.this.setTitle("Mox");
        mTxtDebug.setText("---");
    }

    public void doThreading(View v) {
        if(!threadIsRunning) {
            thread = new TimerThread();     // START EXERCISING!
            btnStart.setText("Stop");
            btnStart.setBackgroundColor(0xffff0000);
            threadIsRunning = true;
            thread.start();

            // don't go to sleep after we started
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "Mox::MyWakelockTag");
            wakeLock.acquire();
        }
        else {
            stopExercising();
        }
    }


    @Override
    protected void onStop()
    {
        saveSettings();

        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        threadIsRunning = false;    // save all data and stop sounds
        if(soundPool != null) {
            soundPool.autoPause();
            soundPool.release();
            soundPool = null;
        }

        if(wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        super.onDestroy();
    }

    private void saveSettings() {
        SharedPreferences userSett = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = userSett.edit();

        if(edtxtBreakEx.getText().length() != 0) {
            editor.putFloat(getString(R.string.settingsKeyPauseBeforeExercise),
                    Float.parseFloat(edtxtBreakEx.getText().toString()));
        }
        if(edtxtBreakMov.getText().length() != 0) {
            editor.putFloat(getString(R.string.settingsKeyPauseBeforeMovements),
                    Float.parseFloat(edtxtBreakMov.getText().toString()));
        }
        if(edtxtMovemCount.getText().length() != 0) {
            editor.putInt(getString(R.string.settingsKeyMovementCount),
                    Integer.parseInt(edtxtMovemCount.getText().toString()));
        }
        if(edtxtMovemDur.getText().length() != 0) {
            editor.putInt(getString(R.string.settingsKeyMovementDur),
                    Integer.parseInt(edtxtMovemDur.getText().toString()));
        }
        if(edtxtSequence.getText().length() !=0) {
            editor.putString(getString(R.string.settingsKeySequence),
                    edtxtSequence.getText().toString());
        }

        editor.commit();
    }

    private void loadSettings() {
        SharedPreferences userSett = MainActivity.this.getPreferences(Context.MODE_PRIVATE);

        float pauseBefBreak = userSett.getFloat(getString(R.string.settingsKeyPauseBeforeExercise),
                12.0f);
        edtxtBreakEx.setText(pauseBefBreak +"");
        float pauseBefMov = userSett.getFloat(getString(R.string.settingsKeyPauseBeforeMovements),
                2.0f);
        edtxtBreakMov.setText(pauseBefMov +"");
        int movCount = userSett.getInt(getString(R.string.settingsKeyMovementCount),
                10);
        edtxtMovemCount.setText(movCount +"");
        int movDur = userSett.getInt(getString(R.string.settingsKeyMovementDur),
                3);
        edtxtMovemDur.setText(movDur +"");

        String seq = userSett.getString(getString(R.string.settingsKeySequence),
                "#1 [8] [2] [10] [3]");
        edtxtSequence.setText(seq);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadSettings();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(audioAttributes)
                    .build();
        }
        else
            soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);


        // init sounds
        sndEndOfExercise = soundPool.load(this, R.raw.end_of_exercise, 1);
        sndTick = soundPool.load(this, R.raw.tick, 1);
        sndFinish = soundPool.load(this, R.raw.finish, 1);
        sndEndOfEverything = soundPool.load(this, R.raw.end_of_everything, 1);
        sndFadeIn = soundPool.load(this, R.raw.ready, 1);
        sndSilence = soundPool.load(this, R.raw.silence, 1);

        sndEndOfEverythingDurMs = getSndDurationInMS(R.raw.end_of_everything);
    }

    private boolean canParseToNumber(String strLine) {
        Pattern pattern =
                Pattern.compile(".*?\\[\\d+(\\.\\d+)?\\] \\[\\d+(\\.\\d+)?\\] \\[\\d+\\] \\[\\d+\\]$");
        Matcher matcher = pattern.matcher(strLine);

        return matcher.find();
    }

    public void disableBtnStart() {
        if(threadIsRunning)
            return;

        btnStart.setEnabled(false);
        btnStart.setBackgroundColor(0xFFAAAAAA);
    }
    public void enableBtnStart() {
        btnStart.setEnabled(true);
        btnStart.setBackgroundColor(0xFF388E3C);
    }

     
    
    private void initViews()
    {
        View.OnFocusChangeListener defFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus)
            {
                EditText editText = (EditText) v;
                editText.setSelection(editText.getText().length());
            }
        };
        TextWatcher textWatcher = new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                refreshTimeEstimate(mSwitchFastMode);
            }

            @Override
            public void afterTextChanged(Editable s)
            {}
        };

        mSwitchFastMode = findViewById(R.id.switchFastMode);
        
        edtxtBreakEx = findViewById(R.id.edtxtBreakEx);
         edtxtBreakEx.setOnFocusChangeListener(defFocusListener);
        edtxtBreakMov = findViewById(R.id.edtxtBreakMov);
         edtxtBreakMov.setOnFocusChangeListener(defFocusListener);
        edtxtMovemCount = findViewById(R.id.edtxtMovemCount);
         edtxtMovemCount.setOnFocusChangeListener(defFocusListener);
        edtxtMovemDur = findViewById(R.id.edtxtedtxtMovemDur);
         edtxtMovemDur.setOnFocusChangeListener(defFocusListener);
        edtxtStartFrom = findViewById(R.id.edtxtStartFrom);
         edtxtStartFrom.setOnFocusChangeListener(defFocusListener);
         edtxtStartFrom.addTextChangedListener(textWatcher);

        mTxtDebug = findViewById(R.id.txtDebug);
        btnStart = findViewById(R.id.btnStart);
        edtxtSequence = findViewById(R.id.edtxtSequence);
        edtxtSequence.addTextChangedListener(textWatcher);
        txtDuration = findViewById(R.id.txtDuration);
    }

    public void refreshTimeEstimate(View v)
    {
        String newStr = "Total duration: ";
        float secsTotal = 0;

        String editText = edtxtSequence.getText().toString();   // get last exercise number
        String []lines = editText.split("\n");
        if(editText.length() == 0) {
            txtDuration.setText(newStr + "0s");
            disableBtnStart();
            return;
        }

        int textStartFromSize = edtxtStartFrom.getText().toString().length();
        for(String line: lines)
        {
            if (!canParseToNumber(line) || textStartFromSize == 0) {
                txtDuration.setText("Total duration: error in sequence/start from");
                disableBtnStart();
                return;
            }
        }

        enableBtnStart();

        // count total duration
        int exCount = lines.length;
        float pauseEx, pauseRep, repCount, repDur;

        for(int i=0; i < exCount; i++) {
            pauseEx = Float.parseFloat(substringRegex(lines[i], "\\[(.*?)\\]", 1));
            pauseRep = Float.parseFloat(substringRegex(lines[i], "\\[(.*?)\\]", 2));
            repCount = Float.parseFloat(substringRegex(lines[i], "\\[(.*?)\\]", 3));
            repDur = Float.parseFloat(substringRegex(lines[i], "\\[(.*?)\\]", 4));

            if(!mSwitchFastMode.isChecked()) {  // slow mode
                secsTotal += pauseEx + 1 /*finishSnd*/;
                secsTotal += repCount * (getReadyDur + (repDur + 1) + pauseRep);
            }
            else {
                secsTotal += pauseEx + getReadyDur + 2; // pause before exercise, getready, endOfExercise snd
                secsTotal += repCount * (repDur *2);
                secsTotal -= repDur; // we don't make the very last 3 break ticks
            }
        }
        
        int secsTotalInt = (int) secsTotal;
        newStr += secsTotalInt/60 + "m " + secsTotalInt%60 +"s";
        txtDuration.setText(newStr);
    }

    private int getSndDurationInMS(int sndRawId) {
        MediaPlayer mp = MediaPlayer.create(this, sndRawId);
        int dur = mp.getDuration();
        mp.release();
        return dur;
    }


}
