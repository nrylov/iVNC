/* Bounded, in-memory diagnostic history. Inputs must be protocol metadata, never login or payload data. */
package dev.ivnc.nativeclient;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Set;

/** Retains connection stages for explicit user copying without writing logs to disk. */
final class DebugLog {
    private static final int MAX_ENTRIES=256;
    private static final int MAX_MESSAGE=1024;
    private static final Set<String> KINDS=Set.of("status","error","size","frames","protocol","public-key");
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    private final String header;
    private final long start=System.nanoTime();
    DebugLog(String metadata){header=metadata+"\nLog contains protocol metadata only; no login details, addresses, keys or screen contents.\n";}
    synchronized void add(String kind,String message){
        if(!KINDS.contains(kind)||message==null)return;
        String bounded=message.length()>MAX_MESSAGE?message.substring(0,MAX_MESSAGE)+"…":message;
        entries.addLast(String.format(Locale.ROOT,"%8.3fs [%s] %s",(System.nanoTime()-start)/1e9,kind,bounded.trim()));
        while(entries.size()>MAX_ENTRIES)entries.removeFirst();
    }
    synchronized String snapshot(){return header+"\n"+String.join("\n",entries);}
    synchronized void clear(){entries.clear();}
}
