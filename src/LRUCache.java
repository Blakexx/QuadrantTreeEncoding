import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LRUCache<K,V> implements CacheManager<K,V> {

    private final HashMap<K, CacheLinkedList<Pair<K,V>>.ListNode> lookup;
    private final CacheLinkedList<Pair<K,V>> controller;
    private final int capacity;

    public LRUCache(int capacity){
        lookup = new HashMap<>();
        controller = new CacheLinkedList<>();
        this.capacity = capacity;
    }

    public void improveItem(K key){
        CacheLinkedList<Pair<K,V>>.ListNode val = lookup.get(key);
        if(val!=null){
            controller.add(controller.remove(val));
        }
    }

    public void put(K key, V value){
        CacheLinkedList<Pair<K,V>>.ListNode val = lookup.get(key);
        if(val==null&&capacity==controller.size()){
            CacheLinkedList<Pair<K,V>>.ListNode toRemove = controller.getFirst();
            controller.remove(toRemove);
            if(toRemove==null){
                return;
            }
            lookup.remove(toRemove.val.key);
        }else if(val!=null){
            controller.remove(val);
        }
        lookup.put(key,controller.add(new Pair<>(key,value)));
    }

    public void remove(K key){
        controller.remove(lookup.remove(key));
    }

    public boolean contains(K key){
        return lookup.get(key)!=null;
    }

    public V getNoCache(K key){
        CacheLinkedList<Pair<K,V>>.ListNode val = lookup.get(key);
        return val!=null?val.val.value:null;
    }

    public V get(K key){
        CacheLinkedList<Pair<K,V>>.ListNode val = lookup.get(key);
        if(val!=null){
            CacheLinkedList<Pair<K,V>>.ListNode node = controller.add(controller.remove(val));
            lookup.put(key,node);
            return node.val.value;
        }
        return null;
    }

    public Set<K> keySet(){
        return lookup.keySet();
    }

    public Collection<V> values(){
        return lookup.values().stream().map((node)->node.val.value).collect(Collectors.toList());
    }

    public String toString(){
        return "Lookup: "+lookup+"\nList: "+controller+"\nSize,Cap: "+controller.size()+" "+capacity+"\n";
    }

}