#!/usr/bin/env python3
"""Prepare a pinned, locally patched LibVNCClient copy without modifying its submodule."""
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent.parent
SOURCE=ROOT/'third_party/libvncserver'
OUTPUT=ROOT/'.dev/libvncclient-source'
REVISION='98aaee0560baa0332339b2ef464ea96ed9b9b28c'
PATCH='socket-idle-rsa33-v3'
identity=REVISION+' '+PATCH
if subprocess.check_output(['git','-C',str(SOURCE),'rev-parse','HEAD'],text=True).strip()!=REVISION:
    raise SystemExit('LibVNCClient revision differs from the reviewed pin')
marker=OUTPUT/'.ivnc-patch'
if not marker.exists() or marker.read_text()!=identity:
    if OUTPUT.exists(): shutil.rmtree(OUTPUT)
    shutil.copytree(SOURCE,OUTPUT,ignore=shutil.ignore_patterns('.git'))
    path=OUTPUT/'src/libvncclient/sockets.c'
    source=path.read_text()
    calls='WaitForMessage(client, USECS_WAIT_PER_RETRY);'
    assert source.count(calls)==2
    source=source.replace(calls,'ivncWaitSocket(client, USECS_WAIT_PER_RETRY);')
    signature='rfbBool\nReadFromRFBServer('
    assert signature in source
    source=source.replace(signature,'static int ivncWaitSocket(rfbClient* client,unsigned int usecs);\n\n'+signature,1)
    source=source.replace('int WaitForMessage(rfbClient* client,unsigned int usecs)','static int ivncWaitSocket(rfbClient* client,unsigned int usecs)',1)
    cached='''  /* Check if we have buffered data available */
  if (client->buffered > 0) {
    return 1;
  }
'''
    assert cached in source
    source=source.replace(cached,'',1)
    source+='''
/* The public event-loop wait sees buffered messages. Exact reads instead wait
 * on the socket: a partial buffered message does not satisfy their request. */
int WaitForMessage(rfbClient* client,unsigned int usecs)
{
  if (client->buffered > 0) return 1;
  return ivncWaitSocket(client,usecs);
}
'''
    # The timeout is idle time, not the number of waits across a healthy
    # fragmented transfer. Reset both exact-read loops after forward progress.
    small='      client->buffered += i;'
    large='      out += i;\n      n -= i;'
    assert source.count(small)==1 and source.count(large)==1
    source=source.replace(small,'      if (i > 0) retries = 0;\n'+small)
    source=source.replace(large,'      if (i > 0) retries = 0;\n'+large)
    path.write_text(source)
    path=OUTPUT/'src/libvncclient/rfbclient.c'
    source=path.read_text()
    selection='if (!WriteToRFBServer(client, (char *)&tAuth[loop], 1)) return FALSE;'
    assert source.count(selection)==1
    source=source.replace(selection,'if (tAuth[loop] != 33 && !WriteToRFBServer(client, (char *)&tAuth[loop], 1)) return FALSE;')
    # Report only the numeric SecurityResult, without exposing credentials or the
    # server's private failure strings. Apple does not append standard reasons.
    auth='    authResult = rfbClientSwap32IfLE(authResult);'
    assert source.count(auth)==1
    source=source.replace(auth,auth+"\n    ivncAuthenticationResult(client, authResult);\n    if (client->authScheme == 33) return authResult == rfbVncAuthOK;")
    signature='rfbBool\nrfbHandleAuthResult('
    assert signature in source
    source=source.replace(signature,'extern void ivncAuthenticationResult(rfbClient*, uint32_t);\n\n'+signature,1)
    path.write_text(source)
    marker.write_text(identity)
print('LibVNCClient '+identity+' prepared in project-local .dev')
