/* Native accessory keys above the Android IME; no WebView or external application UI. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Keeps modifier/navigation keys available alongside normal phone typing. */
final class KeyboardDock {
    private final Activity activity;
    private final FrameLayout root;
    private final LinearLayout panel;
    private final RemoteKeyInput input;
    private final NativeKeyboard keyboard;
    private final Consumer<Integer> reserved;
    private final Map<Integer,Button> modifiers=new HashMap<>();
    private int imeBottom;
    KeyboardDock(Activity activity,FrameLayout root,NativeKeyboard keyboard,Consumer<Integer> reserved){
        this.activity=activity;this.root=root;this.keyboard=keyboard;this.reserved=reserved;
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(0xf0152233);panel.setTag("native-keyboard-dock");panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(panel,layout);
        LinearLayout keys=row();
        modifier(keys,"Ctrl",NativeKeyboard.CTRL);modifier(keys,"Alt",NativeKeyboard.ALT);modifier(keys,"⌘",NativeKeyboard.CMD);modifier(keys,"Shift",NativeKeyboard.SHIFT);
        key(keys,"←",0xff51);key(keys,"↑",0xff52);key(keys,"↓",0xff54);key(keys,"→",0xff53);
        LinearLayout extra=row();View extraRow=(View)extra.getParent();extraRow.setVisibility(View.GONE);
        key(extra,"Esc",0xff1b);key(extra,"Tab",0xff09);key(extra,"Enter",0xff0d);key(extra,"⌫",0xff08);
        key(extra,"Home",0xff50);key(extra,"End",0xff57);key(extra,"PgUp",0xff55);key(extra,"PgDn",0xff56);key(extra,"Delete",0xffff);
        for(int i=1;i<=12;i++)key(extra,"F"+i,0xffbd+i);
        LinearLayout typing=new LinearLayout(activity);panel.addView(typing);
        input=new RemoteKeyInput(activity,text->result(keyboard.text(text)),symbol->result(keyboard.press(symbol)));
        typing.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button more=new Button(activity);more.setText("More");more.setOnClickListener(view->extraRow.setVisibility(extraRow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));typing.addView(more);
        Button close=new Button(activity);close.setText("Hide");close.setOnClickListener(view->hide());typing.addView(close);
        panel.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->reserve());
        root.setOnApplyWindowInsetsListener((view,insets)->{
            imeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;
            if(layout.bottomMargin!=imeBottom){layout.bottomMargin=imeBottom;panel.setLayoutParams(layout);}reserve();return insets;
        });
    }
    private LinearLayout row(){
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);panel.addView(scroll);
        LinearLayout row=new LinearLayout(activity);scroll.addView(row);return row;
    }
    private void key(LinearLayout row,String label,int symbol){Button button=button(row,label);button.setOnClickListener(view->result(keyboard.press(symbol)));}
    private Button button(LinearLayout row,String label){Button button=new Button(activity);button.setText(label);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth((int)(44*activity.getResources().getDisplayMetrics().density));button.setPadding(18,0,18,0);row.addView(button);return button;}
    private void modifier(LinearLayout row,String label,int symbol){Button button=button(row,label);modifiers.put(symbol,button);button.setOnClickListener(view->{keyboard.toggle(symbol);refresh();});}
    private boolean result(boolean accepted){
        if(!accepted)android.widget.Toast.makeText(activity,"Input unavailable, busy, or text too long",android.widget.Toast.LENGTH_SHORT).show();refresh();return accepted;
    }
    private void refresh(){for(Map.Entry<Integer,Button> entry:modifiers.entrySet()){boolean selected=keyboard.selected(entry.getKey());entry.getValue().setTextColor(selected?0xff7fcaff:0xffeeeeee);entry.getValue().setSelected(selected);}}
    private void reserve(){reserved.accept(panel.getVisibility()==View.VISIBLE?panel.getHeight()+imeBottom:0);}
    void toggle(){if(panel.getVisibility()==View.VISIBLE&&imeBottom>0)hide();else show();}
    void show(){panel.setVisibility(View.VISIBLE);input.requestFocus();input.post(()->showIme(10));reserve();}
    private void showIme(int retries){
        if(panel.getVisibility()!=View.VISIBLE)return;
        input.requestFocus();
        if(input.hasWindowFocus()&&input.isAttachedToWindow()){
            android.view.WindowInsetsController controller=input.getWindowInsetsController();if(controller!=null)controller.show(WindowInsets.Type.ime());
            activity.getSystemService(InputMethodManager.class).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        }else if(retries>0)input.postDelayed(()->showIme(retries-1),50);
    }
    void hide(){keyboard.clear();input.getText().clear();refresh();panel.setVisibility(View.GONE);activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);reserved.accept(0);root.requestFocus();}
    void clear(){keyboard.clear();input.getText().clear();refresh();}
}
