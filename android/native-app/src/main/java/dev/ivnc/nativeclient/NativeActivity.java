/* Independent Android viewer for testing LibVNCClient against the Mac without WebView. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.SurfaceTexture;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Experimental Standard-mode path; kept separate from the working HP client. */
public final class NativeActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object lifetime=new Object();
    private long handle;
    private TextureView texture;
    private CursorOverlay cursor;
    private android.widget.ToggleButton pipeline;
    private boolean connected,touchActive;
    private long lastLocalPointerAt;
    private static final long CURSOR_ECHO_GRACE_MS=150;
    private Surface surface;
    private TextView status;
    private DebugLog debugLog;
    private AlertDialog debugDialog;
    private int width=1920,height=1080;
    private float cursorX=960,cursorY=540,lastX,lastY,downX,downY;
    private long downAt;
    private boolean moved, errorShown;
    private EditText host,port,user,password;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        String version="unknown";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        debugLog=new DebugLog("iVNC Native "+version+" · "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+
            " · Android "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")");
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xff080d14);
        texture=new TextureView(this);texture.setSurfaceTextureListener(this);
        root.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        cursor=new CursorOverlay(this);cursor.setVisibility(View.INVISIBLE);root.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);bar.setBackgroundColor(0xd0101b2a);
        status=new TextView(this);status.setTag("native-status");status.setText("Native LibVNCClient experiment — Standard mode");bar.addView(status);
        Button connection=new Button(this);connection.setText("Connection");connection.setOnClickListener(v->connectionDialog());
        Button log=new Button(this);log.setText("Debug log");log.setTag("native-debug-log");log.setOnClickListener(v->showDebugLog());
        LinearLayout actions=new LinearLayout(this);actions.addView(connection,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(log,new LinearLayout.LayoutParams(0,-2,1));
        pipeline=new android.widget.ToggleButton(this);pipeline.setTextOn("Pipeline on");pipeline.setTextOff("Pipeline off");pipeline.setChecked(true);pipeline.setTextSize(12);
        pipeline.setTag("native-pipeline");pipeline.setContentDescription("Pipeline framebuffer update requests");
        pipeline.setOnCheckedChangeListener((button,enabled)->{
            synchronized(lifetime){if(handle!=0)NativeRfb.pipeline(handle,enabled);}
            debugLog.add("status","Update request pipeline "+(enabled?"enabled":"disabled")+" for subsequent updates");
        });
        actions.addView(pipeline,new LinearLayout.LayoutParams(0,-2,1));bar.addView(actions);
        root.addView(bar,new FrameLayout.LayoutParams(-1,-2));
        texture.setOnTouchListener(this::touch);
        setContentView(root);
        host=field("Mac address",InputType.TYPE_CLASS_TEXT);port=field("Port",InputType.TYPE_CLASS_NUMBER);port.setText("5900");
        user=field("Mac username",InputType.TYPE_CLASS_TEXT);password=field("Mac password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        password.setTag("native-password");
        password.setSaveEnabled(false);
        password.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        connectionDialog();
    }
    private void showDebugLog(){
        TextView text=new TextView(this);text.setTag("native-debug-text");text.setText(debugLog.snapshot());text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setTextSize(12);text.setPadding(24,16,24,16);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Native connection log").setView(scroll)
            .setPositiveButton("Copy",null).setNeutralButton("Clear",null).setNegativeButton("Close",null).create();
        debugDialog=dialog;dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("iVNC native debug log",text.getText()));
                android.widget.Toast.makeText(this,"Debug log copied",android.widget.Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{debugLog.clear();text.setText(debugLog.snapshot());});
    }
    private EditText field(String hint,int type){EditText input=new EditText(this);input.setHint(hint);input.setSingleLine();input.setInputType(type);return input;}
    private void connectionDialog(){
        LinearLayout fields=new LinearLayout(this);fields.setPadding(24,12,24,0);fields.setOrientation(LinearLayout.VERTICAL);
        for(EditText input:new EditText[]{host,port,user,password}){
            if(input.getParent()!=null)((android.view.ViewGroup)input.getParent()).removeView(input);fields.addView(input);
        }
        new AlertDialog.Builder(this).setTitle("iVNC Native experiment").setView(fields)
            .setMessage("Separate test app. Apple High Performance and clipboard extensions are not integrated yet.")
            .setPositiveButton("Connect",(dialog,which)->connect()).setNeutralButton("Disconnect",(dialog,which)->stop())
            .setNegativeButton("Cancel",null).show();
    }
    private void connect(){
        if(surface==null){status.setText("Display is not ready; try Connect again");return;}
        final int selectedPort;
        try{selectedPort=Integer.parseInt(port.getText().toString());if(selectedPort<1||selectedPort>65535)throw new NumberFormatException();}
        catch(NumberFormatException invalid){status.setText("Invalid port");return;}
        final String address=host.getText().toString().trim(),username=user.getText().toString(),secret=password.getText().toString();
        if(address.isEmpty()||username.isEmpty()||secret.isEmpty()){status.setText("Enter the Mac address and login");return;}
        password.setText("");stop();
        final Surface target=surface;
        final boolean earlyRequests=pipeline.isChecked();
        worker.execute(()->{
            final long session;
            synchronized(lifetime){session=NativeRfb.create((kind,value)->runOnUiThread(()->event(kind,value)));handle=session;NativeRfb.pipeline(session,earlyRequests);}
            try{NativeRfb.run(session,address,selectedPort,username,secret,target);}
            finally{synchronized(lifetime){if(handle==session)handle=0;NativeRfb.destroy(session);}}
        });
    }
    private void event(String kind,String value){
        if(kind.equals("cursor")){
            if(!touchActive&&android.os.SystemClock.uptimeMillis()-lastLocalPointerAt>=CURSOR_ECHO_GRACE_MS){
                String[] position=value.split(",");cursorX=Math.max(0,Math.min(width-1,Float.parseFloat(position[0])));
                cursorY=Math.max(0,Math.min(height-1,Float.parseFloat(position[1])));updateCursor();
            }
            return;
        }
        debugLog.add(kind,value);
        if(kind.equals("status")&&value.startsWith("Native standard VNC connected"))connected=true;
        if(kind.equals("error")||(kind.equals("status")&&value.equals("Native connection ended"))){connected=false;cursor.setVisibility(View.INVISIBLE);}
        if(kind.equals("metrics")||kind.equals("protocol"))return;
        if(kind.equals("error")){if(errorShown)return;errorShown=true;}
        else if(kind.equals("status")&&value.startsWith("Connecting"))errorShown=false;
        else if(kind.equals("status")&&errorShown)return;
        if(kind.equals("size")){
            String[] size=value.split(",");width=Integer.parseInt(size[0]);height=Integer.parseInt(size[1]);
            cursorX=width/2f;cursorY=height/2f;fit();
        }else if(kind.equals("frames")){if(connected)cursor.setVisibility(View.VISIBLE);status.setText("Native Standard · "+value+" Surface posts");}
        else status.setText(value);
    }
    private void fit(){
        if(width<1||height<1||texture.getWidth()<1||texture.getHeight()<1)return;
        float scale=ViewportMath.scale(texture.getWidth(),texture.getHeight(),width,height);
        android.graphics.Matrix transform=new android.graphics.Matrix();
        transform.setScale(width*scale/texture.getWidth(),height*scale/texture.getHeight(),texture.getWidth()/2f,texture.getHeight()/2f);
        texture.setTransform(transform);updateCursor();
    }
    private void updateCursor(){cursor.position(width,height,cursorX,cursorY);}
    private boolean touch(View view,MotionEvent event){
        if(event.getPointerCount()!=1)return true;
        float x=event.getX(),y=event.getY();
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=downX=x;lastY=downY=y;downAt=event.getEventTime();moved=false;touchActive=true;return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
            float scale=ViewportMath.scale(texture.getWidth(),texture.getHeight(),width,height);
            cursorX=Math.max(0,Math.min(width-1,cursorX+(x-lastX)/Math.max(.01f,scale)));
            cursorY=Math.max(0,Math.min(height-1,cursorY+(y-lastY)/Math.max(.01f,scale)));
            moved|=Math.hypot(x-downX,y-downY)>12;lastX=x;lastY=y;pointer(0);
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP){
            touchActive=false;if(!moved&&event.getEventTime()-downAt<350){pointer(1);pointer(0);}
        }else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)touchActive=false;
        return true;
    }
    private void pointer(int buttons){lastLocalPointerAt=android.os.SystemClock.uptimeMillis();updateCursor();synchronized(lifetime){if(handle!=0)NativeRfb.pointer(handle,(int)cursorX,(int)cursorY,buttons);}}
    private void stop(){connected=false;touchActive=false;if(cursor!=null)cursor.setVisibility(View.INVISIBLE);synchronized(lifetime){if(handle!=0)NativeRfb.stop(handle);}}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){surface=new Surface(value);fit();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value){stop();if(surface!=null)surface.release();surface=null;return true;}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){fit();}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture value){}
    @Override protected void onPause(){stop();super.onPause();}
    @Override protected void onDestroy(){stop();worker.shutdown();super.onDestroy();}
}
