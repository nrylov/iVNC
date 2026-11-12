/* Native keyboard keysyms and one-shot modifiers, adapted from owned app key-model.js. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds atomic press/release batches so closing the keyboard never leaves a modifier held. */
final class NativeKeyboard {
    interface Output { boolean send(int[] events); }
    static final int CTRL=0xffe3,ALT=0xffe9,CMD=0xffeb,SHIFT=0xffe1;
    private final Set<Integer> modifiers=new LinkedHashSet<>();
    private final Output output;
    NativeKeyboard(Output output){this.output=output;}
    void toggle(int key){if(!modifiers.remove(key))modifiers.add(key);}
    boolean selected(int key){return modifiers.contains(key);}
    void clear(){modifiers.clear();}
    boolean press(int symbol){return send(new int[]{symbol});}
    boolean text(CharSequence text){
        if(text.length()==0)return true;
        int[] symbols=text.toString().replace("\r\n","\n").codePoints().map(value->{
            if(value=='\n'||value=='\r')return 0xff0d;if(value=='\t')return 0xff09;
            if(value<0x20||value==0x7f)return 0;
            if(value>=0xd800&&value<=0xdfff)value=0xfffd;
            return value<=255?value:0x01000000|value;
        }).filter(value->value!=0).toArray();
        return send(symbols);
    }
    private boolean send(int[] symbols){
        if(symbols.length>240)return false;
        ArrayList<Integer> events=new ArrayList<>();for(int modifier:modifiers)events.add(modifier);
        for(int symbol:symbols){events.add(symbol);events.add(-symbol);}
        ArrayList<Integer> reverse=new ArrayList<>(modifiers);for(int i=reverse.size()-1;i>=0;i--)events.add(-reverse.get(i));
        if(events.isEmpty())return true;
        if(!output.send(events.stream().mapToInt(Integer::intValue).toArray()))return false;
        clear();return true;
    }
}
