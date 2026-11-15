const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('src/client/java/de/galaxushd/mpsqcamera/CinemaBrowserManager.java','utf8');
const script=source.match(/executeJavaScript\("""([\s\S]*?)"""/)[1];
let checks=0;
for(const host of ['www.mixelpixel-squidgame.net','mixelpixel-squidgame.net','example.com']){
 let shield=true,callback=null,observers=0;
 const ctx={location:{hostname:host,pathname:'/mpsq-player.html'},window:{},document:{getElementById:id=>id==='caption-shield'&&shield?{remove(){shield=false}}:null},MutationObserver:class{constructor(cb){callback=cb;observers++}observe(){}}};
 vm.runInNewContext(script,ctx);
 if(host==='example.com'){assert(shield);assert.equal(observers,0);checks+=2;continue}
 assert(!shield);checks++;
 vm.runInNewContext(script,ctx);assert.equal(observers,1);checks++;
 shield=true;callback();assert(!shield);checks++;
}
assert(!/playVideo|seekTo|pauseVideo|setTimeout/.test(script));checks++;
console.log(`PASS: ${checks} player overlay regression checks (existing, late insertion, idempotence, origin, playback untouched)`);
