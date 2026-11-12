/* IME adapter sends committed text, retaining composition only locally until it is committed. */
package dev.ivnc.nativeclient;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/** Offers the phone's keyboard without logging or saving typed text in view state. */
final class RemoteKeyInput extends EditText {
    private final Predicate<CharSequence> text;
    private final IntPredicate key;
    RemoteKeyInput(Context context,Predicate<CharSequence> text,IntPredicate key){
        super(context);this.text=text;this.key=key;setHint("Type on Mac");setSingleLine();setSaveEnabled(false);
        setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_ACTION_NONE);
        setTag("native-remote-input");
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo info){
        InputConnection base=super.onCreateInputConnection(info);
        return new InputConnectionWrapper(base,false){
            @Override public boolean commitText(CharSequence value,int cursor){
                boolean sent=text.test(value);if(sent)getText().clear();return sent;
            }
            @Override public boolean finishComposingText(){
                int start=BaseInputConnection.getComposingSpanStart(getText()),end=BaseInputConnection.getComposingSpanEnd(getText());
                if(start>=0&&end>start&&!text.test(getText().subSequence(start,end).toString()))return false;
                getText().clear();return super.finishComposingText();
            }
            @Override public boolean deleteSurroundingText(int before,int after){
                if(getText().length()>0)return super.deleteSurroundingText(before,after);
                for(int i=0;i<Math.min(before,32);i++)if(!key.test(0xff08))return false;
                for(int i=0;i<Math.min(after,32);i++)if(!key.test(0xffff))return false;return true;
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before,int after){return deleteSurroundingText(before,after);}
            @Override public boolean sendKeyEvent(KeyEvent event){
                int symbol=symbol(event);if(symbol==0)return super.sendKeyEvent(event);
                return event.getAction()!=KeyEvent.ACTION_DOWN||key.test(symbol);
            }
            @Override public boolean performEditorAction(int action){return key.test(0xff0d);}
        };
    }
    private static int symbol(KeyEvent event){
        switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DEL:return 0xff08;case KeyEvent.KEYCODE_ENTER:return 0xff0d;
            case KeyEvent.KEYCODE_TAB:return 0xff09;case KeyEvent.KEYCODE_ESCAPE:return 0xff1b;
            case KeyEvent.KEYCODE_DPAD_LEFT:return 0xff51;case KeyEvent.KEYCODE_DPAD_UP:return 0xff52;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return 0xff53;case KeyEvent.KEYCODE_DPAD_DOWN:return 0xff54;
            default:int code=event.getUnicodeChar();return code==0?0:code<=255?code:0x01000000|code;
        }
    }
}
