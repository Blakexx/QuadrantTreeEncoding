import java.io.InputStream;
import java.util.function.BiFunction;

public abstract class BitInputStream extends InputStream {

    public int read() {
        if(!hasNext()){
            return -1;
        }
        return readBits(8, BitEncoders.intDecoder);
    }

    public abstract boolean hasNext();

    public abstract byte[] readBits(int num);

    public <E> E readBits(int num, BiFunction<byte[], Integer, E> decoder){
        return decoder.apply(readBits(num),num);
    }

    public abstract boolean readBit();
}
