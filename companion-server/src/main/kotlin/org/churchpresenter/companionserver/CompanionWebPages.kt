package org.churchpresenter.companionserver

/**
 * The self-contained web pages the companion server serves to phones: the Q&A submission, voting
 * and moderation pages, and the presentation remote.
 *
 * Static markup and CSS -- 901 lines of it, with three interpolations in the whole set. Kept out of
 * `CompanionServer` because it is not server logic and its bulk buried the routes: the class was
 * 5,755 lines, a fifth of which was this. Editing a page no longer means scrolling through the
 * request pipeline, and reading the pipeline no longer means scrolling through HTML.
 *
 * Moved verbatim; only visibility changed (private class members -> internal top-level).
 */

internal val qaSharedCss = """
:root{
--qa-primary:#1e88e5;--qa-primary-hover:#1565c0;
--qa-success:#43a047;--qa-success-hover:#2e7d32;
--qa-danger:#e53935;--qa-danger-hover:#c62828;
}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
""".trimIndent()

internal fun qaSubmissionPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Ask a Question</title>
<style>
${qaSharedCss}
:root{--qa-bg:#f5f5f5;--qa-surface:#fff;--qa-ink:#1e1e2e;--qa-sub:#5f5f6b;--qa-border:#e0e0e0;--qa-muted:#616161}
@media(prefers-color-scheme:dark){:root{--qa-bg:#16161f;--qa-surface:#22222e;--qa-ink:#e8e8ef;--qa-sub:#a8a8b5;--qa-border:#3a3a4a;--qa-muted:#9a9aa6}}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:16px}
.card{background:var(--qa-surface);border-radius:16px;box-shadow:0 2px 12px rgba(0,0,0,.1);padding:32px;max-width:480px;width:100%}
h1{font-size:24px;margin-bottom:8px;color:var(--qa-ink)}
p.sub{color:var(--qa-sub);margin-bottom:24px;font-size:14px}
label.field-label{display:block;font-size:13px;font-weight:600;color:var(--qa-ink);margin-bottom:6px}
input.name-input{width:100%;border:2px solid var(--qa-border);border-radius:12px;padding:12px 16px;font-size:15px;font-family:inherit;margin-bottom:16px;background:var(--qa-surface);color:var(--qa-ink);transition:border-color .2s}
input.name-input:focus{outline:none;border-color:var(--qa-primary)}
textarea{width:100%;min-height:120px;border:2px solid var(--qa-border);border-radius:12px;padding:16px;font-size:16px;resize:vertical;font-family:inherit;background:var(--qa-surface);color:var(--qa-ink);transition:border-color .2s}
textarea:focus{outline:none;border-color:var(--qa-primary)}
input.name-input::placeholder,textarea::placeholder{color:var(--qa-muted)}
button{width:100%;min-height:48px;padding:14px;background:var(--qa-primary);color:#fff;border:none;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;margin-top:16px;transition:background .2s}
button:hover{background:var(--qa-primary-hover)}
button:disabled{background:#9aa0a6;cursor:not-allowed}
.msg{text-align:center;padding:12px;border-radius:8px;margin-top:16px;font-size:14px}
.msg.ok{background:#e8f5e9;color:#1b5e20}
.msg.err{background:#ffebee;color:#b71c1c}
.msg.off{background:#fff3e0;color:#e65100}
#charcount{text-align:right;font-size:12px;color:var(--qa-muted);margin-top:4px}
@media(prefers-color-scheme:dark){.msg.ok{background:#1b3a24;color:#a5d6a7}.msg.err{background:#3a1c1f;color:#ef9a9a}.msg.off{background:#3a2a12;color:#ffcc80}.card{box-shadow:0 2px 12px rgba(0,0,0,.4)}}
</style>
</head>
<body>
<main class="card">
<h1 id="page-title">Ask a Question</h1>
<p class="sub" id="page-sub">Your question will be reviewed before being displayed.</p>
<div id="form-area">
<label class="sr-only" for="name">Your name (optional)</label>
<input type="text" id="name" class="name-input" placeholder="Your name">
<label class="sr-only" for="q">Your question</label>
<textarea id="q" maxlength="500" placeholder="Type your question here..."></textarea>
<div id="charcount" aria-live="polite">0 / 500</div>
<button id="btn" onclick="submit()">Submit Question</button>
</div>
<div id="msg" class="msg" role="status" aria-live="polite" style="display:none"></div>
<a id="vote-link" href="/qa/vote" style="display:none;text-align:center;margin-top:12px;text-decoration:none;width:100%;min-height:48px;padding:14px;background:var(--qa-success);color:#fff;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;transition:background .2s;box-sizing:border-box">Vote on Questions</a>
</main>
<script>
const q=document.getElementById('q'),btn=document.getElementById('btn'),msg=document.getElementById('msg'),cc=document.getElementById('charcount'),nameField=document.getElementById('name');
let submitted=false,cooldown=30,votingOn=false;
const submitTime=parseInt(sessionStorage.getItem('qa_submit_time')||'0');
if(submitTime>0){
  const elapsed=Math.floor((Date.now()-submitTime)/1000);
  const savedCooldown=parseInt(sessionStorage.getItem('qa_cooldown')||'30');
  if(elapsed<savedCooldown){submitted=true;showThanks()}
  else{sessionStorage.removeItem('qa_submit_time');sessionStorage.removeItem('qa_cooldown')}
}
q.addEventListener('input',()=>{cc.textContent=q.value.length+' / 500'});
async function submit(){
  const text=q.value.trim();
  if(!text){show('Please enter a question','err');return}
  btn.disabled=true;btn.textContent='Submitting...';
  try{
    const name=nameField.value.trim();
    const r=await fetch('/api/qa/submit',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text,name})});
    if(r.ok){showThanks();sessionStorage.setItem('qa_submit_time',Date.now().toString());sessionStorage.setItem('qa_cooldown',cooldown.toString())}
    else if(r.status===429){showThanks()}
    else if(r.status===403){show('Q&A session is not active right now.','off')}
    else{const d=await r.json().catch(()=>({}));show(d.error||'Submission failed','err');btn.disabled=false;btn.textContent='Submit Question'}
  }catch(e){show('Network error. Please try again.','err');btn.disabled=false;btn.textContent='Submit Question'}
}
function showThanks(){
  document.getElementById('form-area').style.display='none';
  document.getElementById('page-title').style.display='none';
  document.getElementById('page-sub').style.display='none';
  msg.innerHTML='<strong>Thanks for submitting your question!</strong>';
  msg.className='msg ok';msg.style.display='block';
  submitted=true;
}
function show(t,c){msg.textContent=t;msg.className='msg '+c;msg.style.display='block'}
// Check session status periodically
async function checkStatus(){
  try{const r=await fetch('/api/qa/status');const d=await r.json();
    if(d.cooldownSeconds)cooldown=d.cooldownSeconds;
    votingOn=!!d.votingEnabled;
    const vl=document.getElementById('vote-link');if(vl)vl.style.display=votingOn?'block':'none';
    if(submitted){
      const st=parseInt(sessionStorage.getItem('qa_submit_time')||'0');
      if(st>0&&Math.floor((Date.now()-st)/1000)>=cooldown){
        submitted=false;sessionStorage.removeItem('qa_submit_time');sessionStorage.removeItem('qa_cooldown');
        document.getElementById('form-area').style.display='block';msg.style.display='none';
        document.getElementById('page-title').style.display='';document.getElementById('page-sub').style.display='';
        btn.disabled=false;btn.textContent='Submit Question';q.value='';
      }
      return;
    }
    if(!d.sessionActive){document.getElementById('form-area').style.display='none';document.getElementById('page-title').style.display='none';document.getElementById('page-sub').style.display='none';show('Q&A session is not active right now.','off')}
    else{document.getElementById('form-area').style.display='block';document.getElementById('page-title').style.display='';document.getElementById('page-sub').style.display='';if(msg.className.includes('off'))msg.style.display='none'}
  }catch(e){}
}
checkStatus();setInterval(checkStatus,5000);
</script>
</body>
</html>
""".trimIndent()

internal fun qaVotingPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Vote on Questions</title>
<style>
${qaSharedCss}
:root{--qa-bg:#f5f5f5;--qa-surface:#fff;--qa-ink:#1e1e2e;--qa-sub:#5f5f6b;--qa-muted:#616161;--qa-up-bg:#e3f2fd;--qa-down-bg:#ffebee;--qa-score-pos:#2e7d32;--qa-score-neg:#c62828}
@media(prefers-color-scheme:dark){:root{--qa-bg:#16161f;--qa-surface:#22222e;--qa-ink:#e8e8ef;--qa-sub:#a8a8b5;--qa-muted:#9a9aa6;--qa-up-bg:#12314a;--qa-down-bg:#3a1c1f;--qa-score-pos:#81c784;--qa-score-neg:#ef9a9a}}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;padding:16px}
.container{max-width:600px;margin:0 auto}
h1{font-size:24px;color:var(--qa-ink);text-align:center;margin-bottom:4px}
p.sub{color:var(--qa-sub);text-align:center;margin-bottom:24px;font-size:14px}
.question-card{background:var(--qa-surface);border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,.08);padding:20px;margin-bottom:12px;display:flex;align-items:flex-start;gap:16px}
.vote-btns{display:flex;flex-direction:column;align-items:center;gap:2px;min-width:44px}
.vote-btn{display:flex;align-items:center;justify-content:center;border:none;background:none;cursor:pointer;padding:6px;border-radius:8px;transition:background .2s,color .2s;width:44px;height:44px}
.vote-btn:hover{background:var(--qa-up-bg)}
.vote-btn.voted{color:var(--qa-primary);background:var(--qa-up-bg)}
.vote-btn:disabled{cursor:default}
.vote-arrow{font-size:18px;line-height:1;color:var(--qa-muted);transition:color .2s}
.vote-btn.voted .vote-arrow{color:var(--qa-primary)}
.vote-btn.down-voted{color:var(--qa-danger);background:var(--qa-down-bg)}
.vote-btn.down-voted .vote-arrow{color:var(--qa-danger)}
.vote-score{font-size:13px;font-weight:700;text-align:center;line-height:1;min-width:20px}
.q-content{flex:1;min-width:0}
.q-text{font-size:16px;color:var(--qa-ink);line-height:1.4;word-wrap:break-word}
.q-meta{font-size:12px;color:var(--qa-muted);margin-top:6px}
.msg{text-align:center;padding:16px;border-radius:8px;font-size:14px;margin-top:16px}
.msg.off{background:#fff3e0;color:#e65100}
.empty{text-align:center;color:var(--qa-muted);font-size:14px;margin-top:40px}
a.back{display:block;text-align:center;margin-top:20px;text-decoration:none;padding:14px;min-height:48px;background:var(--qa-primary);color:#fff;border-radius:12px;font-size:16px;font-weight:600;transition:background .2s}
a.back:hover{background:var(--qa-primary-hover)}
@media(prefers-color-scheme:dark){.msg.off{background:#3a2a12;color:#ffcc80}}
</style>
</head>
<body>
<main class="container">
<h1 id="page-title">Vote on Questions</h1>
<p class="sub" id="page-sub">Vote on the questions you'd like answered</p>
<div id="questions"></div>
<div id="msg" class="msg" role="status" aria-live="polite" style="display:none"></div>
<a class="back" href="/qa">&larr; Submit a question</a>
</main>
<script>
const questionsEl=document.getElementById('questions'),msgEl=document.getElementById('msg');
const voted=JSON.parse(sessionStorage.getItem('qa_voted')||'{}'); // {id: "up"|"down"}
let lastDataHash='';

async function loadQuestions(){
  try{
    const r=await fetch('/api/qa/approved');
    if(r.status===403){
      document.getElementById('page-title').style.display='none';
      document.getElementById('page-sub').style.display='none';
      questionsEl.innerHTML='';
      msgEl.textContent='Voting is not enabled right now.';
      msgEl.className='msg off';msgEl.style.display='block';
      lastDataHash='';
      return;
    }
    const data=await r.json();
    if(!Array.isArray(data)||data.length===0){
      const sr=await fetch('/api/qa/status');
      const sd=await sr.json();
      if(!sd.sessionActive){
        document.getElementById('page-title').style.display='none';
        document.getElementById('page-sub').style.display='none';
        questionsEl.innerHTML='';
        msgEl.textContent='Q&A session is not active right now.';
        msgEl.className='msg off';msgEl.style.display='block';
      } else {
        document.getElementById('page-title').style.display='';
        document.getElementById('page-sub').style.display='';
        msgEl.style.display='none';
        questionsEl.innerHTML='<div class="empty">No questions yet. Check back soon!</div>';
      }
      lastDataHash='';
      return;
    }
    // Only rebuild DOM if data changed (prevents wrong-question clicks during refresh)
    const newHash=data.map(q=>q.id).join(',');
    if(newHash===lastDataHash){
      // Just update vote states without rebuilding
      data.forEach(q=>{
        const dir=q.voted||voted[q.id]||null;
        updateBtns(q.id,dir);
      });
      return;
    }
    lastDataHash=newHash;
    document.getElementById('page-title').style.display='';
    document.getElementById('page-sub').style.display='';
    msgEl.style.display='none';
    questionsEl.innerHTML=data.map(q=>{
      const dir=q.voted||voted[q.id]||null;
      const score=(q.upvotes||0)-(q.downvotes||0);
      const scoreColor=score>0?'var(--qa-score-pos)':score<0?'var(--qa-score-neg)':'var(--qa-muted)';
      return '<div class="question-card" id="qc-'+q.id+'">'
        +'<div class="vote-btns">'
        +'<button class="vote-btn'+(dir==='up'?' voted':'')+'" id="up-'+q.id+'" aria-label="Upvote" aria-pressed="'+(dir==='up')+'" onclick="vote(\''+q.id+'\',\'up\')"><span class="vote-arrow" aria-hidden="true">&#9650;</span></button>'
        +'<span class="vote-score" id="vs-'+q.id+'" data-score="'+score+'" style="color:'+scoreColor+'">'+score+'</span>'
        +'<button class="vote-btn'+(dir==='down'?' down-voted':'')+'" id="dn-'+q.id+'" aria-label="Downvote" aria-pressed="'+(dir==='down')+'" onclick="vote(\''+q.id+'\',\'down\')"><span class="vote-arrow" aria-hidden="true">&#9660;</span></button>'
        +'</div>'
        +'<div class="q-content">'
        +'<div class="q-text">'+escHtml(q.text)+'</div>'
        +'</div></div>';
    }).join('');
  }catch(e){console.error(e)}
}

async function vote(id,dir){
  try{
    const prevDir=voted[id]||null;
    const r=await fetch('/api/qa/vote',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({questionId:id,direction:dir})});
    if(r.ok){
      const d=await r.json();
      const newDir=d.voted||null;
      if(newDir){voted[id]=newDir}else{delete voted[id]}
      sessionStorage.setItem('qa_voted',JSON.stringify(voted));
      updateBtns(id,newDir,prevDir);
    }
  }catch(e){}
}
function updateBtns(id,dir,prevDir){
  const up=document.getElementById('up-'+id);
  const dn=document.getElementById('dn-'+id);
  const vs=document.getElementById('vs-'+id);
  if(up){up.className='vote-btn'+(dir==='up'?' voted':'');up.setAttribute('aria-pressed',dir==='up')}
  if(dn){dn.className='vote-btn'+(dir==='down'?' down-voted':'');dn.setAttribute('aria-pressed',dir==='down')}
  if(vs){
    let s=parseInt(vs.dataset.score)||0;
    if(prevDir==='up')s--;else if(prevDir==='down')s++;
    if(dir==='up')s++;else if(dir==='down')s--;
    vs.dataset.score=s;vs.textContent=s;
    vs.style.color=s>0?'var(--qa-score-pos)':s<0?'var(--qa-score-neg)':'var(--qa-muted)';
  }
}

function escHtml(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}

loadQuestions();
setInterval(loadQuestions,5000);
</script>
</body>
</html>
""".trimIndent()

internal fun qaAdminPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Q&A Admin</title>
<style>
${qaSharedCss}
:root{--qa-bg:#1e1e2e;--qa-surface:#2a2a3e;--qa-ink:#e0e0e0;--qa-muted:#a0a0ad;--qa-border:#3b3b5c}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;padding:16px}
.header{display:flex;align-items:center;justify-content:space-between;padding:16px;margin-bottom:16px}
h1{font-size:22px}
.tabs{display:flex;gap:8px;margin-bottom:16px;padding:0 16px}
.tab{min-height:44px;padding:8px 20px;border-radius:8px;border:1px solid var(--qa-border);background:transparent;color:var(--qa-ink);cursor:pointer;font-size:14px;transition:background .2s,border-color .2s}
.tab.active{background:var(--qa-primary);border-color:var(--qa-primary);color:#fff}
.tab .count{background:rgba(255,255,255,.2);border-radius:10px;padding:1px 8px;margin-left:6px;font-size:12px}
.list{padding:0 16px}
.q{background:var(--qa-surface);border-radius:12px;padding:16px;margin-bottom:8px;display:flex;align-items:flex-start;gap:12px;flex-wrap:wrap}
.q.live{border:2px solid var(--qa-success)}
.q-text{flex:1;font-size:15px;line-height:1.4;min-width:150px}
.q-time{color:var(--qa-muted);font-size:12px;white-space:nowrap;padding-top:3px}
.q-label{font-size:11px;padding:2px 8px;border-radius:6px;font-weight:600}
.q-label.done{background:#42a5f5;color:#08233b}
.q-label.denied{background:var(--qa-danger);color:#fff}
.q-actions{display:flex;gap:6px;flex-wrap:wrap}
.btn{min-height:44px;padding:8px 14px;border:none;border-radius:8px;cursor:pointer;font-size:13px;font-weight:600;transition:background .2s}
.btn-approve{background:var(--qa-success);color:#fff}.btn-approve:hover{background:var(--qa-success-hover)}
.btn-deny{background:var(--qa-danger);color:#fff}.btn-deny:hover{background:var(--qa-danger-hover)}
.btn-live{background:var(--qa-primary);color:#fff}.btn-live:hover{background:var(--qa-primary-hover)}
.btn-done{background:#42a5f5;color:#08233b}.btn-done:hover{background:#1e88e5;color:#fff}
.btn-back{background:#ff9800;color:#3d2600}.btn-back:hover{background:#f57c00;color:#fff}
.btn-golive-confirm{background:var(--qa-primary);color:#fff;opacity:0.6}.btn-golive-confirm:hover{opacity:1}
.btn-edit{background:#ff9800;color:#3d2600}.btn-edit:hover{background:#f57c00;color:#fff}
.btn-del{background:#4a4a5e;color:#e0e0e0}.btn-del:hover{background:#5e5e77}
.edit-area{width:100%;display:flex;gap:6px;align-items:center;margin-top:8px}
.edit-area textarea{flex:1;background:var(--qa-bg);color:var(--qa-ink);border:2px solid var(--qa-primary);border-radius:8px;padding:8px;font-size:14px;font-family:inherit;resize:vertical;min-height:40px}
.edit-area .btn{white-space:nowrap}
.empty{text-align:center;padding:48px;color:var(--qa-muted);font-size:16px}
.status-bar{padding:8px 16px;background:var(--qa-success);color:#fff;border-radius:8px;margin:0 16px 16px;display:flex;align-items:center;justify-content:space-between}
.status-bar .btn{background:rgba(255,255,255,.2);color:#fff}
.add-bar{display:flex;gap:8px;padding:0 16px;margin-bottom:16px}
.add-bar input{flex:1;min-height:44px;background:var(--qa-surface);color:var(--qa-ink);border:1px solid var(--qa-border);border-radius:8px;padding:10px 14px;font-size:14px;font-family:inherit}
.add-bar input:focus{outline:none;border-color:var(--qa-primary)}
.login{max-width:360px;margin:80px auto;text-align:center}
.login input{width:100%;min-height:48px;background:var(--qa-surface);color:var(--qa-ink);border:2px solid var(--qa-border);border-radius:12px;padding:14px;font-size:16px;margin:16px 0;text-align:center}
.login input:focus{outline:none;border-color:var(--qa-primary)}
.login .btn{width:100%;padding:14px;font-size:16px}
.login .err{color:#ef9a9a;margin-top:8px;font-size:14px}
</style>
</head>
<body>
<div id="login-screen" class="login" style="display:none">
<h1>Q&A Admin</h1>
<p style="color:var(--qa-muted);margin-top:8px">Enter admin password to continue</p>
<label class="sr-only" for="pw-input">Admin password</label>
<input type="password" id="pw-input" aria-label="Admin password" placeholder="Password" onkeydown="if(event.key==='Enter')doLogin()">
<button class="btn btn-live" id="login-btn" onclick="doLogin()">Login</button>
<p id="connecting-msg" style="display:none;color:var(--qa-muted);margin-top:8px">Waiting for approval on the desktop…</p>
<div class="err" id="pw-err" style="display:none"></div>
</div>

<div id="main-app" style="display:none">
<div class="header">
<h1>Q&A Admin</h1>
<span id="status" role="status" aria-live="polite" style="font-size:13px;color:var(--qa-muted)">Connecting...</span>
</div>

<div id="display-bar" class="status-bar" style="display:none">
<span id="display-text">Displaying question...</span>
<button class="btn" onclick="clearDisplay()">Clear Display</button>
</div>

<div class="add-bar">
<label class="sr-only" for="add-input">Add a question</label>
<input type="text" id="add-input" aria-label="Add a question" placeholder="Add a question..." spellcheck="true" onkeydown="if(event.key==='Enter')addQ()">
<button class="btn btn-approve" onclick="addQ()">Add</button>
</div>

<div class="tabs" style="flex-wrap:wrap;gap:4px">
<button class="tab active" onclick="setFilter('ALL',this)">All <span class="count" id="cnt-all">0</span></button>
<button class="tab" onclick="setFilter('INCOMING',this)">Incoming <span class="count" id="cnt-incoming">0</span></button>
<button class="tab" onclick="setFilter('APPROVED',this)">Approved <span class="count" id="cnt-approved">0</span></button>
<button class="tab" onclick="setFilter('INCOMING_APPROVED',this)">Incoming+Approved <span class="count" id="cnt-ia">0</span></button>
<button class="tab" onclick="setFilter('DONE',this)">Done <span class="count" id="cnt-done">0</span></button>
<button class="tab" onclick="setFilter('DENIED',this)">Denied <span class="count" id="cnt-denied">0</span></button>
<button class="tab" id="sort-btn" onclick="toggleSort()" style="margin-left:auto">Sort: Time</button>
</div>

<div class="list" id="list"></div>
</div>

<script>
let questions=[],filter='ALL',sortBy='time',displayedId=null,editingId=null,authed=false;
let password=new URLSearchParams(window.location.search).get('password')||localStorage.getItem('qa_admin_pw')||'';
if(password){localStorage.setItem('qa_admin_pw',password);document.getElementById('pw-input').value=password;}
let deviceId=localStorage.getItem('qa_admin_device_id');
if(!deviceId){deviceId=(crypto.randomUUID?crypto.randomUUID():(Date.now()+'-'+Math.random().toString(36).slice(2)));localStorage.setItem('qa_admin_device_id',deviceId);}
const headers={'Content-Type':'application/json','X-Device-Id':deviceId};

function setHeaders(){
  if(password)headers['X-QA-Password']=password;
}
setHeaders();

// Sends /api/qa/auth and waits for the desktop operator to approve this device — mirrors the
// presentation remote's connection handshake, showing a "waiting for approval" state meanwhile.
async function attemptAuth(){
  const btn=document.getElementById('login-btn');
  const msg=document.getElementById('connecting-msg');
  const errEl=document.getElementById('pw-err');
  document.getElementById('login-screen').style.display='block';
  document.getElementById('main-app').style.display='none';
  errEl.style.display='none';
  btn.disabled=true;msg.style.display='block';
  try{
    const r=await fetch('/api/qa/auth',{method:'POST',headers});
    if(r.ok){authed=true;document.getElementById('login-screen').style.display='none';document.getElementById('main-app').style.display='block';load();checkStatus();return true}
    errEl.textContent=r.status===403?'Connection request denied':'Incorrect password';
    errEl.style.display='block';
    document.getElementById('pw-input').focus();
    return false
  }catch(e){
    errEl.textContent='Could not reach the app';
    errEl.style.display='block';
    return false
  }finally{
    btn.disabled=false;msg.style.display='none';
  }
}
async function doLogin(){
  password=document.getElementById('pw-input').value;
  localStorage.setItem('qa_admin_pw',password);
  headers['X-QA-Password']=password;
  await attemptAuth();
}

// Auto-connect on load — approval popup (if any) surfaces on the desktop while this awaits
attemptAuth();

function lockOut(){
  authed=false;password='';localStorage.removeItem('qa_admin_pw');
  document.getElementById('main-app').style.display='none';
  document.getElementById('login-screen').style.display='block';
  document.getElementById('pw-err').textContent='Session expired. Please log in again.';
  document.getElementById('pw-err').style.display='block';
  document.getElementById('pw-input').value='';document.getElementById('pw-input').focus();
}
let lastLoadSig='';
async function load(){
  if(!authed)return;
  try{
    const r=await fetch('/api/qa/questions',{headers});
    if(r.status===401){lockOut();return}
    if(r.ok)questions=await r.json();
    // Skip the full innerHTML rebuild when nothing relevant changed (prevents flicker/mis-taps).
    const sig=JSON.stringify(questions.map(q=>[q.id,q.status,q.text,q.upvotes,q.downvotes,q.submitterName]))+'|'+displayedId;
    if(sig===lastLoadSig)return;
    lastLoadSig=sig;
    if(!editingId)render();
  }catch(e){}
}

function render(){
  // Show/hide clear display bar
  const dbar=document.getElementById('display-bar');
  if(displayedId){
    const dq=questions.find(q=>q.id===displayedId);
    document.getElementById('display-text').textContent='Displaying: '+(dq?dq.text.substring(0,50):'...');
    dbar.style.display='flex';
  }else{dbar.style.display='none'}
  let filtered;
  if(filter==='ALL')filtered=questions;
  else if(filter==='INCOMING')filtered=questions.filter(q=>q.status==='PENDING');
  else if(filter==='APPROVED')filtered=questions.filter(q=>q.status==='APPROVED');
  else if(filter==='INCOMING_APPROVED')filtered=questions.filter(q=>q.status==='PENDING'||q.status==='APPROVED');
  else if(filter==='DONE')filtered=questions.filter(q=>q.status==='DONE');
  else if(filter==='DENIED')filtered=questions.filter(q=>q.status==='DENIED');
  else filtered=questions;
  const list=document.getElementById('list');
  document.getElementById('cnt-all').textContent=questions.length;
  document.getElementById('cnt-incoming').textContent=questions.filter(q=>q.status==='PENDING').length;
  document.getElementById('cnt-approved').textContent=questions.filter(q=>q.status==='APPROVED').length;
  document.getElementById('cnt-ia').textContent=questions.filter(q=>q.status==='PENDING'||q.status==='APPROVED').length;
  document.getElementById('cnt-done').textContent=questions.filter(q=>q.status==='DONE').length;
  document.getElementById('cnt-denied').textContent=questions.filter(q=>q.status==='DENIED').length;

  if(sortBy==='votes')filtered=[...filtered].sort((a,b)=>b.voteCount-a.voteCount);
  else filtered=[...filtered].sort((a,b)=>a.timestamp-b.timestamp);
  if(!filtered.length){list.innerHTML='<div class="empty">No questions</div>';return}
  list.innerHTML=filtered.map(q=>{
    const time=new Date(q.timestamp).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
    const isLive=q.id===displayedId;
    let label='';
    if(q.status==='DONE')label='<span class="q-label done">Done</span> ';
    else if(q.status==='DENIED')label='<span class="q-label denied">Denied</span> ';
    let actions='<button class="btn btn-edit" onclick="editQ(\''+q.id+'\')">Edit</button>';
    if(q.status==='PENDING'){
      actions+='<button class="btn btn-approve" onclick="action(\'approve\',\''+q.id+'\')">Approve</button><button class="btn btn-deny" onclick="action(\'deny\',\''+q.id+'\')">Deny</button>';
    }else if(q.status==='APPROVED'){
      actions+=(isLive?'':'<button class="btn btn-live" onclick="action(\'display\',\''+q.id+'\')">Go Live</button>')+'<button class="btn btn-done" onclick="action(\'done\',\''+q.id+'\')">Done</button><button class="btn btn-deny" onclick="action(\'deny\',\''+q.id+'\')">Deny</button>';
    }else if(q.status==='DONE'){
      actions+='<button class="btn btn-back" onclick="action(\'approve\',\''+q.id+'\')">Back to Incoming</button>';
      actions+='<button class="btn btn-golive-confirm" onclick="confirmGoLive(\''+q.id+'\')">Go Live</button>';
    }else if(q.status==='DENIED'){
      actions+='<button class="btn btn-approve" onclick="action(\'approve\',\''+q.id+'\')">Approve</button>';
      actions+='<button class="btn btn-golive-confirm" onclick="confirmGoLive(\''+q.id+'\')">Go Live</button>';
    }
    actions+='<button class="btn btn-del" onclick="del(\''+q.id+'\')">Del</button>';
    const nameTag=q.submitterName?'<span style="color:#888;font-size:12px">'+esc(q.submitterName)+':</span> ':'';
    const upTag=q.upvotes>0?'<span style="display:inline-block;background:#e3f2fd;color:#1565c0;font-size:11px;font-weight:700;padding:2px 4px;border-radius:4px;margin-right:2px">&#9650; '+q.upvotes+'</span>':'';
    const dnTag=q.downvotes>0?'<span style="display:inline-block;background:#ffebee;color:#c62828;font-size:11px;font-weight:700;padding:2px 4px;border-radius:4px;margin-right:4px">&#9660; '+q.downvotes+'</span>':'';
    const voteTag=upTag+dnTag;
    return '<div class="q'+(isLive?' live':'')+'" id="q-'+q.id+'"><span class="q-time">'+time+'</span><span class="q-text" id="qt-'+q.id+'">'+voteTag+label+nameTag+esc(q.text)+'</span><div class="q-actions">'+actions+'</div></div>';
  }).join('');
}

function esc(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}

let pendingConfirm=null;
function confirmGoLive(id){
  if(pendingConfirm===id){
    pendingConfirm=null;
    action('approve',id).then(()=>action('display',id));
    return;
  }
  pendingConfirm=id;
  const el=document.getElementById('q-'+id);
  if(el){const btns=el.querySelectorAll('.btn-golive-confirm');btns.forEach(b=>{b.textContent='Confirm Go Live?';b.style.opacity='1'})}
  setTimeout(()=>{if(pendingConfirm===id){pendingConfirm=null;load()}},3000);
}
async function action(act,id){
  try{const r=await fetch('/api/qa/questions/'+id+'/'+act,{method:'POST',headers});
    if(r.status===401){lockOut();return}
    if(r.ok&&act==='display')displayedId=id;
    if(r.ok&&(act==='done'||act==='deny'))if(displayedId===id)displayedId=null;
    load();
  }catch(e){}
}
async function del(id){
  if(!confirm('Delete this question? This cannot be undone.'))return;
  try{const r=await fetch('/api/qa/questions/'+id,{method:'DELETE',headers});if(r.status===401){lockOut();return}load()}catch(e){}
}
function editQ(id){
  const q=questions.find(q=>q.id===id);if(!q)return;
  editingId=id;
  const el=document.getElementById('qt-'+id);if(!el)return;
  el.innerHTML='<div class="edit-area"><textarea id="edit-'+id+'" spellcheck="true" aria-label="Edit question"></textarea><button class="btn btn-approve" onclick="saveEdit(\''+id+'\')">Save</button><button class="btn btn-del" onclick="cancelEdit()">Cancel</button></div>';
  const ta=document.getElementById('edit-'+id);if(ta){ta.value=q.text;ta.focus();ta.setSelectionRange(ta.value.length,ta.value.length)}
}
async function saveEdit(id){
  const ta=document.getElementById('edit-'+id);if(!ta)return;
  const text=ta.value.trim();if(!text)return;
  editingId=null;
  try{const r=await fetch('/api/qa/questions/'+id+'/edit',{method:'POST',headers,body:JSON.stringify({text})});if(r.status===401){lockOut();return}load()}catch(e){}
}
function cancelEdit(){editingId=null;render()}
async function addQ(){
  const inp=document.getElementById('add-input');const text=inp.value.trim();if(!text)return;
  inp.value='';
  try{const r=await fetch('/api/qa/add',{method:'POST',headers,body:JSON.stringify({text})});if(r.status===401){lockOut();return}load()}catch(e){}
}
async function clearDisplay(){
  try{await fetch('/api/qa/clear-display',{method:'POST',headers});displayedId=null;render();load()}catch(e){}
}
function setFilter(f,el){
  filter=f;
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  el.classList.add('active');
  render();
}
function toggleSort(){
  sortBy=sortBy==='time'?'votes':'time';
  document.getElementById('sort-btn').textContent='Sort: '+(sortBy==='votes'?'Votes':'Time');
  render();
}
async function checkStatus(){
  if(!authed)return;
  try{const r=await fetch('/api/qa/status');const d=await r.json();
    document.getElementById('status').textContent=d.sessionActive?'Session Active':'Session Inactive';
    document.getElementById('status').style.color=d.sessionActive?'#66bb6a':'#ef5350';
    const newDisplayed=d.displayedQuestionId||null;
    if(newDisplayed!==displayedId){displayedId=newDisplayed;render()}
  }catch(e){document.getElementById('status').textContent='Disconnected';document.getElementById('status').style.color='#ef5350'}
}

setInterval(()=>{if(authed)load()},3000);
setInterval(()=>{if(authed)checkStatus()},3000);
</script>
</body>
</html>
""".trimIndent()

internal fun presentationRemotePageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Presentation Remote</title>
<style>
*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#111;color:#fff;height:100dvh;display:flex;flex-direction:column;overflow:hidden;user-select:none}
#login{display:flex;flex-direction:column;align-items:center;justify-content:center;flex:1;padding:24px;gap:12px}
#login h2{font-size:20px;margin-bottom:4px}
#login p{font-size:14px;color:#888;text-align:center;max-width:280px}
#login input{width:100%;max-width:320px;padding:12px;border-radius:10px;border:1px solid #333;background:#222;color:#fff;font-size:16px;outline:none}
#login input:focus{border-color:#4fa3e3}
#login button{width:100%;max-width:320px;padding:13px;background:#4fa3e3;color:#fff;border:none;border-radius:10px;font-size:16px;font-weight:600;cursor:pointer}
#err{color:#e57373;font-size:13px;display:none}
#connecting-msg{color:#888;font-size:14px;display:none}
#app{display:none;flex-direction:column;flex:1;overflow:hidden}
#topbar{display:flex;align-items:center;padding:8px 10px;background:#1a1a1a;border-bottom:1px solid #222;gap:8px;flex-wrap:wrap;flex-shrink:0}
#counter{font-size:16px;font-weight:700;letter-spacing:1px;min-width:56px}
#blanked-badge{background:#e57373;color:#fff;font-size:10px;font-weight:700;padding:3px 8px;border-radius:12px;letter-spacing:.5px;display:none;flex-shrink:0}
#btns{display:flex;gap:6px;margin-left:auto}
.icon-btn{background:#2a2a2a;border:1px solid #333;color:#fff;border-radius:8px;padding:7px 10px;font-size:12px;cursor:pointer;font-weight:500;transition:background .15s;white-space:nowrap;touch-action:manipulation}
.icon-btn:active{background:#3a3a3a}
.icon-btn.active{background:#e57373;border-color:#e57373}
.icon-btn.active-play{background:#43a047;border-color:#43a047}
#not-live-bar{background:#1e3a5f;color:#aac4e8;font-size:12px;font-weight:500;padding:6px 16px;text-align:center;display:none;flex-shrink:0;border-bottom:1px solid #2a4a70;letter-spacing:.2px}
#notes-panel{background:#161616;color:#ddd;font-size:14px;line-height:1.5;padding:10px 14px;max-height:26vh;overflow-y:auto;border-bottom:1px solid #222;white-space:pre-wrap;flex-shrink:0}
#notes-panel.hidden{display:none}
#notes-text:empty::before{content:'No presenter notes for this slide';color:#666;font-style:italic}
#slides-area{flex:1;display:flex;flex-direction:row;overflow:hidden;min-height:0}
#cur-wrap{flex:2;position:relative;overflow:hidden;background:#000;display:flex;align-items:center;justify-content:center;border-right:1px solid #222}
#cur-img{max-width:100%;max-height:100%;object-fit:contain;display:block}
#blanked-overlay{position:absolute;inset:0;background:rgba(0,0,0,.5);display:none;align-items:flex-start;justify-content:flex-end;padding:8px;pointer-events:none}
#blanked-overlay span{background:#e57373;color:#fff;font-size:11px;font-weight:700;padding:3px 8px;border-radius:10px}
#next-wrap{flex:1;display:flex;flex-direction:column;overflow:hidden;background:#0d0d0d;min-width:0;cursor:pointer;touch-action:manipulation}
#next-label{font-size:9px;font-weight:700;letter-spacing:1px;color:#555;padding:6px 8px 4px;border-bottom:1px solid #1e1e1e;flex-shrink:0;text-transform:uppercase;pointer-events:none}
#next-img-wrap{flex:1;display:flex;align-items:center;justify-content:center;padding:8px;overflow:hidden;pointer-events:none}
#next-img{max-width:100%;max-height:100%;object-fit:contain;border-radius:4px;display:block}
#upload-status{font-size:11px;color:#aaa;text-align:center;padding:3px 12px;min-height:18px;flex-shrink:0;background:#1a1a1a;border-top:1px solid #222}
#strip-handle{height:6px;background:#222;cursor:row-resize;border-top:1px solid #333;flex-shrink:0;display:flex;align-items:center;justify-content:center}
#strip-handle::after{content:'';width:36px;height:2px;background:#444;border-radius:1px}
#strip-wrap{height:90px;background:#151515;flex-shrink:0;overflow-x:auto;overflow-y:hidden;display:flex;align-items:center;padding:8px 10px;gap:8px;-webkit-overflow-scrolling:touch;scrollbar-width:auto;scrollbar-color:#555 #222}
#strip-wrap::-webkit-scrollbar{height:6px}
#strip-wrap::-webkit-scrollbar-track{background:#222}
#strip-wrap::-webkit-scrollbar-thumb{background:#555;border-radius:3px}
.s-thumb{flex-shrink:0;cursor:pointer;border-radius:6px;overflow:hidden;border:2px solid #2a2a2a;transition:border-color .1s;position:relative;height:var(--thumb-h,66px);aspect-ratio:16/9;touch-action:manipulation}
.s-thumb.cur{border-color:#43a047}
.s-thumb img{width:100%;height:100%;object-fit:contain;display:block;background:#000}
.s-num{position:absolute;bottom:2px;right:3px;font-size:9px;font-weight:700;background:rgba(0,0,0,.65);color:#fff;border-radius:2px;padding:1px 3px;pointer-events:none}
#botbar{display:flex;align-items:center;padding:8px 10px;background:#1a1a1a;border-top:1px solid #222;gap:8px;flex-shrink:0}
.nav-btn{background:#484848;border:1px solid #6a6a6a;color:#fff;border-radius:10px;padding:14px;font-size:18px;font-weight:700;cursor:pointer;flex:1;text-align:center;transition:background .15s;line-height:1;touch-action:manipulation}
.nav-btn:active{background:#606060}
.hidden{display:none}
.grid-icon{display:inline-grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;width:14px;height:14px;vertical-align:middle}
.grid-icon i{background:#fff;border-radius:1px;display:block}
#botbar.expanded{flex:1;align-items:stretch;padding:12px;gap:12px}
#botbar.expanded .nav-btn{font-size:clamp(20px,8vw,44px);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;min-width:0;overflow:hidden}
#botbar.expanded .nav-chevron{font-size:1.3em;line-height:1}
</style>
</head>
<body>
<div id="login">
  <h2>Presentation Remote</h2>
  <div id="connecting-msg">Waiting for approval on the desktop…</div>
  <input id="pw-input" type="password" placeholder="Password (if set)" autocomplete="current-password">
  <button id="connect-btn" onclick="doLogin()">Connect</button>
  <span id="err">Incorrect password</span>
</div>
<div id="app">
  <div id="topbar">
    <div id="counter">– / –</div>
    <div id="blanked-badge">BLANKED</div>
    <div id="btns">
      <button class="icon-btn" id="hide-btn" onclick="toggleHideSlides()" title="Hide slides" aria-label="Hide slides"><span class="grid-icon"><i></i><i></i><i></i><i></i></span></button>
      <button class="icon-btn" id="blank-btn" onclick="toggleBlank()">Blank</button>
      <button class="icon-btn" id="play-btn" onclick="togglePlay()">Auto ▶ 5s</button>
      <button class="icon-btn" id="loop-btn" onclick="toggleLoop()">Loop</button>
      <button class="icon-btn" id="notes-btn" onclick="toggleNotes()" title="Presenter notes" aria-label="Presenter notes">📝 Notes</button>
      <button class="icon-btn" id="upload-btn" onclick="document.getElementById('upload-input').click()">⬆ Upload</button>
      <input type="file" id="upload-input" accept=".pdf,.ppt,.pptx,.key" style="display:none">
    </div>
  </div>
  <div id="not-live-bar">⚠ Presentation not on screen — enable from the desktop app</div>
  <div id="notes-panel" class="hidden"><div id="notes-text"></div></div>
  <div id="slides-area">
    <div id="cur-wrap">
      <img id="cur-img" alt="" draggable="false">
      <div id="blanked-overlay"><span>BLANKED</span></div>
    </div>
    <div id="next-wrap" onclick="goSlide(state.index+1)">
      <div id="next-label">Next Slide</div>
      <div id="next-img-wrap"><img id="next-img" alt="" draggable="false"></div>
    </div>
  </div>
  <div id="upload-status"></div>
  <div id="strip-handle"></div>
  <div id="strip-wrap"></div>
  <div id="botbar">
    <button class="nav-btn" onclick="goSlide(state.index-1)"><span class="nav-chevron">‹</span><span class="nav-label">Backward</span></button>
    <button class="nav-btn" onclick="goSlide(state.index+1)"><span class="nav-label">Forward</span><span class="nav-chevron">›</span></button>
  </div>
</div>
<script>
let state={id:'',index:0,total:0,frozen:false,isPlaying:false,isLive:false,autoScrollInterval:5,looping:true,notes:''};
let fetchFailCount=0;
let offlineMode=false;
let slidesHidden=localStorage.getItem('remote_slides_hidden')==='1';
let notesOverride=localStorage.getItem('remote_notes_visible'); // '1'/'0' = explicit user choice, null = auto (show when notes present)
let password=new URLSearchParams(location.search).get('password')||sessionStorage.getItem('remote_pw')||'';
if(password){sessionStorage.setItem('remote_pw',password);document.getElementById('pw-input').value=password;}
let deviceId=localStorage.getItem('remote_device_id');
if(!deviceId){deviceId=(crypto.randomUUID?crypto.randomUUID():(Date.now()+'-'+Math.random().toString(36).slice(2)));localStorage.setItem('remote_device_id',deviceId);}
const headers={'Content-Type':'application/json','X-Device-Id':deviceId};
if(password)headers['X-Presentation-Password']=password;
function slideUrl(i){return'/api/presentations/'+state.id+'/slides/'+i+(password?('?apiKey='+encodeURIComponent(password)):'');}
let stripBuilt=false;
function loadImg(img,src){
  if(img._want===src)return;
  img._want=src;img._tries=0;
  img.onerror=function(){
    if(img._want!==src)return;
    if(img._tries<6){
      img._tries++;
      setTimeout(()=>{if(img._want===src)img.src=src+(src.includes('?')?'&':'?')+'r='+img._tries;},800*img._tries);
    }
  };
  img.src=src;
}
function buildStrip(){
  const wrap=document.getElementById('strip-wrap');
  wrap.innerHTML='';
  for(let i=0;i<state.total;i++){
    const d=document.createElement('div');
    d.className='s-thumb'+(i===state.index?' cur':'');
    d.id='st-'+i;
    const idx=i;
    d.onclick=()=>goSlide(idx);
    const im=document.createElement('img');im.loading='lazy';loadImg(im,slideUrl(i));
    const span=document.createElement('span');span.className='s-num';span.textContent=i+1;
    d.appendChild(im);d.appendChild(span);
    wrap.appendChild(d);
  }
  stripBuilt=true;
}
function updateStripCurrent(){
  const prev=document.querySelector('.s-thumb.cur');if(prev)prev.classList.remove('cur');
  const cur=document.getElementById('st-'+state.index);
  if(cur){cur.classList.add('cur');cur.scrollIntoView({inline:'nearest',block:'nearest',behavior:'smooth'});}
}
function applyHideSlides(){
  document.getElementById('slides-area').classList.toggle('hidden',slidesHidden);
  document.getElementById('strip-wrap').classList.toggle('hidden',slidesHidden);
  document.getElementById('strip-handle').classList.toggle('hidden',slidesHidden);
  document.getElementById('upload-status').classList.toggle('hidden',slidesHidden);
  if(slidesHidden)document.getElementById('not-live-bar').style.display='none';
  document.getElementById('botbar').classList.toggle('expanded',slidesHidden);
  const hb=document.getElementById('hide-btn');
  hb.classList.toggle('active',slidesHidden);
  hb.title=slidesHidden?'Show slides':'Hide slides';hb.setAttribute('aria-label',hb.title);
}
function toggleHideSlides(){slidesHidden=!slidesHidden;localStorage.setItem('remote_slides_hidden',slidesHidden?'1':'0');applyHideSlides();}
function isNotesVisible(){return notesOverride===null?!!state.notes:notesOverride==='1';}
function applyNotesVisibility(){
  const v=isNotesVisible();
  document.getElementById('notes-panel').classList.toggle('hidden',!v);
  document.getElementById('notes-btn').classList.toggle('active',v);
}
function toggleNotes(){
  notesOverride=isNotesVisible()?'0':'1';
  localStorage.setItem('remote_notes_visible',notesOverride);
  applyNotesVisibility();
}
function updateUI(){
  document.getElementById('counter').textContent=state.total>0?(state.index+1)+' / '+state.total:'– / –';
  const fb=document.getElementById('blank-btn');
  fb.classList.toggle('active',state.frozen);fb.textContent=state.frozen?'Unblank':'Blank';
  document.getElementById('blanked-badge').style.display=state.frozen?'inline-block':'none';
  document.getElementById('blanked-overlay').style.display=state.frozen?'flex':'none';
  const pb=document.getElementById('play-btn');
  pb.classList.toggle('active-play',state.isPlaying);pb.textContent=state.isPlaying?('⏸ Auto '+state.autoScrollInterval+'s'):('Auto ▶ '+state.autoScrollInterval+'s');
  document.getElementById('loop-btn').classList.toggle('active-play',state.looping);
  document.getElementById('not-live-bar').style.display=(state.total>0&&!state.isLive)?'block':'none';
  if(state.id){
    loadImg(document.getElementById('cur-img'),slideUrl(state.index));
    const ni=document.getElementById('next-img');
    if(state.index+1<state.total){loadImg(ni,slideUrl(state.index+1));ni.style.display='block';}
    else{ni.style.display='none';}
  }else{
    const ci=document.getElementById('cur-img');ci._want='';ci.src='';
    const ni=document.getElementById('next-img');ni._want='';ni.src='';ni.style.display='none';
  }
  if(!stripBuilt||document.getElementById('strip-wrap').children.length!==state.total){buildStrip();}
  else{updateStripCurrent();}
  applyHideSlides();
  document.getElementById('notes-text').textContent=state.notes||'';
  applyNotesVisibility();
}
async function fetchStatus(){
  try{
    const r=await fetch('/api/presentation-remote/status');const d=await r.json();
    fetchFailCount=0;
    if(offlineMode){if(d.enabled){location.reload();}else{offlineMode=false;showDisabled();}return;}
    if(!d.enabled){showDisabled();return;}
    const changed=d.id!==state.id||d.index!==state.index||d.total!==state.total||d.frozen!==state.frozen||d.isPlaying!==state.isPlaying||d.isLive!==state.isLive||d.autoScrollInterval!==state.autoScrollInterval||d.looping!==state.looping||d.notes!==state.notes;
    state={...state,...d};if(changed)updateUI();
  }catch(e){fetchFailCount++;if(!offlineMode&&fetchFailCount>=2)showOffline();}
}
function showDisabled(){
  document.getElementById('app').style.display='none';
  document.getElementById('login').style.display='flex';
  document.getElementById('login').innerHTML='<h2>Remote control is disabled</h2><p>Enable it in the app — this page will auto-connect.</p>';
  startPollingForEnable();
}
function showOffline(){
  offlineMode=true;
  document.getElementById('app').style.display='none';
  document.getElementById('login').style.display='flex';
  document.getElementById('login').innerHTML='<h2>App not available</h2><p>Connection lost — the app may be closed or the network is unavailable.</p>';
}
function startPollingForEnable(){
  (async function poll(){
    try{
      const r=await fetch('/api/presentation-remote/status');const d=await r.json();
      if(d.enabled){location.reload();return;}
    }catch(_){}
    setTimeout(poll,3000);
  })();
}
async function doLogin(){
  password=document.getElementById('pw-input').value;
  sessionStorage.setItem('remote_pw',password);headers['X-Presentation-Password']=password;
  const btn=document.getElementById('connect-btn');
  const errEl=document.getElementById('err');
  errEl.style.display='none';btn.disabled=true;btn.textContent='Waiting for approval…';
  try{
    const r=await fetch('/api/presentation-remote/auth',{method:'POST',headers});
    if(r.ok){
      const st=await fetch('/api/presentation-remote/status').then(r=>r.json()).catch(()=>null);
      if(st)state={...state,...st};
      document.getElementById('login').style.display='none';document.getElementById('app').style.display='flex';
      stripBuilt=false;updateUI();startWs();setInterval(fetchStatus,2500);
      return;
    }
    errEl.textContent=r.status===403?'Connection request denied':'Incorrect password';
    errEl.style.display='block';
  }finally{btn.disabled=false;btn.textContent='Connect';}
}
async function post(path){try{return await fetch(path,{method:'POST',headers});}catch(e){return null;}}
function toggleBlank(){state.frozen=!state.frozen;updateUI();post('/api/presentation-remote/freeze');}
function togglePlay(){post('/api/presentation-remote/play-pause');}
function toggleLoop(){post('/api/presentation-remote/loop');}
function goSlide(i){if(i<0||i>=state.total)return;post('/api/presentation-remote/goto/'+i);}
document.getElementById('upload-input').addEventListener('change',async function(){
  const file=this.files[0];if(!file)return;
  const btn=document.getElementById('upload-btn');const status=document.getElementById('upload-status');
  btn.textContent='Uploading…';btn.disabled=true;status.textContent='Uploading '+file.name+'…';
  const reader=new FileReader();
  reader.onload=async function(e){
    try{
      const r=await fetch('/api/presentation-remote/upload',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({name:file.name,data:e.target.result})});
      if(r.ok){status.textContent='✓ Loaded — slides will appear shortly';}
      else{const d=await r.json().catch(()=>({}));status.textContent='✗ '+(d.error||'Upload failed');}
    }catch(err){status.textContent='✗ Network error';}
    btn.textContent='⬆ Upload';btn.disabled=false;
  };
  reader.readAsDataURL(file);this.value='';
});
let touchX=0;
document.getElementById('cur-wrap').addEventListener('touchstart',e=>{touchX=e.changedTouches[0].clientX;},{passive:true});
document.getElementById('cur-wrap').addEventListener('touchend',e=>{
  const dx=e.changedTouches[0].clientX-touchX;
  if(Math.abs(dx)>50){dx<0?goSlide(state.index+1):goSlide(state.index-1);}
},{passive:true});
let stripResizing=false,stripY0=0,stripH0=0;
const sh=document.getElementById('strip-handle');
const sw=document.getElementById('strip-wrap');
function onSRStart(y){stripResizing=true;stripY0=y;stripH0=sw.offsetHeight;}
function onSRMove(y){if(!stripResizing)return;const dh=stripY0-y;const newH=Math.max(52,Math.min(window.innerHeight*0.75,stripH0+dh));sw.style.height=newH+'px';sw.style.setProperty('--thumb-h',Math.max(36,newH-24)+'px');}
sh.addEventListener('mousedown',e=>{onSRStart(e.clientY);e.preventDefault();});
document.addEventListener('mousemove',e=>{onSRMove(e.clientY);});
document.addEventListener('mouseup',()=>{stripResizing=false;});
sh.addEventListener('touchstart',e=>{onSRStart(e.touches[0].clientY);},{passive:true});
document.addEventListener('touchmove',e=>{if(stripResizing){onSRMove(e.touches[0].clientY);e.preventDefault();}},{passive:false});
document.addEventListener('touchend',()=>{stripResizing=false;});
function startWs(){
  const proto=location.protocol==='https:'?'wss':'ws';
  const ws=new WebSocket(proto+'://'+location.host+'/ws');
  ws.onmessage=e=>{
    try{
      const msg=JSON.parse(e.data);
      if(msg.type==='presentation_slide_changed'){
        const d=JSON.parse(msg.payload);
        const newPres=d.id!==state.id;
        state={...state,id:d.id,index:d.index,total:d.total,isPlaying:d.isPlaying,isLive:d.isLive||false,notes:d.notes||''};
        if(newPres)stripBuilt=false;
        updateUI();
      }else if(msg.type==='presentation_freeze_changed'){
        const d=JSON.parse(msg.payload);state={...state,frozen:d.frozen};updateUI();
      }else if(msg.type==='presentation_auto_scroll_changed'){
        const d=JSON.parse(msg.payload);state={...state,autoScrollInterval:d.autoScrollInterval};updateUI();
      }else if(msg.type==='presentation_live_changed'){
        const d=JSON.parse(msg.payload);state={...state,isLive:d.isLive};updateUI();
      }else if(msg.type==='presentation_looping_changed'){
        const d=JSON.parse(msg.payload);state={...state,looping:d.looping};updateUI();
      }
    }catch(_){}
  };
  ws.onclose=()=>{setTimeout(startWs,2000);};
}
(async()=>{
  try{
    const r=await fetch('/api/presentation-remote/status');const d=await r.json();
    if(!d.enabled){
      document.getElementById('login').innerHTML='<h2>Remote control is disabled</h2><p>Enable it in the app — this page will auto-connect.</p>';
      startPollingForEnable();return;
    }
    if(!d.passwordRequired||password){
      const pwInput=document.getElementById('pw-input');const connectBtn=document.getElementById('connect-btn');
      const connMsg=document.getElementById('connecting-msg');
      pwInput.style.display='none';connectBtn.style.display='none';connMsg.style.display='block';
      const authR=await fetch('/api/presentation-remote/auth',{method:'POST',headers});
      pwInput.style.display='';connectBtn.style.display='';connMsg.style.display='none';
      if(!authR.ok){
        pwInput.value=password;
        const errEl=document.getElementById('err');
        errEl.textContent=authR.status===403?'Connection request denied':'Incorrect password';
        errEl.style.display='block';
        return;
      }
      state={...state,...d};
      document.getElementById('login').style.display='none';document.getElementById('app').style.display='flex';
      updateUI();startWs();setInterval(fetchStatus,2500);
    }
  }catch(_){}
})();
</script>
</body>
</html>
""".trimIndent()
