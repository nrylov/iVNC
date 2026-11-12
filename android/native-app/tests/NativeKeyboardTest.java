/* Synthetic keyboard tests prevent stuck modifiers and incorrect Unicode/newline mapping. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests the real batch model without a phone, connection or private text. */
public final class NativeKeyboardTest {
    public static void main(String[] args){
        List<int[]> batches=new ArrayList<>();NativeKeyboard keys=new NativeKeyboard(events->{batches.add(events);return true;});
        keys.toggle(NativeKeyboard.CMD);require(keys.text("c"),"Shortcut rejected");
        equal(batches.remove(0),new int[]{0xffeb,99,-99,-0xffeb});require(!keys.selected(NativeKeyboard.CMD),"One-shot modifier remained selected");
        keys.text("Aπ😀\n\t");equal(batches.remove(0),new int[]{65,-65,0x010003c0,-0x010003c0,0x0101f600,-0x0101f600,0xff0d,-0xff0d,0xff09,-0xff09});
        keys.toggle(NativeKeyboard.CTRL);keys.toggle(NativeKeyboard.ALT);keys.press(0xffff);
        equal(batches.remove(0),new int[]{0xffe3,0xffe9,0xffff,-0xffff,-0xffe9,-0xffe3});
        require(!keys.text("x".repeat(241))&&batches.isEmpty(),"Oversized text partially transmitted");
        NativeKeyboard busy=new NativeKeyboard(events->false);busy.toggle(NativeKeyboard.SHIFT);require(!busy.press(65)&&busy.selected(NativeKeyboard.SHIFT),"Rejected batch lost selected modifier");busy.clear();require(!busy.selected(NativeKeyboard.SHIFT),"Close did not clear local state");
        System.out.println("PASS: atomic shortcuts, one-shot/reversed modifiers, Unicode, Enter/Tab, bounded text and rejected-batch state");
    }
    private static void equal(int[] actual,int[] expected){require(Arrays.equals(actual,expected),"Keyboard event batch differs");}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
