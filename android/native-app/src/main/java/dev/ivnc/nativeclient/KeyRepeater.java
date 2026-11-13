/* Clock-driven key repeat avoids stale callbacks and catch-up bursts after the UI thread pauses. */
package dev.ivnc.nativeclient;

import java.util.function.BooleanSupplier;

/** Emits immediately, waits like a physical keyboard, then repeats until release or rejection. */
final class KeyRepeater {
    static final long INITIAL_DELAY_MS=400,INTERVAL_MS=45;
    private BooleanSupplier action;private long due;
    boolean active(){return action!=null;}
    void start(BooleanSupplier action,long now){stop();if(action.getAsBoolean()){this.action=action;due=now+INITIAL_DELAY_MS;}}
    void tick(long now){if(action==null||now<due)return;if(!action.getAsBoolean()){stop();return;}due=now+INTERVAL_MS;}
    long delay(long now){return Math.max(1,due-now);}
    void stop(){action=null;}
}
