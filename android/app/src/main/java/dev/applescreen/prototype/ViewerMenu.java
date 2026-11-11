/* Small native menu that remains accessible above the native desktop video surface. */
package dev.applescreen.prototype;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import java.util.function.Consumer;

/** Keeps the desktop full-screen while offering one unobtrusive entry to controls. */
final class ViewerMenu {
    static void attach(Activity activity, FrameLayout parent, Consumer<String> action) {
        Button button = new Button(activity);
        button.setText("⋮"); button.setTextSize(24); button.setTextColor(0xffffffff);
        button.setPadding(0,0,0,0); button.setFocusable(false); button.setAlpha(0.65f);
        button.setContentDescription("Viewer options");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xbb17283b); background.setCornerRadius(100);
        button.setBackground(background);
        float density = activity.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams((int)(44*density),(int)(44*density),Gravity.BOTTOM|Gravity.END);
        position.rightMargin = position.bottomMargin = (int)(12*density);
        parent.addView(button,position);
        String[][] items = {{"Connection","connection"},{"Keyboard","keyboard-open"},{"Zoom in","zoom-in"},
            {"Zoom out","zoom-out"},{"Fit desktop","zoom-fit"},{"Left click","left-click"},
            {"Right click","right-click"},{"Drag lock","drag"},{"Get Mac clipboard","get"},
            {"Send phone clipboard","send"},{"Disconnect","disconnect"},{"About iVNC","about"}};
        button.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(new ContextThemeWrapper(activity,android.R.style.Theme_Material),button);
            for (int index=0;index<items.length;index++) popup.getMenu().add(0,index,index,items[index][0]);
            popup.setOnMenuItemClickListener(item -> { action.accept(items[item.getItemId()][1]); return true; });
            popup.show();
        });
    }
}
