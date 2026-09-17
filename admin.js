const API =
"https://streamedproxy.svimranmy.workers.dev";


let selectedMatch=null;



async function loadMatches(){

const box=document.getElementById("matches");

box.innerHTML="Loading...";


try{


const response =
await fetch(
API+"/api/matches/all-today"
);


const data =
await response.json();



const matches =
Array.isArray(data)
?
data
:
data.matches;



box.innerHTML="";



matches.forEach(match=>{


const item=document.createElement("div");


item.className="match";


item.innerHTML=`

<div>

<b>${match.title}</b>

<br>

<small>
${match.category || ""}
</small>

</div>


<button>
Manage
</button>

`;



item.querySelector("button")
.onclick=()=>selectMatch(match);



box.appendChild(item);



});


}

catch(error){

console.error(error);

box.innerHTML=
"Error loading matches";

}


}




async function selectMatch(match){

selectedMatch=match;


document
.getElementById("streamBox")
.classList
.remove("hidden");



document
.getElementById("matchTitle")
.innerText=
match.title;



await saveMatch();


await loadStreams();


}







async function saveMatch(){


await fetch(

API+"/admin/matches",

{

method:"POST",

headers:{

"Content-Type":
"application/json"

},

body:JSON.stringify({

streamed_id:selectedMatch.id,

title:selectedMatch.title,

category:selectedMatch.category,

date:selectedMatch.date,

poster:selectedMatch.poster || ""

})

}

);


}








async function loadStreams(){


const box=
document.getElementById("streams");


box.innerHTML="Loading...";



try{


const response=
await fetch(

API+
"/admin/matches/"
+
selectedMatch.id
+
"/streams"

);



const streams=
await response.json();



box.innerHTML="";



streams.forEach(stream=>{


box.innerHTML +=`

<div class="stream">

<b>
${stream.name}
</b>

<br>

${stream.language}

|

${stream.quality}

</div>

`;


});


}

catch(e){

box.innerHTML="No streams found";

}


}








function openForm(){

document
.getElementById("form")
.classList
.remove("hidden");

}








async function saveStream(){


await fetch(

API+
"/admin/matches/"
+
selectedMatch.id
+
"/streams",

{

method:"POST",

headers:{

"Content-Type":
"application/json"

},


body:JSON.stringify({

name:
document.getElementById("name").value,


language:
document.getElementById("language").value,


quality:
document.getElementById("quality").value,


url:
document.getElementById("url").value


})


}

);



document
.getElementById("form")
.classList
.add("hidden");



loadStreams();


}




loadMatches();
