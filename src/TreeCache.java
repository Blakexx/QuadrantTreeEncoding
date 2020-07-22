import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TreeCache<K,V> implements CacheManager<K,V> {

    private HashMap<Integer,V> lookup;
    private TreeSet<K> keyManager;
    private final Function<K,Integer> hashFunction;
    private final Comparator<K> comparator;
    private final int capacity;

    public TreeCache(int capacity, Comparator<K> comparator, Function<K,Integer> hashFunction){
        this.capacity = capacity;
        this.comparator = comparator;
        this.hashFunction = hashFunction;
        lookup = new HashMap<>();
        keyManager = new TreeSet<>(comparator);
    }

    public void put(K key, V value) {
        if(keyManager.size()==capacity){
            K first = keyManager.first();
            if(comparator.compare(key,first)<0){
                return;
            }
            remove(first);
        }
        lookup.put(hashFunction.apply(key),value);
        keyManager.add(key);
    }

    public void remove(K key) {
        lookup.remove(hashFunction.apply(key));
        keyManager.remove(key);
    }

    public V get(K key) {
        return lookup.get(hashFunction.apply(key));
    }

    public V getNoCache(K key) {
        return get(key);
    }

    public boolean contains(K key) {
        return get(key)!=null;
    }

    public Set<K> keySet() {
        return keyManager;
    }

    public Collection<V> values() {
        return lookup.values();
    }

    public void improveItem(K key){

    }

    public String toString(){
        return "Lookup: "+lookup+"\nList: "+keyManager+"\nSize,Cap: "+keyManager.size()+" "+capacity+"\n";
    }
}
