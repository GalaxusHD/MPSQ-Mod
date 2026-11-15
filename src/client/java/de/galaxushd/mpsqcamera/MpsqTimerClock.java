package de.galaxushd.mpsqcamera;

/** Timer arithmetic independent of rendering, world ticks and wall-clock changes. */
final class MpsqTimerClock {
    static final long MAX=359_999_000L;
    long initial=300_000, value=initial;
    boolean running, up, finished;
    void start(){if(up||value>0){running=true;finished=false;}}
    void reset(){value=initial;running=false;finished=false;}
    void set(long ms){initial=value=Math.clamp(ms,0,MAX);running=false;finished=false;}
    void add(long ms){value=Math.clamp(value+ms,0,MAX);if(value>0)finished=false;}
    boolean advance(long ms){
        if(!running||ms<=0)return false;
        value=up?Math.min(MAX,value+ms):Math.max(0,value-ms);
        if(up&&value==MAX){running=false;return false;}
        if(!up&&value==0){running=false;if(!finished){finished=true;return true;}}
        return false;
    }
    long seconds(){return up?value/1000:(value+999)/1000;}
    static long parse(String text){
        String s=text.trim().toLowerCase(java.util.Locale.ROOT);
        if(s.matches("\\d+"))return bounded(Math.multiplyExact(Long.parseLong(s),1000L));
        var m=java.util.regex.Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matcher(s);
        if(!m.matches()||s.isEmpty())throw new IllegalArgumentException("Zeit als 5m, 2m30s oder Sekunden eingeben.");
        long n=0;for(int i=1;i<=3;i++)if(m.group(i)!=null)n=Math.addExact(n,Math.multiplyExact(Long.parseLong(m.group(i)),i==1?3600:i==2?60:1));
        return bounded(Math.multiplyExact(n,1000));
    }
    private static long bounded(long n){if(n<0||n>MAX)throw new IllegalArgumentException("Zeitbereich: 0 bis 99h59m59s.");return n;}
    static String digits(long seconds,String format){
        boolean hours=format.equals("hours")||format.equals("auto")&&seconds>=3600;
        long left=hours?seconds/3600:seconds/60,right=hours?seconds/60%60:seconds%60;
        return String.format(java.util.Locale.ROOT,"%02d%02d",Math.min(99,left),right);
    }
    static String label(long seconds){return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
}
