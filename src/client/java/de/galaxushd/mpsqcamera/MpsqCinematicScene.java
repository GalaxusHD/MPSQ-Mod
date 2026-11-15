package de.galaxushd.mpsqcamera;

import java.util.*;

/** Editor data independent of the menu and Minecraft renderer. Times are seconds. */
public final class MpsqCinematicScene {
    public String id=UUID.randomUUID().toString(), name="Neue Szene", dimension="";
    public boolean bars=true;
    public int pathVersion=1;
    public String triggerZone="";
    public boolean onMasterEnter, redstoneEnabled, once=true;
    public static String uniqueName(String value, Collection<MpsqCinematicScene> scenes, String ownId){
        String name=value.trim();
        if(name.isEmpty()||name.length()>64)throw new IllegalArgumentException("Name: 1–64 Zeichen.");
        if(scenes.stream().anyMatch(s->!s.id.equals(ownId)&&s.name.equalsIgnoreCase(name)))throw new IllegalArgumentException("Dieser Szenenname ist bereits vergeben.");
        return name;
    }
    public Npc npc=new Npc();
    /** Preserve editor references when a validation or disk write fails. */
    void restoreFrom(MpsqCinematicScene saved) {
        id=saved.id;name=saved.name;dimension=saved.dimension;bars=saved.bars;triggerZone=saved.triggerZone;
        pathVersion=saved.pathVersion;onMasterEnter=saved.onMasterEnter;redstoneEnabled=saved.redstoneEnabled;once=saved.once;
        points=saved.points;events=saved.events;
        if(npc==null||saved.npc==null)npc=saved.npc;else npc.restoreFrom(saved.npc);
    }

    public static final class Npc {
        public String name="NPC",category="mob",type="minecraft:villager",url="";
        public String[] equipment={"minecraft:air","minecraft:air","minecraft:air","minecraft:air","minecraft:air","minecraft:air"};
        public String cosmeticModel="",cosmeticBone="h_head";
        public double cosmeticX,cosmeticY,cosmeticZ;
        public float cosmeticScale=1,cosmeticRX,cosmeticRY,cosmeticRZ;
        public boolean glow=false;
        public String rankTag="";
        public boolean placed;
        public double x,y,z;
        public float yaw,headYaw,pitch;
        public String look="FIXED",linkedNpc="";
        public double movementStart;
        public String scenesClip="idle",visibility="CINEMATIC",scenesTask="none";
        public float scenesScale=1;
        public boolean scenesMotionEnabled=true,scenesMotionLoop,closedRoute;
        public String scenesDirection="FORWARD";
        void restoreFrom(Npc n){cosmeticModel=n.cosmeticModel;cosmeticBone=n.cosmeticBone;cosmeticX=n.cosmeticX;cosmeticY=n.cosmeticY;cosmeticZ=n.cosmeticZ;cosmeticScale=n.cosmeticScale;cosmeticRX=n.cosmeticRX;cosmeticRY=n.cosmeticRY;cosmeticRZ=n.cosmeticRZ;glow=n.glow;rankTag=n.rankTag;scenesTask=n.scenesTask;visibility=n.visibility;scenesMotionEnabled=n.scenesMotionEnabled;scenesMotionLoop=n.scenesMotionLoop;closedRoute=n.closedRoute;scenesDirection=n.scenesDirection;scenesClip=n.scenesClip;scenesScale=n.scenesScale;name=n.name;category=n.category;type=n.type;url=n.url;equipment=n.equipment;placed=n.placed;x=n.x;y=n.y;z=n.z;yaw=n.yaw;headYaw=n.headYaw;pitch=n.pitch;look=n.look;linkedNpc=n.linkedNpc;movementStart=n.movementStart;route=n.route;actions=n.actions;recording=n.recording;recordingId=n.recordingId;}

        public String recordingId="";
        public java.util.List<MpsqMotionCapture.Frame> recording=new ArrayList<>();
        public Waypoint[] route=new Waypoint[21];
        public List<NpcAction> actions=new ArrayList<>();
        public void validate(){
            if(recording==null)recording=new ArrayList<>();MpsqMotionCapture.validate(recording);
            if(cosmeticModel==null)cosmeticModel="";if(cosmeticBone==null)cosmeticBone="h_head";
            if(!Set.of("h_head","body","left_arm","right_arm","left_leg","right_leg").contains(cosmeticBone)||!Double.isFinite(cosmeticX)||!Double.isFinite(cosmeticY)||!Double.isFinite(cosmeticZ)||Math.abs(cosmeticX)>32||Math.abs(cosmeticY)>32||Math.abs(cosmeticZ)>32||!Float.isFinite(cosmeticScale)||cosmeticScale<.1||cosmeticScale>3||!Float.isFinite(cosmeticRX)||!Float.isFinite(cosmeticRY)||!Float.isFinite(cosmeticRZ))throw new IllegalArgumentException("Ungültige Accessoire-Position.");
            if(rankTag==null)rankTag="";
            if(visibility==null)visibility="CINEMATIC";if(!Set.of("ALL","CINEMATIC").contains(visibility))throw new IllegalArgumentException("Ungültige NPC-Sichtbarkeit.");if(scenesClip==null)scenesClip="idle";if(!Float.isFinite(scenesScale)||scenesScale<.25||scenesScale>3)throw new IllegalArgumentException("Ungültige NPC-Größe.");
            if(linkedNpc==null)linkedNpc="";if(route==null)route=new Waypoint[21];if(actions==null)actions=new ArrayList<>();if(look==null)look="FIXED";
            if(route.length==5)route=Arrays.copyOf(route,21);
            if(route.length!=21||actions.size()>128||!Set.of("FIXED","PLAYER","CAMERA","PLAYER_HEAD","CAMERA_HEAD").contains(look)||!Double.isFinite(movementStart)||movementStart<0||movementStart>86400||!Float.isFinite(headYaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Ungültige NPC-Bewegung/Rotation.");
            for(int i=0;i<route.length;i++)if(route[i]!=null){var p=route[i];if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Double.isFinite(p.duration)||p.duration<.1||p.duration>3600)throw new IllegalArgumentException("Ungültiger NPC-Wegpunkt.");if(i>0&&(route[0]==null||(i!=4&&route[i-1]==null)))throw new IllegalArgumentException("Wegpunkte müssen nacheinander gesetzt werden.");}
            if(actions.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Ungültige NPC-Aktion.");
            actions.sort(Comparator.comparingDouble(a->a.time));double end=-1;
            for(var a:actions){if(a==null||a.clip==null||a.clip.isBlank()||a.clip.length()>128||a.look==null||!Set.of("DEFAULT","FIXED","PLAYER","CAMERA","PLAYER_HEAD","CAMERA_HEAD").contains(a.look)||!Double.isFinite(a.time)||a.time<0||a.time>86400||!Double.isFinite(a.duration)||a.duration<.1||a.duration>3600)throw new IllegalArgumentException("Ungültige NPC-Aktion.");if(a.time<end)throw new IllegalArgumentException("NPC-Aktionen dürfen sich nicht überlappen.");end=a.time+a.duration;}
        }
        public double duration(){if(recording!=null&&!recording.isEmpty())return movementStart+recording.getLast().time;double end=movementStart;for(int i=1;route!=null&&i<route.length;i++)if(route!=null&&route[i]!=null)end+=route[i].duration;return Math.max(route!=null&&route[0]!=null?end+closingDuration():0,actions==null?0:actions.stream().mapToDouble(a->a.time+a.duration).max().orElse(0));}
        public Waypoint position(double time){
            if(!scenesMotionEnabled)return new Waypoint(x,y,z,1);
            return position(time,scenesMotionLoop,scenesDirection);
        }
        public double closingDuration(){
            if(!closedRoute||route==null||route[0]==null)return 0;
            Waypoint last=route[0];double distance=0,seconds=0;
            for(int i=1;i<route.length;i++)if(route[i]!=null){var p=route[i];distance+=distance(last,p);seconds+=p.duration;last=p;}
            return last==route[0]?0:Math.max(.1,distance(last,route[0])*(distance>1e-6?seconds/distance:1/1.4));
        }
        private static double distance(Waypoint a,Waypoint b){return Math.sqrt(Math.pow(a.x-b.x,2)+Math.pow(a.y-b.y,2)+Math.pow(a.z-b.z,2));}
        public Waypoint position(double time,boolean loop,String direction){
            var capture=MpsqMotionCapture.sample(recording,Math.max(0,time-movementStart),loop,direction);if(capture!=null)return new Waypoint(capture.x(),capture.y(),capture.z(),1);
            var points=new ArrayList<Waypoint>();if(route!=null)for(var p:route)if(p!=null)points.add(p);
            if(points.isEmpty())return new Waypoint(x,y,z,1);if(points.size()==1)return points.getFirst();
            double closing=closingDuration();boolean closed=closing>0;
            if(closed){var first=points.getFirst();points.add(new Waypoint(first.x,first.y,first.z,closing));}
            double length=points.stream().skip(1).mapToDouble(p->p.duration).sum();
            double age=Math.max(0,time-movementStart),cycle="BOTH".equals(direction)?2*length:length;
            double t=loop?age%cycle:Math.min(age,cycle);
            if("REVERSE".equals(direction))t=length-t;else if("BOTH".equals(direction)&&t>length)t=2*length-t;
            double start=0;for(int i=1;i<points.size();i++){
                var a=points.get(i-1);var b=points.get(i);
                if(t<start+b.duration){
                    var previous=i>1?points.get(i-2):closed?points.get(points.size()-2):a;
                    var next=i+1<points.size()?points.get(i+1):closed?points.get(1):b;
                    double f=Math.max(0,(t-start)/b.duration),pd=i==1&&closed?closing:previous==a?b.duration:a.duration,nd=next==b?b.duration:next.duration;
                    return new Waypoint(curve(previous.x,a.x,b.x,next.x,f,pd,b.duration,nd),curve(previous.y,a.y,b.y,next.y,f,pd,b.duration,nd),curve(previous.z,a.z,b.z,next.z,f,pd,b.duration,nd),b.duration);
                }start+=b.duration;
            }return points.getLast();
        }
        public NpcAction action(double time){if(actions!=null)for(var a:actions)if(time>=a.time&&time<a.time+a.duration)return a;return null;}

    }
    public static float snapRotation(float angle){return Math.round(angle/15f)*15f;}
    public static final class Waypoint {
        public double x,y,z,duration=3;
        public Waypoint(){}
        public Waypoint(double x,double y,double z,double duration){this.x=x;this.y=y;this.z=z;this.duration=duration;}
    }
    public static final class NpcAction {public String clip="idle",look="DEFAULT";public double time,duration=2;}
    public List<Point> points=new ArrayList<>();
    public List<Event> events=new ArrayList<>();
    public static final class Event {
        public String id=UUID.randomUUID().toString(),type="CHAT",value="",extra="";
        public double time,duration=3,x,y,z;
        public float volume=1,pitch=1;
        public void validate(){
            if(id==null||type==null||!Set.of("CHAT","ACTIONBAR","TITLE","COMMAND","SOUND","MEDIA","PARTICLE","FADE","DIALOGUE").contains(type)||value==null||extra==null||value.length()>2048||extra.length()>2048)throw new IllegalArgumentException("Ungültiges Ereignis.");
            if(!Double.isFinite(time)||time<0||time>86400||!Double.isFinite(duration)||duration<0.1||duration>3600||!Float.isFinite(volume)||volume<0||volume>1||!Float.isFinite(pitch)||pitch<0.25||pitch>4||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Zeit, Dauer, Lautstärke oder Tonhöhe ungültig.");
            if(type.equals("COMMAND")&&(value.isBlank()||value.contains("\n")||value.contains("\r")||value.length()>256))throw new IllegalArgumentException("Bitte genau einen Befehl eingeben (max. 256 Zeichen).");
            if(type.equals("MEDIA")&&!value.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Bitte eine lokale oder hochgeladene Sound-ID eingeben.");
        }
    }
    public static final class Point {
        public double time,x,y,z;
        public float yaw,pitch;
        public String transition="LINEAR";
        public Point(){}
        public Point(double time,double x,double y,double z,float yaw,float pitch){this.time=time;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
    }
    public double duration(){return Math.max(npc!=null&&npc.placed?npc.duration():0,Math.max(points.stream().mapToDouble(p->p.time).max().orElse(0),events==null?0:events.stream().mapToDouble(e->e.time+e.duration).max().orElse(0)));}
    public void validate(){
        if(id==null||name==null||name.isBlank()||name.length()>64||points==null||dimension==null||triggerZone==null||triggerZone.length()>64)throw new IllegalArgumentException("Ungültige Szene.");
        if(npc==null)npc=new Npc();npc.validate();
        if(events==null)events=new ArrayList<>();
        if(events.size()>512)throw new IllegalArgumentException("Maximal 512 Ereignisse pro Szene.");
        var eventIds=new HashSet<String>();for(var e:events){if(e==null)throw new IllegalArgumentException("Leeres Ereignis.");e.validate();if(!eventIds.add(e.id))throw new IllegalArgumentException("Doppelte Ereignis-ID.");}
        if(npc.name==null||npc.name.length()>64||npc.category==null||!Set.of("mob","npc_skin_normal","npc_skin_slim","npc_model").contains(npc.category)||npc.type==null||npc.url==null||npc.equipment==null||npc.equipment.length!=6)throw new IllegalArgumentException("Ungültige NPC-Einstellungen.");
        for(String item:npc.equipment)if(item==null||item.isBlank())throw new IllegalArgumentException("Ungültiges NPC-Item.");
        if(!Double.isFinite(npc.x)||!Double.isFinite(npc.y)||!Double.isFinite(npc.z)||!Float.isFinite(npc.yaw))throw new IllegalArgumentException("Ungültige NPC-Position.");
        points.sort(Comparator.comparingDouble(p->p.time));double previous=-1;
        for(Point p:points){
            if(!Double.isFinite(p.time)||p.time<0||p.time>86400||p.time<=previous||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Float.isFinite(p.yaw)||!Float.isFinite(p.pitch)||Math.abs(p.pitch)>90||!Set.of("LINEAR","SMOOTH","CUT").contains(p.transition))throw new IllegalArgumentException("Punkte brauchen eindeutige Zeiten und gültige Positionen.");
            previous=p.time;
        }
    }
    public Point sample(double time){
        if(points.isEmpty())return null;
        Point a=points.getFirst();if(time<=a.time)return a;
        for(int i=1;i<points.size();i++){
            Point b=points.get(i);if(time<b.time){
                if("CUT".equals(b.transition))return a;
                double t=(time-a.time)/(b.time-a.time);
                float angle=(b.yaw-a.yaw)%360;if(angle>180)angle-=360;if(angle<-180)angle+=360;
                if("SMOOTH".equals(b.transition)){
                    Point prev=i>1&&"SMOOTH".equals(a.transition)?points.get(i-2):a;
                    Point next=i+1<points.size()&&"SMOOTH".equals(points.get(i+1).transition)?points.get(i+1):b;
                    double d=b.time-a.time,pd=prev==a?d:a.time-prev.time,nd=next==b?d:next.time-b.time;
                    double endYaw=a.yaw+angle;
                    return new Point(time,curve(prev.x,a.x,b.x,next.x,t,pd,d,nd),curve(prev.y,a.y,b.y,next.y,t,pd,d,nd),curve(prev.z,a.z,b.z,next.z,t,pd,d,nd),
                        (float)curve(a.yaw-angleDelta(prev.yaw,a.yaw),a.yaw,endYaw,endYaw+angleDelta(b.yaw,next.yaw),t,pd,d,nd),
                        (float)Math.max(-90,Math.min(90,curve(prev.pitch,a.pitch,b.pitch,next.pitch,t,pd,d,nd))));
                }
                return new Point(time,a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t,(float)(a.yaw+angle*t),(float)(a.pitch+(b.pitch-a.pitch)*t));
            }a=b;
        }return a;
    }
    static double angleDelta(double a,double b){double d=(b-a)%360;return d>180?d-360:d< -180?d+360:d;}
    /** Time-weighted Catmull-Rom tangents round corners, including axis-aligned legs. */
    static double curve(double p,double a,double b,double n,double t,double pd,double d,double nd){
        double delta=(b-a)/d, before=(a-p)/pd, after=(n-b)/nd;
        double m0=(before*d+delta*pd)/(pd+d);
        double m1=(delta*nd+after*d)/(d+nd);
        double t2=t*t,t3=t2*t;
        double value=(2*t3-3*t2+1)*a+(t3-2*t2+t)*d*m0+(-2*t3+3*t2)*b+(t3-t2)*d*m1;
        return value;
    }
}



