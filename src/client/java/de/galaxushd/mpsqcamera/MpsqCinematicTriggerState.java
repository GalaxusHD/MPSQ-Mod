package de.galaxushd.mpsqcamera;

import java.util.*;

/** Entry detection seeds silently on world load; remaining inside never retriggers. */
public final class MpsqCinematicTriggerState {
    private final Map<String,Boolean> inside=new HashMap<>();
    public boolean entered(String key,boolean current){
        Boolean previous=inside.put(key,current);
        return previous!=null&&!previous&&current;
    }
    public void clear(){inside.clear();}
}
