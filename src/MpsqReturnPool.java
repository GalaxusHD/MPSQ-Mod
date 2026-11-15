package de.galaxushd.mpsqcamera;

import java.util.*;

/** Round-independent participation state; streamer rank is never changed. */
public final class MpsqReturnPool {
    public final Map<String,String> waiting = new LinkedHashMap<>();
    public final Map<String,String> selected = new LinkedHashMap<>();
    public final Set<String> returned = new HashSet<>();
    public static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    public boolean exclude(String name) {
        String key=key(name); if(key.isEmpty())return false;
        returned.remove(key);
        return waiting.put(key,name)==null;
    }
    public List<String> draw(int count,Random random) {
        if(count<1||count>50)throw new IllegalArgumentException("Anzahl muss zwischen 1 und 50 liegen.");
        List<String> keys=new ArrayList<>(waiting.keySet());keys.removeAll(selected.keySet());
        if(count>keys.size())throw new IllegalArgumentException("Nicht genügend Kandidaten verfügbar.");
        Collections.shuffle(keys,random);
        List<String> result=new ArrayList<>();
        for(String key:keys.subList(0,count)){String name=waiting.get(key);selected.put(key,name);result.add(name);}
        return List.copyOf(result);
    }
    public boolean confirm(String name) {
        String key=key(name);if(selected.remove(key)==null)return false;
        waiting.remove(key);returned.add(key);return true;
    }
}
