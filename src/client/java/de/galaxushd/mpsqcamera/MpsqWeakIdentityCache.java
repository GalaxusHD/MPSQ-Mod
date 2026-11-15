package de.galaxushd.mpsqcamera;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Objects;

/** Render-thread cache: never invokes entity hashCode/equals or keeps a despawned actor alive. */
public final class MpsqWeakIdentityCache<K,V> {
    private final ReferenceQueue<K> collected=new ReferenceQueue<>();
    private final HashMap<Key<K>,V> values=new HashMap<>();
    private static final class Key<T> extends WeakReference<T> {
        private final int hash;
        Key(T object,ReferenceQueue<T> queue){super(Objects.requireNonNull(object),queue);hash=System.identityHashCode(object);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){if(this==other)return true;T value=get();return value!=null&&other instanceof Key<?> key&&value==key.get();}
    }
    private void clean(){java.lang.ref.Reference<? extends K> ref;while((ref=collected.poll())!=null)values.remove(ref);}
    public V put(K key,V value){clean();return values.put(new Key<>(key,collected),value);}
    public V get(K key){clean();return values.get(new Key<>(key,null));}
    public V getOrDefault(K key,V fallback){V value=get(key);return value==null?fallback:value;}
    public int size(){clean();return values.size();}
}
