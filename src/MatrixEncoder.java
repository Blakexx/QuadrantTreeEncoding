import java.util.function.BiFunction;

public interface MatrixEncoder<E>{

    int refSize();

    int dataSize();

    int headerSize();

    void setMatrix(E[][] m);

    void setEncoder(BiFunction<E,Integer,byte[]> e);

    void setDecoder(BiFunction<byte[],Integer,E> d);

    default void printAnalytics(){
        System.out.println("Header size: "+headerSize()+" bits");
        System.out.println("Data size: "+dataSize()+" bits");
        System.out.println("Ref size: "+refSize()+" bits");
    }

    MemoryController encodeMatrix(MemoryController controller);

    Matrix<E> getMatrix(MemoryController controller, double cachePercent);

    E[][] decodeMatrix(MemoryController controller);

    static <V> V[][] decodeMatrix(MemoryController controller, BiFunction<byte[],Integer,V> decoder){
        throw new RuntimeException("Not implemented");
    }

    default String getName(){
        return "Matrix";
    }
}