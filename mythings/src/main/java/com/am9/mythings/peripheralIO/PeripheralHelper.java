package com.am9.mythings.peripheralIO;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.am9.mythings.peripheralIO.Music;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class PeripheralHelper {
    private static final String TAG = PeripheralHelper.class.getSimpleName();
    private static final Queue<Music.Note> SONG = new ArrayDeque<>();

    private Gpio ledGpio;
    private Pwm bus;
    private Handler buzzerSongHandler;


    public Gpio getLedGpio() {
        return ledGpio;
    }

//    public void setLedGpio(Gpio ledGpio) {
//        this.ledGpio = ledGpio;
//    }

    public PeripheralHelper() {
        PeripheralManager service = PeripheralManager.getInstance();
        //ledGpio
        try {
            ledGpio = service.openGpio(BoardDefaults.getGPIOForLED());
            ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
        //speakerPwn
        try {
            bus = service.openPwm(BoardDefaults.getPWMForSpeaker());
        } catch (IOException e) {
            throw new IllegalStateException("PWM1 bus cannot be opened.", e);
        }

        try {
            bus.setPwmDutyCycle(50);
        } catch (IOException e) {
            throw new IllegalStateException("PWM1 bus cannot be configured.", e);
        }

        HandlerThread handlerThread = new HandlerThread("BackgroundThread");
        handlerThread.start();
        buzzerSongHandler = new Handler(handlerThread.getLooper());

    }

    public void setLeDValue(boolean value) {
        try {
            ledGpio.setValue(value);
        } catch (IOException e) {
            Log.e(TAG, "Error updating GPIO value", e);
        }
    }

    public void setSpeakerValue(boolean value) {
        if (value == true) {
            SONG.addAll(Music.POKEMON_ANIME_THEME);
            buzzerSongHandler.post(playSong);
        }
    }
    private final Runnable playSong = new Runnable() {
        @Override
        public void run() {
            if (SONG.isEmpty()) {
                return;
            }

            Music.Note note = SONG.poll();

            if (note.isRest()) {
                SystemClock.sleep(note.getPeriod());
            }
            else {
                try {
                    bus.setPwmFrequencyHz(note.getFrequency());
                    bus.setEnabled(true);
                    SystemClock.sleep(note.getPeriod());
                    bus.setEnabled(false);
                } catch (IOException e) {
                    throw new IllegalStateException("PWM1 bus cannot play note.", e);
                }
            }
            buzzerSongHandler.post(this);
        }
    };
}




