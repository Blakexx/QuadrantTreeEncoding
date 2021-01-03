import java.io.*;

public class DiskByteArray implements ByteArray{

    private RandomAccessFile file;
    private final int size;
    private final File source;

    public DiskByteArray(int size, File source){
        this.source = source;
        this.size = size;
        try{
            if(source.exists()){
                source.delete();
            }
            source.getParentFile().mkdirs();
            source.createNewFile();
            file = new RandomAccessFile(source,"rw");
            FileOutputStream stream = new FileOutputStream(source);
            stream.write(new byte[size]);
            stream.close();
        }catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException("Something went wrong...");
        }
    }

    public File source(){
        return source;
    }

    public int size(){
        return size;
    }

    public byte get(int i){
        try{
            file.seek(i);
            return file.readByte();
        }catch(Exception e){
            throw new RuntimeException("Something went wrong...");
        }
    }

    public void set(int i, byte value){
        try{
            file.seek(i);
            file.writeByte(value);
        }catch(Exception e){
            throw new RuntimeException("Something went wrong...");
        }
    }
}
