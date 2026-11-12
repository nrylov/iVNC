/* Synthetic saved-login and lifecycle regression; owner profile/key/clipboard remain untouched. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Drives the real Activity through connect, pause/resume, recreation, Disconnect and Forget. */
public final class SavedSessionInstrumentation extends Instrumentation {
    private NativeActivity activity;
    private final ProfileStore[] store=new ProfileStore[1];
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try(SyntheticRfbServer server=new SyntheticRfbServer(true)){
            server.enhanced=server.encrypted=server.reconnect=server.zeroGeometry=true;
            store[0]=new ProfileStore(getTargetContext(),"native-profile-test","ivnc.native.profile.v1-test");store[0].forget();getTargetContext().getSharedPreferences("native-session-test",0).edit().clear().commit();
            launch();onMain(()->{
                ((EditText)field("host")).setText("127.0.0.1");((EditText)field("port")).setText(String.valueOf(server.listener.getLocalPort()));
                ((EditText)field("user")).setText("synthetic-user");((EditText)field("password")).setText("synthetic-password");
                ((AlertDialog)field("connectionPanel")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            waitLive(server,1);
            waitUntil(()->{JSONObject saved=new JSONObject(store[0].load());return !saved.isNull("profile");},"Remembered login was not encrypted/persisted");
            onMain(()->{
                require(((EditText)field("password")).getText().length()==0,"Password remained in the EditText");action("zoom-in");
                callActivityOnPause(activity);require(!(boolean)field("connected"),"Pause left viewer connected");callActivityOnResume(activity);
            });
            waitLive(server,2);Thread.sleep(200);
            onMain(()->{require((boolean)field("connected"),"Stale ended callback cancelled resumed session");require(((DesktopViewport)field("viewport")).zoom()>1,"Return lost the zoomed viewport");});
            onMain(()->activity.finish());waitForIdleSync();launch();waitLive(server,3);
            onMain(()->{require(field("connectionPanel")==null,"Saved auto-resume asked for login again");require(((EditText)field("password")).getText().length()==0,"Saved secret was put into a text control");action("disconnect");callActivityOnPause(activity);callActivityOnResume(activity);});
            Thread.sleep(400);require(server.logins.get()==3,"Manual Disconnect unexpectedly reconnected");
            onMain(()->activity.finish());waitForIdleSync();launch();waitForIdleSync();
            onMain(()->{
                AlertDialog panel=(AlertDialog)field("connectionPanel");require(panel!=null,"Manual disconnect intent was not persisted");
                require(((EditText)field("host")).getText().toString().equals("127.0.0.1"),"Saved host was not restored");
                panel.findViewById(android.R.id.content).findViewWithTag("native-forget-connection").performClick();
            });
            waitUntil(this::forgotten,"Forget retained encrypted credentials");
            onMain(()->{Method connect=NativeActivity.class.getDeclaredMethod("connect");connect.setAccessible(true);connect.invoke(activity);});
            Thread.sleep(300);require(server.logins.get()==3,"Forgotten password was automatically reused");
            try(SyntheticRfbServer rejected=new SyntheticRfbServer(true,true)){
                rejected.enhanced=rejected.encrypted=rejected.reconnect=true;
                onMain(()->{((SavedConnection)field("saved")).autoResume(true);((android.widget.CheckBox)field("remember")).setChecked(true);((EditText)field("port")).setText(String.valueOf(rejected.listener.getLocalPort()));
                    ((EditText)field("password")).setText("synthetic-password");Method connect=NativeActivity.class.getDeclaredMethod("connect");connect.setAccessible(true);connect.invoke(activity);});
                waitUntil(()->{final boolean[] failed={false};onMain(()->failed[0]=(boolean)field("errorShown"));return rejected.logins.get()==1&&failed[0];},"Rejected login was not reported");
                onMain(()->{require(!((SavedConnection)field("saved")).autoResume(),"Rejected login retained cold-resume intent");callActivityOnPause(activity);callActivityOnResume(activity);});
                Thread.sleep(400);require(rejected.accepted.get()==1,"Rejected credentials were retried on return");
            }
            onMain(()->activity.finish());waitForIdleSync();
            getTargetContext().getSharedPreferences("native-profile-test",0).edit().putString("encrypted","invalid-test-ciphertext").commit();launch();
            onMain(()->{
                AlertDialog panel=(AlertDialog)field("connectionPanel");android.view.View forget=panel.findViewById(android.R.id.content).findViewWithTag("native-forget-connection");
                require(forget!=null,"Unreadable saved login cannot be forgotten");forget.performClick();
            });
            waitUntil(this::forgotten,"Corrupt saved connection was not removed");
            require(server.failure==null,"Synthetic server failed: "+server.failure);
            result.putString("stream","PASS: encrypted remembered login, password absent from text controls, pause/resume with preserved zoom and no stale-ended race, saved auto-launch, manual Disconnect, and Forget. Synthetic profile/clipboard only.");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","SAVED SESSION FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{
            if(activity!=null){try{onMain(()->{NativeConnection connection=(NativeConnection)field("connection");connection.disconnect();((SavedConnection)field("saved")).forget(ok->{});activity.finish();});}catch(Exception ignored){}}
        }
    }
    private boolean forgotten()throws Exception{JSONObject json=new JSONObject(store[0].load());return json.has("profile")&&json.isNull("profile");}
    private void launch(){activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).putExtra("ivnc.synthetic",true).putExtra("ivnc.synthetic.autostart",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void waitLive(SyntheticRfbServer server,int sessions)throws Exception{
        waitUntil(()->{
            if(server.failure!=null)throw new IllegalStateException(server.failure);
            if(server.logins.get()<sessions)return false;
            final boolean[] live={false};onMain(()->live[0]=(boolean)field("connected")&&(boolean)field("screenReady"));return live[0];
        },"Session did not become live after transition "+sessions);
    }
    private void action(String name)throws Exception{Method action=NativeActivity.class.getDeclaredMethod("viewerAction",String.class);action.setAccessible(true);action.invoke(activity,name);}
    private Object field(String name)throws Exception{Field field=NativeActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private interface Work{void run()throws Exception;}
    private interface Check{boolean test()throws Exception;}
    private void onMain(Work work)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new IllegalStateException(error.get());}
    private static void waitUntil(Check check,String message)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<end){if(check.test())return;Thread.sleep(50);}throw new IllegalStateException(message);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
