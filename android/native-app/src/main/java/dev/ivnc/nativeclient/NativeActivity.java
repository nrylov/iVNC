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

/** Experimental Standard-mode path; kept separate from the working HP client. */
public final class NativeActivity extends Activity implements TextureView.SurfaceTextureListener {
    private NativeConnection connection;
    private SavedConnection saved;
    private ClipboardSync clipboard;
    private boolean resumed,profileSaved,haveFramebuffer,screenReady;
    private android.widget.CheckBox remember;
    private boolean synthetic;
    private TextureView texture;
    private ViewportRenderer renderer;
    private KeyboardDock keyboardDock;
    private int keyboardReserved;
    private final DesktopViewport viewport=new DesktopViewport();
    private CursorOverlay cursor;
    private android.widget.ToggleButton pipeline;
    private boolean connected,touchActive,ignoreTouch;
    private long lastLocalPointerAt;
    private static final long CURSOR_ECHO_GRACE_MS=150;
    private Surface surface;
    private TextView status;
    private DebugLog debugLog;
    private AlertDialog debugDialog,connectionPanel;
    private int width=1920,height=1080;
    private float cursorX=960,cursorY=540;
    private NativeTrackpad trackpad;
    private boolean errorShown;
    private EditText host,port,user,password;
    private android.widget.CheckBox compatibility;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        synthetic=(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&getIntent().getBooleanExtra("ivnc.synthetic",false);
        saved=new SavedConnection(this,synthetic);
        connection=new NativeConnection(new NativeRfb.Events(){
            public void event(String kind,String value){NativeActivity.this.event(kind,value);}
            public void clipboard(byte[] bytes){clipboard.receive(bytes);}
        });
        clipboard=new ClipboardSync(synthetic?ClipboardSync.memory():ClipboardSync.android(this),connection::clipboard,message->android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_SHORT).show());
        getWindow().setDecorFitsSystemWindows(false);
        String version="unknown";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        debugLog=new DebugLog("iVNC Native "+version+" · "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+
            " · Android "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")");
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xff080d14);
        texture=new TextureView(this);texture.setSurfaceTextureListener(this);renderer=new ViewportRenderer(texture);
        root.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        cursor=new CursorOverlay(this);cursor.setVisibility(View.INVISIBLE);root.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
        cursor.viewport(viewport);
        status=new TextView(this);status.setTag("native-status");status.setText("Disconnected");status.setVisibility(View.GONE);status.setPadding(20,40,20,12);status.setBackgroundColor(0xcc101b2a);root.addView(status,new FrameLayout.LayoutParams(-1,-2));
        pipeline=new android.widget.ToggleButton(this);pipeline.setChecked(true);
        pipeline.setOnCheckedChangeListener((button,enabled)->{
            connection.input(handle->NativeRfb.pipeline(handle,enabled));
            debugLog.add("status","Update request pipeline "+(enabled?"enabled":"disabled")+" for subsequent updates");
        });
        ViewerControls.attach(this,root,()->(connected?"Connected":"Disconnected")+" · "+Math.round(viewport.zoom()*100)+"%",pipeline::isChecked,()->trackpad!=null&&trackpad.dragLocked(),()->trackpad!=null&&trackpad.direct(),()->keyboardReserved,this::viewerAction);
        trackpad=new NativeTrackpad(getResources().getDisplayMetrics().density,new NativeTrackpad.Output(){
            public void move(float dx,float dy){
                float scale=Math.max(.01f,viewport.scale());
                cursorX=Math.max(0,Math.min(width-1,cursorX+dx/scale));
                cursorY=Math.max(0,Math.min(height-1,cursorY+dy/scale));pointer(trackpad.buttons());
            }
            public void position(float x,float y){cursorX=viewport.remoteX(x);cursorY=viewport.remoteY(y);pointer(trackpad.buttons());}
            public void zoom(float factor,float x,float y,float dx,float dy){
                viewport.zoomBy(factor,x-dx,y-dy);viewport.panBy(dx,dy);applyViewport();
            }
            public void hold(boolean down){cursor.dragging(down);pointer(down?1:0);}
            public void click(int button){pointer(button);pointer(0);}
            public void wheel(int mask){connection.input(handle->NativeRfb.wheel(handle,(int)cursorX,(int)cursorY,mask));}
        });
        trackpad.setScrollSpeed(ScrollSettings.savedSpeed(this));
        keyboardDock=new KeyboardDock(this,root,new NativeKeyboard(events->{
            return connected&&connection.keys(events);
        }),bottom->{
            keyboardReserved=Math.min(bottom,Math.max(0,root.getHeight()-(int)(72*getResources().getDisplayMetrics().density)));ViewerControls.reserved(root,keyboardReserved);
            int margin=Math.min(bottom,Math.max(0,root.getHeight()-64));
            for(View viewer:new View[]{texture,cursor}){FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)viewer.getLayoutParams();if(layout.bottomMargin!=margin){layout.bottomMargin=margin;viewer.setLayoutParams(layout);}}
        });
        texture.setOnTouchListener(this::touch);
        setContentView(root);root.post(()->ViewerControls.immersive(this));
        host=field("Mac address",InputType.TYPE_CLASS_TEXT);port=field("Port",InputType.TYPE_CLASS_NUMBER);port.setText("5900");
        user=field("Mac username",InputType.TYPE_CLASS_TEXT);password=field("Mac password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        password.setTag("native-password");
        password.setSaveEnabled(false);
        password.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        compatibility=new android.widget.CheckBox(this);compatibility.setText("Compatibility mode");
        remember=new android.widget.CheckBox(this);remember.setText("Remember connection securely");remember.setChecked(true);
        ConnectionProfile stored=saved.profile();
        if(stored!=null){host.setText(stored.host);port.setText(String.valueOf(stored.port));user.setText(stored.username);compatibility.setChecked(!stored.enhanced);pipeline.setChecked(stored.pipeline);password.setHint("Saved password");}
        if((!synthetic||getIntent().getBooleanExtra("ivnc.synthetic.autostart",false))&&stored!=null&&saved.autoResume()){profileSaved=true;connection.connect(stored);}
        else connectionDialog();
        if(saved.error()!=null){status.setText(saved.error());status.setVisibility(View.VISIBLE);}
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
        if(compatibility.getParent()!=null)((android.view.ViewGroup)compatibility.getParent()).removeView(compatibility);
        fields.addView(compatibility);
        if(remember.getParent()!=null)((android.view.ViewGroup)remember.getParent()).removeView(remember);fields.addView(remember);
        TextView note=new TextView(this);note.setText(saved.error()!=null?saved.error():saved.profile()==null?"Login is encrypted on this device when remembered.":"Saved connection · password stored securely");fields.addView(note);
        if(saved.profile()!=null||saved.error()!=null){Button forget=new Button(this);forget.setText("Forget saved connection");forget.setTag("native-forget-connection");forget.setOnClickListener(view->{
            stop();saved.forget(ok->note.setText(ok?"Saved connection forgotten":"Could not fully remove saved connection"));
            password.setText("");password.setHint("Mac password");remember.setChecked(false);forget.setEnabled(false);
        });fields.addView(forget);}
        android.widget.ScrollView content=new android.widget.ScrollView(this);content.addView(fields);
        connectionPanel=new AlertDialog.Builder(this).setTitle("Connect to your Mac").setView(content)
            .setMessage("Standard mode · macOS Screen Sharing").setCancelable(true)
            .setPositiveButton("Connect",(dialog,which)->connect()).setNeutralButton("Disconnect",(dialog,which)->stop())
            .setNegativeButton("Cancel",null).create();connectionPanel.show();
    }
    private void connect(){
        status.setVisibility(View.VISIBLE);
        final int selectedPort;
        try{selectedPort=Integer.parseInt(port.getText().toString());if(selectedPort<1||selectedPort>65535)throw new NumberFormatException();}
        catch(NumberFormatException invalid){status.setText("Invalid port");return;}
        String address=host.getText().toString().trim(),username=user.getText().toString(),secret=password.getText().toString();
        ConnectionProfile stored=saved.profile();if(secret.isEmpty()&&stored!=null&&stored.matches(address,selectedPort,username))secret=stored.password;
        if(address.isEmpty()||username.isEmpty()||secret.isEmpty()){status.setText("Enter the Mac address and login");return;}
        password.setText("");trackpad.cancel();clipboard.ready(false);connected=screenReady=false;haveFramebuffer=false;profileSaved=false;
        if(!remember.isChecked())saved.forget(ok->{});
        connection.connect(new ConnectionProfile(address,selectedPort,username,secret,!compatibility.isChecked(),pipeline.isChecked(),remember.isChecked()));
    }
    private void event(String kind,String value){
        if(kind.equals("cursor")){
            if(!touchActive&&android.os.SystemClock.uptimeMillis()-lastLocalPointerAt>=CURSOR_ECHO_GRACE_MS){
                String[] position=value.split(",");cursorX=Math.max(0,Math.min(width-1,Float.parseFloat(position[0])));
                cursorY=Math.max(0,Math.min(height-1,Float.parseFloat(position[1])));updateCursor();
            }
            return;
        }
        if(kind.equals("status")&&value.equals("Apple encrypted control transport active"))clipboard.ready(true);
        if(kind.equals("error")&&NativeConnection.permanentFailure(value))saved.autoResume(false);
        debugLog.add(kind,kind.equals("metrics")?value+" "+renderer.counters():value);
        if(kind.equals("status")&&value.startsWith("Native standard VNC connected"))connected=true;
        if(kind.equals("error")||(kind.equals("status")&&value.equals("Native connection ended"))){connected=screenReady=false;clipboard.ready(false);trackpad.cancel();cursor.setVisibility(View.INVISIBLE);}
        if(kind.equals("metrics")||kind.equals("protocol"))return;
        if(kind.equals("error")){if(errorShown)return;errorShown=true;}
        else if(kind.equals("status")&&value.startsWith("Connecting"))errorShown=false;
        else if(kind.equals("status")&&errorShown)return;
        status.setVisibility(connected&&screenReady&&!errorShown?View.GONE:View.VISIBLE);
        if(kind.equals("size")){
            String[] size=value.split(",");int nextWidth=Integer.parseInt(size[0]),nextHeight=Integer.parseInt(size[1]);
            if(!haveFramebuffer||width!=nextWidth||height!=nextHeight){cursorX=nextWidth/2f;cursorY=nextHeight/2f;viewport.reset();}
            width=nextWidth;height=nextHeight;haveFramebuffer=true;fit();
        }else if(kind.equals("frames")){
            screenReady=true;if(connected){cursor.setVisibility(View.VISIBLE);status.setVisibility(View.GONE);}
            if(!profileSaved&&connection.profile()!=null){profileSaved=true;ConnectionProfile profile=connection.profile();
                if(profile.remember){saved.autoResume(true);saved.save(profile,ok->{if(!ok)android.widget.Toast.makeText(this,"Connection works, but saving failed",android.widget.Toast.LENGTH_LONG).show();else password.setHint("Saved password");});}}
            status.setText("Native Standard · "+value+" Surface posts");
        }
        else status.setText(value);
    }
    private void viewerAction(String action){
        switch(action){
            case "input-mode":trackpad.setDirect(!trackpad.direct());touchActive=false;break;
            case "keyboard":trackpad.cancel();keyboardDock.toggle();break;
            case "drag-lock":
                if(connected)trackpad.setDragLocked(!trackpad.dragLocked());else android.widget.Toast.makeText(this,"Connect to your Mac first",android.widget.Toast.LENGTH_SHORT).show();break;
            case "connection":trackpad.cancel();keyboardDock.hide();connectionDialog();break;
            case "refresh":connection.input(NativeRfb::refresh);break;
            case "clipboard-get":clipboard.request();break;
            case "clipboard-send":clipboard.phone(true);break;
            case "log":showDebugLog();break;
            case "pipeline":pipeline.setChecked(!pipeline.isChecked());break;
            case "scroll":ScrollSettings.show(this,speed->trackpad.setScrollSpeed(speed));break;
            case "disconnect":stop();break;
            case "fit":viewport.reset();applyViewport();break;
            case "zoom-in":case "zoom-out":
                viewport.zoomBy(action.equals("zoom-in")?1.4f:1/1.4f,texture.getWidth()/2f,texture.getHeight()/2f);applyViewport();break;
            case "help":new AlertDialog.Builder(this).setTitle("Desktop gestures")
                .setMessage("Use the PAD / TOUCH button to switch input modes. Trackpad is the default. In touch mode, tap the target directly; slide one finger to drag or select text. Two-finger scrolling and pinch zoom work in both modes.\n\nIn trackpad mode: slide one finger to move the pointer. Tap to click. Double-tap with one finger, keep it down after the second tap, then slide to drag. Lift to release.\n\nFor longer text selections, enable Drag lock in the menu, slide one finger (lift and reposition as needed), then turn Drag lock off to release.\n\nSlide two fingers together to scroll; tap with two fingers to right-click.\n\nPinch to zoom. When zoomed in, the view follows your pointer near its edges—keep using one finger to reach the rest of the desktop.\n\nUse Fit desktop to show the whole screen.")
                .setPositiveButton("Done",null).show();break;
        }
    }
    private void fit(){viewport.resize(texture.getWidth(),texture.getHeight(),width,height);if(trackpad==null||!trackpad.direct())viewport.follow(cursorX,cursorY);applyViewport();}
    private void applyViewport(){renderer.apply(viewport);updateCursor();}
    private void updateCursor(){cursor.position(width,height,cursorX,cursorY);}
    private boolean touch(View view,MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN)ignoreTouch=trackpad.direct()&&!viewport.contains(event.getX(),event.getY());
        if(ignoreTouch)return true;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            touchActive=true;trackpad.down(event.getPointerId(index),event.getX(index),event.getY(index),event.getEventTime());
        }else if(action==MotionEvent.ACTION_MOVE){
            int count=event.getPointerCount();int[] ids=new int[count];float[] xs=new float[count],ys=new float[count];
            for(int i=0;i<count;i++){ids[i]=event.getPointerId(i);xs[i]=event.getX(i);ys[i]=event.getY(i);}
            trackpad.moveContacts(ids,xs,ys);
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            trackpad.up(event.getPointerId(index),event.getEventTime());touchActive=action!=MotionEvent.ACTION_UP;
        }else if(action==MotionEvent.ACTION_CANCEL){trackpad.cancel();touchActive=false;}
        return true;
    }
    private void pointer(int buttons){lastLocalPointerAt=android.os.SystemClock.uptimeMillis();if(trackpad==null||!trackpad.direct())viewport.follow(cursorX,cursorY);applyViewport();connection.input(handle->NativeRfb.pointer(handle,(int)cursorX,(int)cursorY,buttons));}
    private void offline(){
        if(keyboardDock!=null)keyboardDock.clear();connected=screenReady=false;touchActive=false;clipboard.ready(false);
        if(trackpad!=null)trackpad.cancel();if(cursor!=null)cursor.setVisibility(View.INVISIBLE);
    }
    private void stop(){offline();connection.disconnect();saved.autoResume(false);}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){surface=new Surface(value);fit();connection.surface(surface);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value){offline();connection.surface(null);surface=null;return true;}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){fit();}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture value){}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)ViewerControls.immersive(this);if(clipboard!=null)clipboard.focus(focused&&resumed);}
    @Override protected void onResume(){super.onResume();resumed=true;clipboard.focus(hasWindowFocus());connection.foreground(true);}
    @Override protected void onPause(){resumed=false;clipboard.focus(false);offline();connection.foreground(false);super.onPause();}
    @Override protected void onDestroy(){connection.close();saved.close();super.onDestroy();}
}
