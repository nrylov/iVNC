/* Shared chunked base64 helpers for trusted JS/native binary transport. */
/** Encodes an exact byte view without overflowing the JS argument stack. */
export function encodeBytes(data) {
    const bytes = data instanceof ArrayBuffer ? new Uint8Array(data)
        : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    let binary = '';
    for (let offset = 0; offset < bytes.length; offset += 8192)
        binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
    return btoa(binary);
}
/** Returns independent bytes, since the native event string is short-lived. */
export function decodeBytes(text) {
    return Uint8Array.from(atob(text), character => character.charCodeAt(0));
}
