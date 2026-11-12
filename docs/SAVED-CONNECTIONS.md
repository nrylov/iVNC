# Saved connection and foreground reconnect

## Native client (0.15)

Enable **Remember connection securely** and connect. After the first displayed
frame, the native app saves one connection including password, enhanced /
compatibility mode and pipeline preference. It uses its own app-scoped Keystore
key and encrypted preferences, independent of the archived client. Saved passwords
appear as a hint and are never inserted into a password text field.

Switching away closes the active session; returning reconnects automatically.
A saved connection with active resume intent also reconnects after process
recreation. Manual **Disconnect** or login/permission rejection disables that
intent. **Forget saved connection** stops the session, clears the saved entry/key
and prevents automatic reuse. Unchecking Remember before connecting removes the
saved entry. A continuously foreground network failure has no automatic retry loop.

Galaxy synthetic tests verify these behaviors, zero-size startup/zoom retention,
stale callback rejection, rejected-login suppression and recovery from unreadable
saved data. Tests use separate keys/preferences and an in-memory clipboard; actual
Mac 0.15 verification is pending.

```sh
./scripts/device.sh shell am instrument -w -r \
  dev.ivnc.nativeclient/.SavedSessionInstrumentation
./scripts/device.sh shell am instrument -w -r \
  dev.ivnc.nativeclient/.ProfileStoreInstrumentation
```

## Storage

The complete profile uses AES-256-GCM with a fresh IV and an app-scoped Android
Keystore key requiring an unlocked device. Preferences hold ciphertext and IV;
Android backup is disabled. Unreadable/tampered data reports an error and retains
a Forget action. There is no plaintext fallback. Credentials exist in app memory
while authenticating and retaining reconnect intent. They are excluded from logs.
