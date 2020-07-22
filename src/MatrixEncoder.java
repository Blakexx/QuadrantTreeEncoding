import java.io.IOException;
import java.util.function.BiFunction;

public interface MatrixEncoder<E>{

  int refSize();

  void setMatrix(E[][] m);

  void setEncoder(BiFunction<E,Integer,byte[]> e);

  void setDecoder(BiFunction<byte[],Integer,E> d);

  void printAnalytics();

  MemoryController encodeMatrix();

  void encodeMatrix(String path) throws IOException;

  E[][] decodeMatrix(BitReader reader) throws IOException;

  static <V> V[][] decodeMatrix(BitReader reader, BiFunction<byte[],Integer,V> decoder) throws IOException{
    throw new IOException("Not implemented");
  }
}