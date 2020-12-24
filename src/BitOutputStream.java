import java.io.OutputStream;
import java.util.function.BiFunction;

public abstract class BitOutputStream extends OutputStream {

    public void write(int b) {
        writeBits(8, b, BitEncoders.intEncoder);
    }

    public abstract void writeBits(int length, byte[] bits);

    public <E> void writeBits(int length, E data, BiFunction<E, Integer, byte[]> encoder){
        writeBits(length,encoder.apply(data,length));
    }

    public abstract void writeBit(boolean bit);

}
