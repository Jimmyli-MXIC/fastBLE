package com.am9.commlib;


import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

public class PeripheralHelper {
    public Gpio getLedGpio() {
        return ledGpio;
    }

    public void setLedGpio(Gpio ledGpio) {
        this.ledGpio = ledGpio;
    }

    private Gpio ledGpio;

    public PeripheralHelper() {
        PeripheralManager pioService = PeripheralManager.getInstance();
        try {
            ledGpio = pioService.openGpio(BoardDefaults.getGPIOForLED());
            ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            System.out.println("Error configuring GPIO pins" + e);
        }
    }

    public void setLeDValue(boolean value){
        try{
            ledGpio.setValue(value);
        } catch (IOException e){
            System.out.println("Error updating GPIO value" + e);
        }
    }


}
