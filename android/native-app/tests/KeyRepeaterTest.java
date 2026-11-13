/* Deterministic hold-repeat regression; no sleeping or Android services required. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Verifies repeat timing, cancellation, backpressure and modifier retention without stuck keys. */
public final class KeyRepeaterTest {
    public static void main(String[] args){
        List<int[]> batches=new ArrayList<>();NativeKeyboard keyboard=new NativeKeyboard(events->{batches.add(events);return true;});KeyRepeater repeat=new KeyRepeater();
        keyboard.toggle(NativeKeyboard.SHIFT);NativeKeyboard.RepeatedKey key=keyboard.repeated(0xff51);repeat.start(key::press,1000);
        require(batches.size()==1&&!keyboard.selected(NativeKeyboard.SHIFT),"Immediate press/modifier consumption");
        repeat.tick(1399);require(batches.size()==1,"Repeat before initial delay");repeat.tick(1400);repeat.tick(1445);require(batches.size()==3,"Repeat cadence");
        for(int[] batch:batches)require(Arrays.equals(batch,new int[]{0xffe1,0xff51,-0xff51,-0xffe1}),"Held gesture lost modifiers or releases");
        repeat.tick(10000);require(batches.size()==4,"UI stall caused a catch-up burst");repeat.stop();repeat.tick(20000);require(batches.size()==4&&!repeat.active(),"Release leaked repeat");
        repeat.start(()->false,0);require(!repeat.active(),"Rejected initial press kept repeating");
        int[] accepted={0};repeat.start(()->++accepted[0]<2,0);repeat.tick(400);repeat.tick(800);require(!repeat.active()&&accepted[0]==2,"Busy output did not stop timer");
        NativeKeyboard rejected=new NativeKeyboard(events->false);rejected.toggle(NativeKeyboard.CTRL);require(!rejected.repeated(0xff52).press()&&rejected.selected(NativeKeyboard.CTRL),"Rejected first press consumed modifier");
        System.out.println("PASS: immediate arrow, initial delay/cadence, no catch-up bursts, cancellation, rejected input and modifier retention with balanced releases");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
