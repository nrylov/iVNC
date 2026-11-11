/* Android IME text entry and an advanced keyboard dock without browser-level shortcut interception. */
import { RemoteKeys, MODIFIERS, SPECIAL_KEYS } from './key-model.js';

/** Exposes remote modifiers, navigation keys and F1–F12 alongside ordinary phone typing. */
export function attachKeyboard(getRfb, isConnected) {
    const dock = document.getElementById('keyboard-dock');
    const input = document.getElementById('type-input');
    const keys = new RemoteKeys((...args) => { if (isConnected()) getRfb()?.sendKey(...args); });
    const modifierButtons = [];
    function button(row, text, action) {
        const item = document.createElement('button');
        item.textContent = text;
        item.onpointerdown = event => event.preventDefault();
        item.onclick = action;
        row.append(item);
        return item;
    }
    const modifiers = document.getElementById('modifier-keys');
    for (const [label, symbol, code] of MODIFIERS) {
        const item = button(modifiers, label, () => item.setAttribute('aria-pressed', String(keys.toggle(symbol, code))));
        item.setAttribute('aria-pressed', 'false'); modifierButtons.push(item);
    }
    const special = document.getElementById('special-keys');
    for (const [label, symbol, code] of SPECIAL_KEYS) button(special, label, () => keys.key(symbol, code));
    const functions = document.getElementById('function-keys');
    for (let number = 1; number <= 12; number++)
        button(functions, 'F' + number, () => keys.key(0xffbd + number, 'F' + number));
    function resetInput() { input.value = ' '; input.setSelectionRange(1, 1); }
    function release() {
        keys.release();
        for (const item of modifierButtons) item.setAttribute('aria-pressed', 'false');
    }
    function close() { release(); input.blur(); dock.hidden = true; document.body.classList.remove('keyboard-visible'); }
    document.getElementById('keyboard-open').onclick = () => {
        if (!dock.hidden) { close(); return; }
        dock.hidden = false; document.body.classList.add('keyboard-visible'); resetInput(); input.focus({preventScroll:true});
    };
    document.getElementById('keyboard-close').onclick = close;
    document.getElementById('keyboard-type').onclick = () => { resetInput(); input.focus({preventScroll:true}); };
    let composing = false;
    input.oncompositionstart = () => { composing = true; };
    input.oncompositionend = () => { composing = false; consume({inputType:'insertText'}); };
    function consume(event) {
        if (composing || event.isComposing) return;
        if (event.inputType?.startsWith('delete') && input.value === '') keys.key(0xff08, 'Backspace');
        else keys.text(input.value.startsWith(' ') ? input.value.slice(1) : input.value);
        resetInput();
    }
    input.oninput = consume;
    input.onkeydown = event => {
        if (composing || event.isComposing) return;
        const named = SPECIAL_KEYS.find(([, , code]) => code === event.code || code === event.key);
        if (named) { event.preventDefault(); keys.key(named[1], named[2]); }
    };
    return { close, release, update: () => {
        document.getElementById('keyboard-open').disabled = !isConnected();
        if (!isConnected()) close();
    } };
}
