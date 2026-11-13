#!/usr/bin/env python3
"""Prepare a pinned, locally patched LibVNCClient copy without modifying its submodule."""
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent.parent
SOURCE=ROOT/'third_party/libvncserver'
OUTPUT=ROOT/'.dev/libvncclient-source'
REVISION='98aaee0560baa0332339b2ef464ea96ed9b9b28c'
PATCH='socket-idle-rsa33-apple-session-v6'
identity=REVISION+' '+PATCH
# Complete release source bundles have no .git metadata. They include the exact
# prepared client source and identity so rebuilding does not require a Git clone.
ARCHIVED=ROOT/'prepared/libvncclient'
if not (SOURCE/'.git').exists() and ARCHIVED.exists():
    if (ARCHIVED/'.ivnc-patch').read_text()!=identity:
        raise SystemExit('Prepared release source identity differs from the reviewed pin')
    archived_marker=OUTPUT/'.ivnc-patch'
    if not archived_marker.exists() or archived_marker.read_text()!=identity:
        if OUTPUT.exists(): shutil.rmtree(OUTPUT)
        OUTPUT.parent.mkdir(parents=True,exist_ok=True)
        shutil.copytree(ARCHIVED,OUTPUT)
    raise SystemExit(0)
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
    source=source.replace('\nReadFromRFBServer(rfbClient*','\nivncReadPlain(rfbClient*',1)
    source=source.replace('\nWriteToRFBServer(rfbClient*','\nivncWritePlain(rfbClient*',1)
    source+='\nextern rfbBool ivncReadStream(rfbClient*,char*,unsigned int);\nextern rfbBool ivncWriteStream(rfbClient*,const char*,unsigned int);\nrfbBool ReadFromRFBServer(rfbClient* client,char* out,unsigned int n){return ivncReadStream(client,out,n);}\nrfbBool WriteToRFBServer(rfbClient* client,const char* bytes,unsigned int n){return ivncWriteStream(client,bytes,n);}\n'
    source=source.replace('  if (client->buffered > 0) return 1;','  if (client->buffered > 0 || ivncClearBuffered(client)) return 1;')
    source=source.replace('int WaitForMessage(rfbClient* client,unsigned int usecs)\n{','extern int ivncClearBuffered(rfbClient*);\nint WaitForMessage(rfbClient* client,unsigned int usecs)\n{')
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
    # One incremental request is kept ahead of decoding, instead of adding the
    # complete previous message's read/decode delay to every request cycle.
    signature='  case rfbFramebufferUpdate:\n  {\n    rfbFramebufferUpdateRectHeader rect;'
    assert source.count(signature)==1
    source=source.replace(signature,signature+'\n    rfbBool requestWasPipelined = FALSE;')
    received='    msg.fu.nRects = rfbClientSwap16IfLE(msg.fu.nRects);'
    assert source.count(received)==1
    source=source.replace(received,received+'\n    requestWasPipelined = ivncPipelineUpdates(client);\n    if (requestWasPipelined && !SendIncrementalFramebufferUpdateRequest(client)) return FALSE;')
    tail='    if (!SendIncrementalFramebufferUpdateRequest(client))\n      return FALSE;'
    assert source.count(tail)==1
    source=source.replace(tail,'    if (!requestWasPipelined && ivncContinueUpdates(client) && !SendIncrementalFramebufferUpdateRequest(client))\n      return FALSE;')
    source=source.replace('extern void ivncAuthenticationResult(rfbClient*, uint32_t);','extern int ivncContinueUpdates(rfbClient*);\nextern int ivncAppleRectangle(rfbClient*, unsigned,int,int,int,int);\nextern int ivncPipelineUpdates(rfbClient*);\nextern void ivncAuthenticationResult(rfbClient*, uint32_t);')
    rectangle='      if (rect.encoding == rfbEncodingXCursor ||'
    assert source.count(rectangle)==1
    source=source.replace(rectangle,'      {int apple = ivncAppleRectangle(client, rect.encoding, rect.r.x, rect.r.y, rect.r.w, rect.r.h); if(apple < 0) return FALSE; if(apple > 0) continue;}\n\n'+rectangle)
    declarations='extern int ivncAppleVersion(rfbClient*,int,int);\nextern int ivncAppleClientInit(rfbClient*,int);\nextern int ivncAppleServerInit(rfbClient*);\nextern int ivncAppleMessage(rfbClient*,unsigned);\nextern int ivncAppleEncodings(rfbClient*,uint32_t*,int,int);\n'
    source=declarations+source
    version='  sprintf(pv,rfbProtocolVersionFormat,client->major,client->minor);'
    assert source.count(version)==1
    source=source.replace(version,'  if (!ivncAppleVersion(client,major,minor)) return FALSE;\n'+version)
    source=source.replace('  ci.shared = (client->appData.shareDesktop ? 1 : 0);','  ci.shared = ivncAppleClientInit(client, (client->appData.shareDesktop ? 1 : 0));')
    initialized='  client->desktopName[client->si.nameLength] = 0;'
    assert source.count(initialized)==1
    source=source.replace(initialized,initialized+'\n  if (!ivncAppleServerInit(client)) return FALSE;')
    beforeLength='  len = sz_rfbSetEncodingsMsg + se->nEncodings * 4;'
    assert source.count(beforeLength)==1
    source=source.replace(beforeLength,'  se->nEncodings = ivncAppleEncodings(client,encs,se->nEncodings,MAX_ENCODINGS);\n'+beforeLength)
    message='  switch (msg.type) {'
    assert source.count(message)==1
    source=source.replace(message,'  { int apple = ivncAppleMessage(client,msg.type); if(apple < 0) return FALSE; if(apple > 0) return TRUE; }\n'+message)
    # Declarations require the public types, which are available below upstream includes.
    source=source.removeprefix(declarations)
    source=source.replace('rfbBool\nInitialiseRFBConnection(',declarations+'\nrfbBool\nInitialiseRFBConnection(',1)
    path.write_text(source)
    path=OUTPUT/'src/libvncclient/vncviewer.c'
    source=path.read_text()
    signature='rfbBool rfbClientInitialise(rfbClient* client) {'
    assert source.count(signature)==1
    source=source.replace(signature,'extern int ivncAppleSetupBefore(rfbClient*);\nextern int ivncAppleSetupAfter(rfbClient*);\nextern int ivncAppleInitialRequest(rfbClient*);\n'+signature)
    source=source.replace('  if (!SetFormatAndEncodings(client))','  if (!ivncAppleSetupBefore(client)) return FALSE;\n  if (!SetFormatAndEncodings(client))',1)
    scale='  if (client->appData.scaleSetting>1)'
    assert source.count(scale)==1
    source=source.replace(scale,'  if (!ivncAppleSetupAfter(client)) return FALSE;\n  if (!ivncAppleInitialRequest(client)) return TRUE;\n'+scale)
    path.write_text(source)
    marker.write_text(identity)
print('LibVNCClient '+identity+' prepared in project-local .dev')
