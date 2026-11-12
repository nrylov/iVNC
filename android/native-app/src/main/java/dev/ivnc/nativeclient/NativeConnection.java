/* Owns native socket lifetime across foreground/surface transitions, independent of viewer widgets. */
package dev.ivnc.nativeclient;

import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;

/** Stops background streams and creates a fresh session on return, discarding stale callbacks. */
final class NativeConnection implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object lifetime=new Object();
    private final NativeRfb.Events events;
    private volatile long epoch;
    private volatile boolean foreground;
    private Surface surface;
    private long handle;
    private boolean started,wanted;
    private ConnectionProfile profile;
    NativeConnection(NativeRfb.Events events){this.events=events;}
    static boolean permanentFailure(String value){
        return value.contains("authentication")||value.contains("denied")||value.contains("does not permit")||value.contains("does not offer enhanced");
    }
    ConnectionProfile profile(){return profile;}
    void connect(ConnectionProfile profile){suspend();this.profile=profile;wanted=true;start();}
    void foreground(boolean visible){foreground=visible;if(visible)start();else suspend();}
    void surface(Surface surface){if(this.surface!=surface){suspend();Surface old=this.surface;this.surface=surface;if(old!=null)worker.execute(old::release);}start();}
    void disconnect(){wanted=false;profile=null;suspend();}
    void input(LongConsumer action){synchronized(lifetime){if(handle!=0)action.accept(handle);}}
    boolean keys(int[] keys){synchronized(lifetime){return handle!=0&&NativeRfb.keys(handle,keys);}}
    boolean clipboard(int action,byte[] bytes){synchronized(lifetime){return handle!=0&&NativeRfb.clipboard(handle,action,bytes);}}
    private void suspend(){epoch++;started=false;synchronized(lifetime){if(handle!=0)NativeRfb.stop(handle);}}
    private void start(){
        if(!wanted||started||!foreground||surface==null||!surface.isValid()||profile==null)return;
        started=true;final long token=++epoch;final Surface target=surface;final ConnectionProfile selected=profile;
        worker.execute(()->{
            final long session;
            synchronized(lifetime){
                if(token!=epoch||!foreground)return;
                session=NativeRfb.create(new NativeRfb.Events(){
                    public void event(String kind,String value){main.post(()->{
                        if(token!=epoch||!foreground)return;
                        if(kind.equals("error")&&permanentFailure(value))wanted=false;
                        if(kind.equals("status")&&value.equals("Native connection ended"))started=false;
                        events.event(kind,value);
                    });}
                    public void clipboard(byte[] bytes){main.post(()->{if(token==epoch&&foreground)events.clipboard(bytes);});}
                });
                handle=session;NativeRfb.pipeline(session,selected.pipeline);NativeRfb.enhanced(session,selected.enhanced);
            }
            try{NativeRfb.run(session,selected.host,selected.port,selected.username,selected.password,target);}
            finally{synchronized(lifetime){if(handle==session)handle=0;NativeRfb.destroy(session);}}
        });
    }
    @Override public void close(){disconnect();surface(null);worker.shutdown();}
}
