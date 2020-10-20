import java.util.function.BiFunction;

public interface BitController {

    void clear();

    void delete(int start, int end);

    int size();

    Boolean getBit(int index);

    byte[] getBits(int index, int length);

    <E> E getBits(int index, int length, BiFunction<byte[],Integer,E> decoder);

    void setBit(int index, boolean bit);

    void setBits(int index, int length, byte[] data);

    <E> void setBits(int index, int length, E data, BiFunction<E, Integer, byte[]> encoder);

    void trim();

    BitReader inputStream();

    BitWriter outputStream();

    String bitToString(int start);

    String toStringWithCapacity();
}
