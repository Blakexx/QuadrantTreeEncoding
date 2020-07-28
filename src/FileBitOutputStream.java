import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;

public class FileBitOutputStream implements BitWriter {

    FileOutputStream output;
    byte outputBuffer, outCount = 0;

    public FileBitOutputStream(String path, boolean append) throws IOException{
        output = new FileOutputStream(path, append);
    }

    public void writeBits(int length, byte[] bits) throws IOException{
        if(length>bits.length*8){
            throw new IllegalArgumentException("Invalid length");
        }
        int byteIndex = 0;
        int mask = 128;
        for(int i = 0; i<length;i++){
            if(mask==0){
                byteIndex++;
                mask = 128;
            }
            writeBit((bits[byteIndex]&mask)!=0);
            mask>>=1;
        }
    }

    public <E> void writeBits(int length, E data, BiFunction<E, Integer, byte[]> encoder) throws IOException{
        writeBits(length, encoder.apply(data,length));
    }

    public void writeBit(boolean bit) throws IOException{
        int toWrite = bit?1:0;
        if(outCount==8){
            flush();
        }
        outputBuffer+=(toWrite<<(7-outCount++));
    }

    public void flush() throws IOException{
        if(outCount!=0){
            output.write(outputBuffer);
            outCount = 0;
            outputBuffer = 0;
        }
    }

    public void close() throws IOException{
        flush();
        output.close();
    }
}