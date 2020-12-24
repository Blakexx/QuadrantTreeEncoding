import java.io.*;

public class FileBitOutputStream extends BitOutputStream {

    private FileOutputStream output;
    private byte outputBuffer, outCount = 0;

    public FileBitOutputStream(File file, boolean append) throws IOException{
        file.createNewFile();
        output = new FileOutputStream(file, append);
    }

    public FileBitOutputStream(String path, boolean append) throws IOException{
        this(new File(path),append);
    }

    public void writeBits(int length, byte[] bits){
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

    public void writeBit(boolean bit){
        int toWrite = bit?1:0;
        if(outCount==8){
            flush();
        }
        outputBuffer+=(toWrite<<(7-outCount++));
    }

    public void flush(){
        if(outCount!=0){
            try{
                output.write(outputBuffer);
            }catch(IOException e){
                throw new RuntimeException("Flush failed");
            }
            outCount = 0;
            outputBuffer = 0;
        }
    }

    public void close(){
        try{
            flush();
            output.close();
        }catch(IOException e){
            throw new RuntimeException("Close failed");
        }
    }
}