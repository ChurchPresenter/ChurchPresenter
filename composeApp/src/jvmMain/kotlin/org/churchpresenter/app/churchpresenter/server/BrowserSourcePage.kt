package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/**
 * Self-contained HTML page for an OBS/vMix Browser Source input. The actual content is
 * rendered off-screen in main.kt (BrowserSourceVideoRenderer,
 * using the same BiblePresenter/SongPresenter/AnnouncementsPresenter/PicturePresenter/
 * StageMonitorScreen composables as everywhere else) and streamed as binary-framed PNG
 * deltas over a WebSocket — so styling is pixel-identical to the native output by
 * construction, not reimplemented in CSS/JS. WebSocket was chosen after HTTP
 * multipart/x-mixed-replace proved unreliable in both directions: Chromium's `<img>` support
 * for that MIME type is legacy/inconsistent (historically JPEG-only), and Safari's
 * `fetch()`/`ReadableStream` failed outright with "TypeError: Load failed" for this exact
 * indefinitely-long streaming response pattern — reproduced even on localhost, so it wasn't
 * a network issue. WebSocket message boundaries are handled natively by the browser, so
 * there's no manual buffer/boundary parsing on either side anymore. Each delta is usually
 * just the sub-rectangle that changed (not the full frame), composited onto a persistent
 * offscreen canvas at the stream's native resolution; that offscreen canvas is what's
 * actually drawn, scaled+centered, into the visible window-sized `<canvas>`. Served with
 * `Cache-Control: no-store` since OBS/browsers cache a Browser Source page aggressively and
 * otherwise won't refetch it after this wire protocol changes — a stale cached copy of this
 * page's JS would silently misinterpret messages from a newer server. If this protocol
 * changes again, any already-open OBS Browser Source still needs a manual "Refresh cache of
 * current page" (OBS won't do this on its own even with no-store, since it doesn't re-request
 * an already loaded page) — no-store only guarantees a *fresh* page load gets current JS.
 */
internal fun browserSourceOverlayPage(
    index: Int,
    output: ScreenAssignment,
    apiKeyEnabled: Boolean,
    apiKey: String,
    bgOverride: String? = null,
): String {
    val needsKey = output.browserSourceApiKeyRequired || (apiKeyEnabled && apiKey.isNotEmpty())
    val keyParam = if (needsKey) "?${Constants.QUERY_PARAM_API_KEY}=" + java.net.URLEncoder.encode(apiKey, "UTF-8") else ""
    // ?bg= is a per-request debug override (e.g. for viewing outside OBS, where a page
    // background left transparent just renders as opaque white in a plain browser tab) —
    // it's purely a page-preview convenience, unrelated to whether the rendered frame itself
    // has a background (that's screenAssignment.showFullscreenBackground/showLowerThirdBackground,
    // read by BrowserSourceVideoRenderer, same fields native output uses).
    val bodyBg = when (bgOverride?.lowercase()) {
        "black" -> "#000"
        else -> "transparent"
    }
    val wsPath = "/api${Constants.ENDPOINT_BROWSER_SOURCE}/$index/ws$keyParam"
    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>ChurchPresenter Browser Source</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:100%;height:100%;background:$bodyBg;overflow:hidden}
#stage{position:fixed;inset:0}
#frame{width:100%;height:100%;display:block}
#diag{position:fixed;bottom:8px;left:8px;max-width:90%;padding:6px 10px;
  background:rgba(200,0,0,0.85);color:#fff;font:12px/1.4 monospace;
  border-radius:4px;display:none;z-index:9999;white-space:pre-wrap}
</style>
</head>
<body>
<div id="stage"><canvas id="frame"></canvas></div>
<div id="diag"></div>
<script>
const wsUrl=(location.protocol==='https:'?'wss:':'ws:')+'//'+location.host+'$wsPath';
const canvas=document.getElementById('frame');
const ctx=canvas.getContext('2d');

// Surfaces failures directly on the page instead of only in devtools — this page is normally
// only ever looked at through OBS/vMix's embedded browser, where nobody opens a console, so a
// silently-swallowed error here previously meant "shows nothing" with zero way to diagnose why.
const diagEl=document.getElementById('diag');
function showDiag(msg){
  if(!diagEl)return;
  diagEl.textContent=msg;
  diagEl.style.display='block';
}
function clearDiag(){
  if(diagEl)diagEl.style.display='none';
}
if(typeof OffscreenCanvas==='undefined'){
  showDiag('Browser Source error: this browser/OBS version does not support OffscreenCanvas. Update your browser or OBS.');
}

// The offscreen canvas is the authoritative "current full frame" at the stream's native
// resolution. Incoming deltas (full-frame or a changed sub-rectangle) are composited onto it;
// the visible, window-sized canvas is then repainted scaled+centered from it. This is also why
// a window resize with no new delta doesn't go blank — resizeCanvas() just repaints from the
// same offscreen content at the new size.
let offscreen=null, offscreenCtx=null, hasFullFrame=false;

function paintBitmap(){
  if(!offscreen)return;
  const scale=Math.min(canvas.width/offscreen.width, canvas.height/offscreen.height);
  const w=offscreen.width*scale, h=offscreen.height*scale;
  const x=(canvas.width-w)/2, y=(canvas.height-h)/2;
  ctx.clearRect(0,0,canvas.width,canvas.height);
  ctx.drawImage(offscreen,x,y,w,h);
}
function resizeCanvas(){
  canvas.width=window.innerWidth;
  canvas.height=window.innerHeight;
  paintBitmap();
}
window.addEventListener('resize',resizeCanvas);
resizeCanvas();

async function drawFrame(pngBytes,rx,ry,rw,rh,fullW,fullH){
  const isFullFrame = rx===0 && ry===0 && rw===fullW && rh===fullH;
  // A newly-connected/reconnected client has nothing to apply a partial rect onto yet — discard
  // any delta until the first full frame arrives, so it never shows a corrupt/torn draw.
  if(!hasFullFrame && !isFullFrame)return;
  try{
// Payload is PNG (transparency) or JPEG (fully opaque frames) — sniff the first byte.
const mime = pngBytes[0]===0xFF ? 'image/jpeg' : 'image/png';
const blob=new Blob([pngBytes],{type:mime});
const bitmap=await createImageBitmap(blob);
if(isFullFrame){
  if(!offscreen || offscreen.width!==fullW || offscreen.height!==fullH){
    offscreen=new OffscreenCanvas(fullW,fullH);
    offscreenCtx=offscreen.getContext('2d');
  }
  offscreenCtx.clearRect(0,0,fullW,fullH);
  offscreenCtx.drawImage(bitmap,0,0);
  hasFullFrame=true;
}else{
  // Replace, don't blend: frames carry real alpha, and source-over compositing a delta
  // whose pixels became MORE transparent (e.g. a fade-out) would ghost over stale pixels.
  offscreenCtx.clearRect(rx,ry,rw,rh);
  offscreenCtx.drawImage(bitmap,rx,ry);
}
bitmap.close();
paintBitmap();
clearDiag();
  }catch(e){
console.error('[BrowserSource] drawFrame failed',e);
showDiag('Browser Source error (rendering frame): '+e);
  }
}
// Each WebSocket message is exactly one frame delta — a fixed 24-byte big-endian header (six
// Int32s: x, y, rectWidth, rectHeight, fullWidth, fullHeight) followed by the raw PNG bytes. No
// manual buffer/boundary parsing needed: the browser's WebSocket implementation already delivers
// complete messages, unlike the old fetch()+ReadableStream+multipart approach this replaced.
// drawFrame is async (createImageBitmap) — during a burst of large frames (e.g. a full-screen
// crossfade) decode+draw can take longer than the ~33ms tick interval frames arrive at. Chaining
// every message unconditionally would grow an ever-longer backlog and make the display fall
// further and further behind real time. Instead, coalesce to the latest: if a new message arrives
// while one is still being processed, only the newest is kept — any skipped intermediate partial
// delta leaves the offscreen canvas briefly wrong in that region, but the server's periodic
// full-frame reseed (~every 5s, see FULL_FRAME_RESEED_MS in BrowserSourceVideoRenderer.kt)
// self-heals that within a bounded window, which is a better trade than unbounded latency growth.
let isProcessingFrame=false;
let pendingFrame=null;
async function processPendingFrame(){
  if(isProcessingFrame)return;
  const frame=pendingFrame;
  if(!frame)return;
  pendingFrame=null;
  isProcessingFrame=true;
  await drawFrame(frame.pngBytes,frame.x,frame.y,frame.w,frame.h,frame.fullW,frame.fullH);
  isProcessingFrame=false;
  if(pendingFrame)processPendingFrame();
}
function onSocketMessage(event){
  const buf=event.data;
  const view=new DataView(buf);
  const x=view.getInt32(0);
  const y=view.getInt32(4);
  const w=view.getInt32(8);
  const h=view.getInt32(12);
  const fullW=view.getInt32(16);
  const fullH=view.getInt32(20);
  const pngBytes=new Uint8Array(buf,24);
  pendingFrame={pngBytes,x,y,w,h,fullW,fullH};
  processPendingFrame();
}
function connect(){
  // A fresh connection may follow a stale/dropped one — never composite a partial delta onto
  // whatever the offscreen canvas last held until this connection's own full frame arrives.
  hasFullFrame=false;
  const ws=new WebSocket(wsUrl);
  ws.binaryType='arraybuffer';
  ws.onmessage=onSocketMessage;
  ws.onopen=()=>clearDiag();
  ws.onerror=(e)=>{
console.error('[BrowserSource] websocket error',e);
  };
  ws.onclose=(event)=>{
// A disabled/unknown output or bad API key closes with an explicit reason (see the
// /ws route in CompanionServer.kt) — surface it directly instead of silently retrying
// forever with no clue why nothing is showing up.
if(event.reason){
  showDiag('Browser Source error: '+event.reason);
}else if(!event.wasClean){
  showDiag('Browser Source error: connection lost, retrying...');
}
setTimeout(connect,2000);
  };
}
connect();
</script>
</body>
</html>
""".trimIndent()
}
