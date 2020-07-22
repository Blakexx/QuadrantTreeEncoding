import java.io.IOException;
import java.util.function.BiFunction;

public interface BitWriter {

    void writeBits(int length, byte[] bits) throws IOException;

    <E> void writeBits(int length, E data, BiFunction<E, Integer, byte[]> encoder) throws IOException;

    void writeBit(boolean bit) throws IOException;

    default void flush() throws Exception{
        throw new IllegalStateException("Not implemented");
    }

    void close() throws IOException;

}
