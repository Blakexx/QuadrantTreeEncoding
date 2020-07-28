import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.BiFunction;

public class FileBitInputStream implements BitReader{

    FileInputStream input;
    byte inputBuffer, inCount = 8;

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

    public byte[] readBits(int num) throws IOException{
        byte[] bytes = new byte[(int)Math.ceil(num/8.0)];
        for(int i = 0; i<num;i++){
            if(readBit()){
                bytes[i/8] |= (byte)(128>>>(i%8));
            }
        }
        return bytes;
    }

    public <E> E readBits(int num, BiFunction<byte[], Integer, E> decoder) throws IOException{
        return decoder.apply(readBits(num),num);
    }

    public boolean readBit() throws IOException{
        prepareReadBuffer();
        return (inputBuffer&(1<<7-inCount++))!=0;
    }

    private void prepareReadBuffer() throws IOException{
        if(inCount==8){
            inCount = 0;
            int newData = input.read();
            if(newData==-1){
                throw new IOException("End of file");
            }
            inputBuffer = (byte)newData;
        }
    }

    public void close() throws IOException{
        input.close();
    }
}