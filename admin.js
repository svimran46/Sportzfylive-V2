const API="https://sportzfylive.svimranmy.workers.dev";
let adminToken=localStorage.getItem("sportzfy_admin_token")||"";
let adminSession=localStorage.getItem("sportzfy_admin_session")||"";
let channels=[];
let allMatches=[];

function esc(v){return String(v??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#039;")}

async function api(path,options={}){
  const response=await fetch(API+path,{...options,headers:{
    "Accept":"application/json",
    "Content-Type":"application/json",
    ...(adminSession ? {"X-Admin-Session":adminSession} : {"X-Admin-Token":adminToken}),
    ...(options.headers||{})
  }});
  let data=null;try{data=await response.json()}catch(_){}
  if(!response.ok)throw new Error(data?.error||`HTTP ${response.status}`);
  return data;
}

async function loginAdmin(){
  const input=document.getElementById("adminToken");
  adminToken=input.value.trim();
  if(!adminToken)return;
  try{
    const response=await fetch(API+"/api/admin/login",{method:"POST",headers:{
      "Content-Type":"application/json",
      "X-Admin-Token":adminToken
    }});
    const data=await response.json();
    if(!response.ok)throw new Error(data?.error||"Authentication failed");
    adminSession=data.session;
    localStorage.setItem("sportzfy_admin_session",adminSession);
    localStorage.removeItem("sportzfy_admin_token");
    adminToken="";
    document.getElementById("adminLogin").style.display="none";
    await Promise.all([loadChannels(),loadMatches()]);
  }catch(e){
    adminToken="";
    adminSession="";
    localStorage.removeItem("sportzfy_admin_token");
    localStorage.removeItem("sportzfy_admin_session");
    document.getElementById("loginError").textContent="Authentication failed.";
  }
}

async function loadChannels(){
  const box=document.getElementById("channelList");
  try{channels=await fetch(API+"/api/channels").then(r=>r.json());renderChannels()}
  catch(e){box.innerHTML='<div class="error">Unable to load channels.</div>'}
}

function renderChannels(){
  const box=document.getElementById("channelList");
  const q=(document.getElementById("channelSearch")?.value||"").trim().toLowerCase();
  const selected=new Set([...document.querySelectorAll("#channelList input:checked")].map(x=>x.value));
  const list=channels.filter(c=>String(c.name||"").toLowerCase().includes(q)||String(c.genre||"").toLowerCase().includes(q));
  box.innerHTML=list.length?list.map(c=>`<label class="channel-item"><input type="checkbox" value="${esc(c.id)}" ${selected.has(String(c.id))?"checked":""}><span>${esc(c.name)}</span></label>`).join(""):'<div style="padding:15px;color:#8f99a8">No channels found.</div>';
}

function selectedChannelIds(){return [...document.querySelectorAll("#channelList input:checked")].map(x=>x.value)}

async function createMatch(){
  const status=document.getElementById("formStatus"),title=document.getElementById("title").value.trim();
  if(!title){status.className="error";status.textContent="Match title is required.";return}
  status.className="status";status.textContent="Saving...";
  try{
    await api("/api/admin/matches",{method:"POST",body:JSON.stringify({
      title,
      category:document.getElementById("category").value,
      startTime:document.getElementById("startTime").value?new Date(document.getElementById("startTime").value).toISOString():"",
      status:document.getElementById("status").value,
      poster:document.getElementById("poster").value.trim(),
      description:document.getElementById("description").value.trim(),
      channelIds:selectedChannelIds()
    })});
    status.textContent="Match created successfully.";
    clearForm();
    await loadMatches();
    showPage("matches");
  }catch(e){status.className="error";status.textContent=e.message}
}

function clearForm(){
  ["title","startTime","poster","description"].forEach(id=>document.getElementById(id).value="");
  document.getElementById("status").value="scheduled";
  document.querySelectorAll("#channelList input").forEach(x=>x.checked=false);
}

function renderMatchList(box,matches){
  box.innerHTML=matches.length?matches.map(m=>`<div class="match"><div><b>${esc(m.title)}</b><br><small>${esc(m.category)} • ${esc(m.status)} • ${esc(m.startTime||"No date")}</small></div><button onclick="deleteMatch('${esc(m.id)}')">Delete</button></div>`).join(""):'<div style="padding:15px;color:#8f99a8">No matches created yet.</div>';
}

async function loadMatches(){
  const box=document.getElementById("matches");
  if(box)box.innerHTML="Loading...";
  try{
    allMatches=await api("/api/admin/matches");
    const count=document.getElementById("matchCount");
    if(count)count.textContent=`${allMatches.length} match${allMatches.length===1?"":"es"}`;
    if(box)renderMatchList(box,allMatches);
    const allBox=document.getElementById("allMatches");
    if(allBox)renderMatchList(allBox,allMatches);
  }catch(e){
    if(box)box.innerHTML='<div class="error">Unable to load matches: '+esc(e.message)+'</div>';
    const allBox=document.getElementById("allMatches");
    if(allBox)allBox.innerHTML='<div class="error">Unable to load matches: '+esc(e.message)+'</div>';
  }
}

async function syncStreamedMatches(){
  const status=document.getElementById("streamedSyncStatus");
  if(!status)return;
  status.className="status";
  status.textContent="Syncing today's matches from Streamed...";
  try{
    const result=await api("/api/admin/sync-streamed",{method:"POST"});
    status.textContent="Sync complete. Streamed: "+result.streamedCount+"; created: "+result.created+"; updated: "+result.updated+".";
    await loadMatches();
  }catch(e){
    status.className="error";
    status.textContent=e.message;
  }
}

async function autoLinkMatches(){
  const status=document.getElementById("autoLinkStatus");
  if(!status)return;
  status.className="status";
  status.textContent="Linking local channels to Streamed source mappings...";
  try{
    const result=await api("/api/admin/auto-link",{method:"POST"});
    const linked=result.results.filter(item=>item.addedChannelIds?.length).length;
    const matched=result.results.filter(item=>item.streamedMatchId).length;
    status.textContent="Done. Matched "+matched+"/"+result.processed+" matches; added channels to "+linked+".";
    await loadMatches();
  }catch(e){
    status.className="error";
    status.textContent=e.message;
  }
}

async function deleteMatch(id){
  if(!confirm("Delete this match?"))return;
  try{await api("/api/admin/matches/"+encodeURIComponent(id),{method:"DELETE"});await loadMatches()}
  catch(e){alert(e.message)}
}

function showPage(page){
  document.querySelectorAll(".page").forEach(x=>x.classList.add("hidden"));
  const target=document.getElementById("page-"+page);
  if(target)target.classList.remove("hidden");
  document.querySelectorAll(".menu div").forEach(x=>x.classList.toggle("active",x.dataset.page===page));
  const titles={dashboard:"Admin Dashboard",matches:"Matches",streams:"Streams",settings:"Settings"};
  document.getElementById("pageTitle").textContent=titles[page]||"Admin Dashboard";
  document.getElementById("sidebar").classList.remove("open");
  if(page==="matches"){loadChannels();loadMatches()}
}

function toggleSidebar(){document.getElementById("sidebar").classList.toggle("open")}

document.querySelectorAll(".menu div").forEach(item=>item.addEventListener("click",()=>showPage(item.dataset.page)));
document.getElementById("channelSearch").addEventListener("input",renderChannels);

if(adminSession){
  api("/api/admin/matches").then(()=>{
    document.getElementById("adminLogin").style.display="none";
    loadChannels();loadMatches();
  }).catch(()=>{
    localStorage.removeItem("sportzfy_admin_session");
    adminSession="";
    document.getElementById("adminLogin").style.display="flex";
  });
}else{
  document.getElementById("adminLogin").style.display="flex";
}
