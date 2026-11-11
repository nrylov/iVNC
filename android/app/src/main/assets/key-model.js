/* X11 keysyms and sticky remote modifiers, used by the phone's advanced keyboard. */
export const MODIFIERS = [
    ['Ctrl', 0xffe3, 'ControlLeft'], ['Alt / ⌥', 0xffe9, 'AltLeft'],
    ['Cmd / ⌘', 0xffeb, 'MetaLeft'], ['Shift', 0xffe1, 'ShiftLeft'],
];
export const SPECIAL_KEYS = [
    ['Esc',0xff1b,'Escape'],['Tab',0xff09,'Tab'],['←',0xff51,'ArrowLeft'],
    ['↑',0xff52,'ArrowUp'],['↓',0xff54,'ArrowDown'],['→',0xff53,'ArrowRight'],
    ['Enter',0xff0d,'Enter'],['⌫',0xff08,'Backspace'],['Delete',0xffff,'Delete'],
    ['Home',0xff50,'Home'],['End',0xff57,'End'],['PgUp',0xff55,'PageUp'],
    ['PgDn',0xff56,'PageDown'],['Space',0x20,'Space'],
];
/** Encodes Unicode using the RFB/X11 convention while retaining named special keys. */
export function textKeys(text) {
    return [...text].filter(character => character !== '\r').map(character => {
        if (character === '\n') return [0xff0d, 'Enter'];
        const point = character.codePointAt(0);
        const code = /^[a-z]$/i.test(character) ? 'Key' + character.toUpperCase()
            : /^[0-9]$/.test(character) ? 'Digit' + character : 'Unidentified';
        return [point > 255 ? 0x01000000 | point : point, code];
    });
}
/** Keeps modifier down/up balanced across toggles, hide, disconnect and backgrounding. */
export class RemoteKeys {
    constructor(send) { this.send = send; this.held = new Map(); }
    toggle(symbol, code) {
        const down = !this.held.has(symbol);
        if (down) this.held.set(symbol, code); else this.held.delete(symbol);
        this.send(symbol, code, down);
        return down;
    }
    key(symbol, code) { this.send(symbol, code); }
    text(text) { for (const [symbol, code] of textKeys(text)) this.key(symbol, code); }
    release() {
        for (const [symbol, code] of this.held) this.send(symbol, code, false);
        this.held.clear();
    }
}
