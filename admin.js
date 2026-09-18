const API="https://sportzfylive.svimranmy.workers.dev";
let adminToken=sessionStorage.getItem("sportzfy_admin_token")||"";
let channels=[];

function esc(v){return String(v??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#039;")}
async function api(path,options={}){
  const response=await fetch(API+path,{...options,headers:{
    "Accept":"application/json",
    "Content-Type":"application/json",
    "X-Admin-Token":adminToken,
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
  sessionStorage.setItem("sportzfy_admin_token",adminToken);
  try{
    await api("/api/admin/matches");
    document.getElementById("adminLogin").style.display="none";
    await Promise.all([loadChannels(),loadMatches()]);
  }catch(e){
    adminToken="";sessionStorage.removeItem("sportzfy_admin_token");
    document.getElementById("loginError").textContent="Authentication failed.";
  }
}
async function loadChannels(){
  const box=document.getElementById("channelList");
  try{channels=await api("/api/channels");renderChannels()}
  catch(e){box.innerHTML='<div class="error">Unable to load channels.</div>'}
}
function renderChannels(){
  const box=document.getElementById("channelList");
  const q=document.getElementById("channelSearch").value.trim().toLowerCase();
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
      title,category:document.getElementById("category").value,
      startTime:document.getElementById("startTime").value?new Date(document.getElementById("startTime").value).toISOString():"",
      status:document.getElementById("status").value,
      poster:document.getElementById("poster").value.trim(),
      description:document.getElementById("description").value.trim(),
      channelIds:selectedChannelIds()
    })});
    status.textContent="Match created successfully.";clearForm();await loadMatches();
  }catch(e){status.className="error";status.textContent=e.message}
}
function clearForm(){
  ["title","startTime","poster","description"].forEach(id=>document.getElementById(id).value="");
  document.getElementById("status").value="scheduled";
  document.querySelectorAll("#channelList input").forEach(x=>x.checked=false);
}
async function loadMatches(){
  const box=document.getElementById("matches");box.innerHTML="Loading...";
  try{
    const matches=await api("/api/admin/matches");
    document.getElementById("matchCount").textContent=`${matches.length} match${matches.length===1?"":"es"}`;
    box.innerHTML=matches.length?matches.map(m=>`<div class="match"><div><b>${esc(m.title)}</b><br><small>${esc(m.category)} • ${esc(m.status)} • ${esc(m.startTime||"No date")}</small></div><button onclick="deleteMatch('${esc(m.id)}')">Delete</button></div>`).join(""):'<div style="padding:15px;color:#8f99a8">No matches created yet.</div>';
  }catch(e){box.innerHTML='<div class="error">Unable to load matches.</div>'}
}
async function deleteMatch(id){
  if(!confirm("Delete this match?"))return;
  try{await api("/api/admin/matches/"+encodeURIComponent(id),{method:"DELETE"});await loadMatches()}catch(e){alert(e.message)}
}
document.getElementById("channelSearch").addEventListener("input",renderChannels);
if(adminToken)loginAdmin();
