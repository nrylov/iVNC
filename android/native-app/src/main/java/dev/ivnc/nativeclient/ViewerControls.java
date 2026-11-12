/* Unobtrusive native keyboard, input-mode and menu controls above the desktop. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Gives the remote desktop the full viewport while keeping connection and diagnostics accessible. */
final class ViewerControls {
    static void immersive(Activity activity){
        android.view.WindowInsetsController controller=activity.getWindow().getDecorView().getWindowInsetsController();
        if(controller!=null){controller.hide(android.view.WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
    }
    static void reserved(FrameLayout root,int bottom){
        android.view.WindowInsets insets=root.getRootWindowInsets();int safe=insets==null?0:insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom;
        for(String tag:new String[]{"native-viewer-menu","native-keyboard-button","native-input-mode"}){
            android.view.View button=root.findViewWithTag(tag);if(button==null)continue;
            FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)button.getLayoutParams();int margin=Math.max(safe,bottom)+(int)(12*root.getResources().getDisplayMetrics().density);if(layout.bottomMargin!=margin){layout.bottomMargin=margin;button.setLayoutParams(layout);}
        }
    }
    static void attach(Activity activity,FrameLayout root,Supplier<String> status,Supplier<Boolean> pipeline,Supplier<Boolean> dragging,Supplier<Boolean> direct,java.util.function.IntSupplier reserved,Consumer<String> action){
        float density=activity.getResources().getDisplayMetrics().density;
        Button button=new Button(activity);button.setText("⋮");button.setTextSize(25);button.setTextColor(0xffffffff);
        button.setPadding(0,0,0,0);button.setAlpha(.65f);button.setContentDescription("Viewer options");button.setTag("native-viewer-menu");
        GradientDrawable background=new GradientDrawable();background.setColor(0xcc17283b);background.setCornerRadius(100*density);button.setBackground(background);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams((int)(48*density),(int)(48*density),Gravity.BOTTOM|Gravity.END);
        position.rightMargin=position.bottomMargin=(int)(12*density);root.addView(button,position);
        button.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            position.rightMargin=safe.right+(int)(12*density);position.bottomMargin=Math.max(safe.bottom,reserved.getAsInt())+(int)(12*density);button.setLayoutParams(position);return insets;
        });
        Button keyboard=new Button(activity);keyboard.setText("⌨");keyboard.setTextSize(20);keyboard.setTextColor(0xffffffff);
        keyboard.setPadding(0,0,0,0);keyboard.setAlpha(.65f);keyboard.setContentDescription("Open keyboard");keyboard.setTag("native-keyboard-button");
        GradientDrawable keyboardBackground=new GradientDrawable();keyboardBackground.setColor(0xcc17283b);keyboardBackground.setCornerRadius(100*density);keyboard.setBackground(keyboardBackground);
        FrameLayout.LayoutParams keyboardPosition=new FrameLayout.LayoutParams((int)(48*density),(int)(48*density),Gravity.BOTTOM|Gravity.END);
        keyboardPosition.rightMargin=(int)(72*density);keyboardPosition.bottomMargin=(int)(12*density);root.addView(keyboard,keyboardPosition);
        keyboard.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            keyboardPosition.rightMargin=safe.right+(int)(72*density);keyboardPosition.bottomMargin=Math.max(safe.bottom,reserved.getAsInt())+(int)(12*density);keyboard.setLayoutParams(keyboardPosition);return insets;
        });
        keyboard.setOnClickListener(view->action.accept("keyboard"));
        Button mode=new Button(activity);mode.setTextSize(10);mode.setTextColor(0xffffffff);mode.setPadding(0,0,0,0);mode.setAlpha(.65f);mode.setTag("native-input-mode");
        GradientDrawable modeBackground=new GradientDrawable();modeBackground.setColor(0xcc17283b);modeBackground.setCornerRadius(100*density);mode.setBackground(modeBackground);
        FrameLayout.LayoutParams modePosition=new FrameLayout.LayoutParams((int)(48*density),(int)(48*density),Gravity.BOTTOM|Gravity.END);
        modePosition.rightMargin=(int)(132*density);modePosition.bottomMargin=(int)(12*density);root.addView(mode,modePosition);inputMode(mode,direct.get());
        mode.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            modePosition.rightMargin=safe.right+(int)(132*density);modePosition.bottomMargin=Math.max(safe.bottom,reserved.getAsInt())+(int)(12*density);mode.setLayoutParams(modePosition);return insets;
        });
        mode.setOnClickListener(view->{action.accept("input-mode");inputMode(mode,direct.get());});
        button.setOnClickListener(view->{
            PopupMenu menu=new PopupMenu(activity,button);
            menu.getMenu().add(status.get()).setEnabled(false);
            String[][] items={{"Connection…","connection"},{"Zoom in","zoom-in"},{"Zoom out","zoom-out"},
                {"Fit desktop","fit"},{"Scroll sensitivity…","scroll"},{"Refresh desktop","refresh"},{"Get Mac clipboard","clipboard-get"},{"Send phone clipboard","clipboard-send"},{"Gesture help","help"},{"Disconnect","disconnect"}};
            for(int i=0;i<items.length;i++)menu.getMenu().add(0,i,i+1,items[i][0]);
            menu.getMenu().add(0,300,4,"Drag lock").setCheckable(true).setChecked(dragging.get());
            android.view.SubMenu advanced=menu.getMenu().addSubMenu(0,200,20,"Diagnostics");
            advanced.add(0,100,0,"Pipeline updates").setCheckable(true).setChecked(pipeline.get());
            advanced.add(0,101,1,"Debug log");
            menu.setOnMenuItemClickListener(item->{
                int id=item.getItemId();if(id==300){action.accept("drag-lock");return true;}if(id==200)return false;if(id==100)action.accept("pipeline");else if(id==101)action.accept("log");
                else if(id>=0&&id<items.length)action.accept(items[id][1]);return true;
            });menu.show();
        });
    }
    private static void inputMode(Button button,boolean direct){
        button.setText(direct?"TOUCH":"PAD");
        String description=(direct?"Touch mode. Switch to trackpad mode":"Trackpad mode. Switch to touch mode");
        button.setContentDescription(description);button.setTooltipText(description);
    }
}
