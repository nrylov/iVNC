/* Local, non-secret scroll sensitivity preference for the experimental native viewer. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Lets the owner tune standard wheel scrolling without rebuilding or changing pointer speed. */
final class ScrollSettings {
    private static final String PREFERENCES="viewer-input",KEY="scroll-speed";
    static int savedSpeed(Activity activity){
        return Math.max(1,Math.min(24,activity.getSharedPreferences(PREFERENCES,0).getInt(KEY,NativeTrackpad.DEFAULT_SCROLL_SPEED)));
    }
    static void show(Activity activity,IntConsumer changed){
            LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,16,24,8);
            TextView label=new TextView(activity);int initial=savedSpeed(activity);label.setText("Scroll speed: "+initial+"×");content.addView(label);
            SeekBar slider=new SeekBar(activity);slider.setMax(23);slider.setProgress(initial-1);content.addView(slider);
            TextView help=new TextView(activity);help.setText("Higher values scroll farther for the same two-finger motion. Pointer speed is unchanged.");content.addView(help);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){label.setText("Scroll speed: "+(progress+1)+"×");}
                public void onStartTrackingTouch(SeekBar bar){}
                public void onStopTrackingTouch(SeekBar bar){}
            });
            new AlertDialog.Builder(activity).setTitle("Scroll sensitivity").setView(content).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(dialog,which)->{
                    int value=slider.getProgress()+1;activity.getSharedPreferences(PREFERENCES,0).edit().putInt(KEY,value).apply();changed.accept(value);
                }).show();
    }
}
