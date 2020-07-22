import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface CacheManager<K,V> {

    void put(K key, V value);

    void remove(K key);

    V get(K key);

    V getNoCache(K key);

    boolean contains(K key);

    Set<K> keySet();

    Collection<V> values();

    void improveItem(K key);
}
