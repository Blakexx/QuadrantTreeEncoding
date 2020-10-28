public class MemoryByteArray implements ByteArray{

    private byte[] array;

    public MemoryByteArray(int size){
        array = new byte[size];
    }

    public int size(){
        return array.length;
    }

    public byte get(int i){
        return array[i];
    }

    public void set(int i, byte b){
        array[i] = b;
    }
}
