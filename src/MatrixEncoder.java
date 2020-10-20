import java.io.IOException;
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

    MemoryController encodeMatrix();

    void encodeMatrix(String path) throws IOException;

    E[][] decodeMatrix(BitReader input) throws IOException;

    static <V> V[][] decodeMatrix(BitReader input, BiFunction<byte[],Integer,V> decoder) throws IOException{
        throw new IOException("Not implemented");
    }

    default String getName(){
        return "Matrix";
    }
}