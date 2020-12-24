import java.io.*;
import java.util.function.BiFunction;

public class FileBitInputStream extends BitInputStream{

    private FileInputStream input;
    private byte inputBuffer, inCount = 8;
    private int totalRead;

    public FileBitInputStream(File file) throws IOException{
        input = new FileInputStream(file);
    }

    public FileBitInputStream(String path) throws IOException{
        input = new FileInputStream(path);
    }

    public boolean hasNext(){
        if(inCount==8){
            try{
                prepareReadBuffer();
            }catch(Exception e){
                return false;
            }
        }
        return true;
    }

    public int totalRead(){
        return totalRead;
    }

    public byte[] readBits(int num){
        byte[] bytes = new byte[(int)Math.ceil(num/8.0)];
        for(int i = 0; i<num;i++){
            if(readBit()){
                bytes[i/8] |= (byte)(128>>>(i%8));
            }
        }
        totalRead+=num;
        return bytes;
    }

    public boolean readBit(){
        prepareReadBuffer();
        totalRead++;
        return (inputBuffer&(1<<7-inCount++))!=0;
    }

    private void prepareReadBuffer(){
        if(inCount==8){
            inCount = 0;
            int newData = -1;
            try{
                newData = input.read();
            }catch(IOException e){
                throw new RuntimeException("Read failed");
            }
            if(newData==-1){
                throw new RuntimeException("End of file");
            }
            inputBuffer = (byte)newData;
        }
    }

    public void close(){
        try{
            input.close();
        }catch(IOException e){
            throw new RuntimeException("Close failed");
        }
    }
}