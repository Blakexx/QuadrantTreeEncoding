import java.io.IOException;
import java.util.function.BiFunction;

public interface BitReader {

    boolean hasNext();

    int totalRead();

    byte[] readBits(int num) throws IOException;

    <E> E readBits(int num, BiFunction<byte[], Integer, E> decoder) throws IOException;

    boolean readBit() throws IOException;

    void close() throws IOException;
}
