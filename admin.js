const API="https://sportzfylive.svimranmy.workers.dev";
let adminToken=localStorage.getItem("sportzfy_admin_token")||"",adminSession=localStorage.getItem("sportzfy_admin_session")||"",channels=[],allMatches=[],matchPage=1;

function esc(v){return String(v??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#039;")}
async function api(path,options={}){const r=await fetch(API+path,{...options,headers:{"Accept":"application/json","Content-Type":"application/json",...(adminSession?{"X-Admin-Session":adminSession}:{"X-Admin-Token":adminToken}),...(options.headers||{})}});let d=null;try{d=await r.json()}catch(_){}if(!r.ok)throw new Error(d?.error||`HTTP ${r.status}`);return d}

async function loginAdmin(){const input=document.getElementById("adminToken");adminToken=input.value.trim();if(!adminToken)return;try{const r=await fetch(API+"/api/admin/login",{method:"POST",headers:{"Content-Type":"application/json","X-Admin-Token":adminToken}}),d=await r.json();if(!r.ok)throw new Error(d?.error||"Authentication failed");adminSession=d.session;localStorage.setItem("sportzfy_admin_session",adminSession);localStorage.removeItem("sportzfy_admin_token");adminToken="";document.getElementById("adminLogin").style.display="none";await Promise.all([loadChannels(),loadMatches(),loadContent()])}catch(e){document.getElementById("loginError").textContent="Authentication failed."}}

async function loadChannels(){try{channels=await fetch(API+"/api/channels").then(r=>r.json());renderChannels();renderSources()}catch(e){const b=document.getElementById("channelList");if(b)b.innerHTML='<div class="error">Unable to load TV sources.</div>'}}
function renderChannels(){const b=document.getElementById("channelList");if(!b)return;const q=(document.getElementById("channelSearch")?.value||"").toLowerCase();const selected=new Set([...document.querySelectorAll("#channelList input:checked")].map(x=>x.value));const list=channels.filter(c=>String(c.name||"").toLowerCase().includes(q)||String(c.genre||"").toLowerCase().includes(q));b.innerHTML=list.length?list.map(c=>`<label class="channel-item"><input type="checkbox" value="${esc(c.id)}" ${selected.has(String(c.id))?"checked":""}><span>${esc(c.name)} <span class="pill">${esc(c.country||"")}</span></span></label>`).join(""):'<div style="padding:15px;color:#748399">No TV sources found.</div>'}
function selectedChannelIds(){return [...document.querySelectorAll("#channelList input:checked")].map(x=>x.value)}

function clearForm(){["title","startTime","poster","description","editMatchId"].forEach(id=>document.getElementById(id).value="");document.getElementById("status").value="scheduled";document.getElementById("category").value="FOOTBALL";document.getElementById("matchFormTitle").textContent="Add Match Manually";document.querySelectorAll("#channelList input").forEach(x=>x.checked=false);document.getElementById("formStatus").textContent=""}
function openCreateMatch(){clearForm();showPage("matches")}
function editMatch(id){const m=allMatches.find(x=>String(x.id)===String(id));if(!m)return;showPage("matches");document.getElementById("matchFormTitle").textContent="Edit Match";document.getElementById("editMatchId").value=m.id;document.getElementById("title").value=m.title||"";document.getElementById("category").value=m.category||"OTHER";document.getElementById("status").value=m.status||"scheduled";document.getElementById("startTime").value=m.startTime?new Date(m.startTime).toISOString().slice(0,16):"";document.getElementById("poster").value=m.poster||"";document.getElementById("description").value=m.description||"";setTimeout(()=>document.querySelectorAll("#channelList input").forEach(x=>x.checked=(m.channelIds||[]).map(String).includes(String(x.value))),150)}
async function saveMatch(){const s=document.getElementById("formStatus"),title=document.getElementById("title").value.trim();if(!title){s.className="error";s.textContent="Match title is required.";return}s.className="status";s.textContent="Saving...";const id=document.getElementById("editMatchId").value;const body={title,category:document.getElementById("category").value,startTime:document.getElementById("startTime").value?new Date(document.getElementById("startTime").value).toISOString():"",status:document.getElementById("status").value,poster:document.getElementById("poster").value.trim(),description:document.getElementById("description").value.trim(),channelIds:selectedChannelIds()};try{await api(id?"/api/admin/matches/"+encodeURIComponent(id):"/api/admin/matches",{method:id?"PUT":"POST",body:JSON.stringify(body)});s.textContent=id?"Match updated successfully.":"Match created successfully.";await loadMatches();if(!id)clearForm()}catch(e){s.className="error";s.textContent=e.message}}

function renderMatchList(){const q=(document.getElementById("matchSearch")?.value||"").trim().toLowerCase(),filtered=allMatches.filter(m=>[m.title,m.category,m.status].some(v=>String(v||"").toLowerCase().includes(q))),size=Number(document.getElementById("matchPageSize")?.value||10),pages=Math.max(1,Math.ceil(filtered.length/size));matchPage=Math.min(matchPage,pages);const items=filtered.slice((matchPage-1)*size,matchPage*size);const html=items.length?items.map(m=>`<div class="match"><div><div><b>${esc(m.title)}</b></div><div style="color:#748399;font-size:11px;margin-top:4px">${esc(m.category)} • ${esc(m.status)} • ${esc(m.startTime?new Date(m.startTime).toLocaleString():"No date")}</div></div><div class="match-actions"><button class="secondary" onclick="editMatch('${esc(m.id)}')">Edit</button><button class="danger" onclick="deleteMatch('${esc(m.id)}')">Delete</button></div></div>`).join(""):'<div style="padding:15px;color:#748399">No matches found.</div>';const a=document.getElementById("allMatches"),d=document.getElementById("matches");if(a)a.innerHTML=html;if(d)d.innerHTML=html;const count=document.getElementById("matchCount");if(count)count.textContent=`${allMatches.length} total match${allMatches.length===1?"":"es"}`;renderPagination(pages);populateFeatured()}
function renderPagination(pages){const p=document.getElementById("matchPagination");if(!p)return;p.innerHTML="";const add=(t,n,dis,active)=>{const b=document.createElement("button");b.textContent=t;b.disabled=dis;if(active)b.classList.add("active");b.onclick=()=>{matchPage=n;renderMatchList()};p.appendChild(b)};add("First",1,matchPage===1);add("Previous",Math.max(1,matchPage-1),matchPage===1);const st=Math.max(1,Math.min(matchPage-2,pages-4)),en=Math.min(pages,st+4);for(let n=st;n<=en;n++)add(String(n),n,false,n===matchPage);add("Next",Math.min(pages,matchPage+1),matchPage===pages);add("Last",pages,matchPage===pages)}
async function loadMatches(){try{allMatches=await api("/api/admin/matches");renderMatchList()}catch(e){const msg='<div class="error">Unable to load matches: '+esc(e.message)+'</div>';document.getElementById("matches").innerHTML=msg;document.getElementById("allMatches").innerHTML=msg}}

function editChannel(id){const c=channels.find(x=>String(x.id)===String(id));if(!c)return;document.getElementById("channelEditId").value=c.id;document.getElementById("channelName").value=c.name||"";document.getElementById("channelLogo").value=c.logo||"";document.getElementById("channelGenre").value=c.genre||"";document.getElementById("channelCountry").value=c.country||"";document.getElementById("channelBroadcastName").value=c.broadcastName||""}
function clearChannelForm(){["channelEditId","channelName","channelLogo","channelGenre","channelCountry","channelBroadcastName"].forEach(id=>document.getElementById(id).value="");document.getElementById("channelStatus").textContent=""}
function renderSources(){const b=document.getElementById("sourceList");if(!b)return;const q=(document.getElementById("sourceSearch")?.value||"").toLowerCase();const list=channels.filter(c=>[c.name,c.genre,c.country,c.broadcastName].some(v=>String(v||"").toLowerCase().includes(q)));b.innerHTML=list.length?list.map(c=>`<div class="match"><div><b>${esc(c.name)}</b><div style="color:#748399;font-size:11px">${esc(c.genre||"")} • ${esc(c.country||"")}</div></div><div class="match-actions"><button class="secondary" onclick="editChannel('${esc(c.id)}')">Edit</button><button class="danger" onclick="deleteChannel('${esc(c.id)}')">Delete</button></div></div>`).join(""):'<div style="padding:15px;color:#748399">No TV sources found.</div>'}
async function saveChannel(){const name=document.getElementById("channelName").value.trim();if(!name){document.getElementById("channelStatus").textContent="Name is required.";return}const id=document.getElementById("channelEditId").value||crypto.randomUUID(),item={id,name,logo:document.getElementById("channelLogo").value.trim(),genre:document.getElementById("channelGenre").value.trim(),country:document.getElementById("channelCountry").value.trim().toUpperCase(),broadcastName:document.getElementById("channelBroadcastName").value.trim()};const i=channels.findIndex(c=>String(c.id)===String(id));if(i>=0)channels[i]={...channels[i],...item};else channels.push(item);try{await api("/api/channels",{method:"POST",body:JSON.stringify(channels)});document.getElementById("channelStatus").textContent="TV source saved.";renderSources();renderChannels();clearChannelForm()}catch(e){document.getElementById("channelStatus").className="error";document.getElementById("channelStatus").textContent=e.message}}
async function deleteChannel(id){if(!confirm("Delete this TV source?"))return;channels=channels.filter(c=>String(c.id)!==String(id));try{await api("/api/channels",{method:"POST",body:JSON.stringify(channels)});renderSources();renderChannels()}catch(e){alert(e.message)}}

async function loadContent(){try{const d=await api("/api/admin/site-content");document.getElementById("noticeEnabled").checked=!!d.notice?.enabled;document.getElementById("noticeText").value=d.notice?.text||"";document.getElementById("adEnabled").checked=!!d.banner?.enabled;document.getElementById("adImage").value=d.banner?.image||"";document.getElementById("adLink").value=d.banner?.link||"";populateFeatured(d.featuredMatchId||"")}catch(e){}}
function populateFeatured(selected){const s=document.getElementById("featuredMatch");if(!s)return;const old=selected||s.value;s.innerHTML='<option value="">No featured match</option>'+allMatches.map(m=>`<option value="${esc(m.id)}">${esc(m.title)}</option>`).join("");s.value=old||""}
async function saveContent(){const s=document.getElementById("contentStatus");try{await api("/api/admin/site-content",{method:"PUT",body:JSON.stringify({notice:{enabled:document.getElementById("noticeEnabled").checked,text:document.getElementById("noticeText").value.trim()},banner:{enabled:document.getElementById("adEnabled").checked,image:document.getElementById("adImage").value.trim(),link:document.getElementById("adLink").value.trim()},featuredMatchId:document.getElementById("featuredMatch").value})});s.className="status";s.textContent="Website content saved successfully."}catch(e){s.className="error";s.textContent=e.message}}

async function syncStreamedMatches(){const s=document.getElementById("streamedSyncStatus");s.textContent="Syncing...";try{const r=await api("/api/admin/sync-streamed",{method:"POST"});s.textContent=`Sync complete. Streamed: ${r.streamedCount}; created: ${r.created}; updated: ${r.updated}.`;await loadMatches()}catch(e){s.className="error";s.textContent=e.message}}
async function syncBroadcasts(){
  const s=document.getElementById("broadcastSyncStatus");
  const box=document.getElementById("broadcastSyncResults");
  s.className="status";
  s.textContent="Syncing broadcasts and automatically creating matchable TV sources...";
  if(box) box.innerHTML="";
  try{
    const r=await api("/api/admin/sync-broadcasts",{method:"POST"});
    if(r.configured===false){
      s.className="error";
      s.textContent=r.message||"Broadcast API is not configured.";
      return;
    }

    s.textContent=`Batch complete. Processed: ${r.processed??0}; broadcasts found: ${r.broadcastCount??0}; matches with broadcasts: ${r.matchedMatches??0}; matches linked: ${r.linkedMatches??0}; new TV sources: ${r.createdChannels??0}; remaining: ${r.remaining??0}.`;

    if(box){
      const rows=Array.isArray(r.diagnostics)?r.diagnostics:[];
      const items=rows.map(x=>{
        const broadcasts=Array.isArray(x.broadcasts)?x.broadcasts:[];
        const added=Array.isArray(x.addedChannelIds)?x.addedChannelIds:[];
        return `<div class="preview" style="margin-top:8px">
          <b>${esc(x.title||"Untitled match")}</b>
          <div style="margin-top:5px">Status: ${esc(x.status||"unknown")}</div>
          <div>Broadcasts: ${broadcasts.length?broadcasts.map(b=>esc((b.name||"")+(b.country?" • "+b.country:""))).join(", "):"None found"}</div>
          <div>TV sources linked/created: ${added.length}</div>
        </div>`;
      }).join("");
      box.innerHTML=rows.length?items:`<div class="preview">No diagnostic rows returned for this batch.</div>`;
    }

    await loadChannels();
    await loadMatches();
  }catch(e){
    s.className="error";
    s.textContent=e.message;
  }
}
async function autoLinkMatches(){const s=document.getElementById("autoLinkStatus");s.textContent="Running...";try{const r=await api("/api/admin/auto-link",{method:"POST"});s.textContent=`Done. Matched ${r.results.filter(x=>x.streamedMatchId).length}/${r.processed}.`;await loadMatches()}catch(e){s.className="error";s.textContent=e.message}}
async function deleteMatch(id){if(!confirm("Delete this match?"))return;try{await api("/api/admin/matches/"+encodeURIComponent(id),{method:"DELETE"});await loadMatches()}catch(e){alert(e.message)}}

function showPage(page){document.querySelectorAll(".page").forEach(x=>x.classList.add("hidden"));document.getElementById("page-"+page)?.classList.remove("hidden");document.querySelectorAll(".menu div").forEach(x=>x.classList.toggle("active",x.dataset.page===page));const t={dashboard:"Dashboard",matches:"Matches",channels:"TV Sources",settings:"Website Content",sync:"Sync & Automation"};document.getElementById("pageTitle").textContent=t[page]||"Dashboard";document.getElementById("sidebar").classList.remove("open");if(page==="matches"){loadChannels();loadMatches()}if(page==="channels")loadChannels();if(page==="settings"){loadContent();populateFeatured()}}
function toggleSidebar(){document.getElementById("sidebar").classList.toggle("open")}
document.querySelectorAll(".menu div").forEach(x=>x.addEventListener("click",()=>showPage(x.dataset.page)));
document.getElementById("channelSearch")?.addEventListener("input",renderChannels);document.getElementById("matchSearch")?.addEventListener("input",()=>{matchPage=1;renderMatchList()});document.getElementById("matchPageSize")?.addEventListener("change",()=>{matchPage=1;renderMatchList()});document.getElementById("sourceSearch")?.addEventListener("input",renderSources);
if(adminSession){api("/api/admin/matches").then(()=>{document.getElementById("adminLogin").style.display="none";loadChannels();loadMatches();loadContent()}).catch(()=>{localStorage.removeItem("sportzfy_admin_session");adminSession="";document.getElementById("adminLogin").style.display="flex"})}else document.getElementById("adminLogin").style.display="flex";