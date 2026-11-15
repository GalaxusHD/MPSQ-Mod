const fs=require('fs'),vm=require('vm'),assert=require('assert');
const src=fs.readFileSync('src/client/java/de/galaxushd/mpsqcamera/CinemaAudioManager.java','utf8');
const script=src.match(/return """([\s\S]*?)"""\.replace/)[1];
for(const gain of [0,.125,1]){
 const media={volume:1},messages=[];
 const frame={src:'https://www.youtube.com/embed/test',get contentDocument(){throw Error('cross origin')},contentWindow:{postMessage:(message,origin)=>messages.push([JSON.parse(message),origin])}};
 const document={querySelectorAll:s=>s==='video,audio'?[media]:[frame]};
 vm.runInNewContext(script.replaceAll('__GAIN__',String(gain)),{document,URL});
 assert.equal(media.volume,gain);assert.equal(messages[0][0].args[0],gain*100);assert.equal(messages[0][1],'https://www.youtube.com');
}
console.log('PASS: 9 volume checks including mute and cross-origin YouTube');
