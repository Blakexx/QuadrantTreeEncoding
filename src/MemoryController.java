import java.io.File;
import java.io.OutputStream;
import java.util.function.BiFunction;

public class MemoryController{

    private BitList<Boolean> bits;
    public final boolean onDisk;
    public final File source;
    private int size;
    private final BiFunction<Boolean,Integer,byte[]> bitEncoder = (bool,len)->new byte[]{(byte)(bool?-128:0)};
    private final BiFunction<byte[],Integer,Boolean> bitDecoder = (bits,len)->bits[0]!=0;

    private BitList<Boolean> makeList(int newSize){
        if(!onDisk){
            return new BitList<>(newSize, 1,
                    bitEncoder,
                    bitDecoder
            );
        }
        return new BitList<>(newSize, 1,
                bitEncoder,
                bitDecoder,
                source
        );
    }

    private void resize(int newSize, int newCapacity){
        if(newSize>newCapacity){
            throw new IllegalArgumentException("Invalid Parameters");
        }
        BitList<Boolean> newList = new BitList<>(newCapacity,1,
                bitEncoder,
                bitDecoder
        );
        int toCopy = Math.min(size(),newSize);
        newList.setMany(0,toCopy,bits.getMany(0,toCopy));
        if(!onDisk){
            bits = newList;
        }else{
            bits = makeList(newCapacity);
            bits.setMany(0,toCopy,newList.getMany(0,toCopy));
        }
        size = newSize;
    }

    public MemoryController(){
        onDisk = false;
        source = null;
        size = 0;
        bits = makeList(8);
    }

    public MemoryController(File source){
        onDisk = true;
        this.source = source;
        size = 0;
        bits = makeList(8);
    }

    private void ensureCapacity(int toAdd){
        if(toAdd<=0){
            return;
        }
        if(size() + toAdd <= bits.length){
            size+=toAdd;
            return;
        }
        int toMult = (int)Math.ceil(Main.logBase(size()+toAdd,2) - Main.logBase(bits.length,2));
        toMult = 1<<toMult;
        resize(size()+toAdd,bits.length*toMult);
    }

    public void clear(){
        size = 0;
        bits = makeList(8);
    }

    public void delete(int start, int end){
        if(start>end||start<0||end>size()){
            throw new IllegalArgumentException();
        }
        setBits(start,size()-end,getBits(end,size()-end));
        size-=(end-start);
    }

    public int size(){
        return size;
    }

    public int sizeWithCapacity(){
        return bits.length;
    }

    public Boolean getBit(int index){
        if(index>=size()){
            throw new ArrayIndexOutOfBoundsException("Index "+index+" is greater than length "+size());
        }
        return bits.get(index);
    }

    public byte[] getBits(int index, int length){
        if(index+length>size()){
            throw new ArrayIndexOutOfBoundsException("Index "+(index+length)+" is greater than length "+size());
        }
        if(length<0){
            throw new IllegalArgumentException();
        }
        if(length==0){
            return new byte[0];
        }
        return bits.getMany(index,length);
    }

    public <E> E getBits(int index, int length, BiFunction<byte[],Integer,E> decoder){
        return decoder.apply(getBits(index,length),length);
    }

    public void setBit(int index, boolean bit){
        ensureCapacity(Math.max(0,index+1-size()));
        bits.set(index,bit);
    }

    public void setBits(int index, int length, byte[] data){
        if(length<0){
            throw new IllegalArgumentException();
        }
        if(length==0){
            return;
        }
        ensureCapacity(Math.max(0,length+index-size()));
        bits.setMany(index,length,data);
    }

    public <E> void setBits(int index, int length, E data, BiFunction<E, Integer, byte[]> encoder){
        setBits(index,length,encoder.apply(data,length));
    }

    public MemoryBitOutputStream outputStream(){
        return new MemoryBitOutputStream(this);
    }

    public static class MemoryBitOutputStream extends BitOutputStream {

        private final MemoryController controller;
        private boolean closed = false;

        private MemoryBitOutputStream(MemoryController con){
            controller = con;
        }

        private void checkOpen(){
            if(closed){
                throw new IllegalStateException("Input is closed");
            }
        }

        public void writeBits(int length, byte[] bits){
            checkOpen();
            controller.setBits(controller.size(),length,bits);
        }

        public void writeBit(boolean bit){
            checkOpen();
            controller.setBit(controller.size(),bit);
        }

        public void close(){
            closed = true;
        }

        public void flush(){}
    }

    public MemoryBitInputStream inputStream(){
        return new MemoryBitInputStream(this);
    }

    public static class MemoryBitInputStream extends BitInputStream {

        private int readIndex;
        private final MemoryController controller;
        private boolean closed = false;

        private MemoryBitInputStream(MemoryController con){
            controller = con;
        }

        private void checkOpen(){
            if(closed){
                throw new IllegalStateException("Input is closed");
            }
        }

        public boolean hasNext(){
            return !closed&&readIndex<controller.size();
        }

        public int totalRead(){
            return readIndex;
        }

        public void reset(){
            checkOpen();
            readIndex = 0;
        }

        public boolean readBit(){
            checkOpen();
            if(!hasNext()){
                throw new IndexOutOfBoundsException();
            }
            return controller.getBit(readIndex++);
        }

        public byte[] readBits(int num){
            checkOpen();
            byte[] data = controller.getBits(readIndex,num);
            readIndex+=num;
            return data;
        }

        public <E> E readBits(int num, BiFunction<byte[], Integer, E> decoder){
            return decoder.apply(readBits(num),num);
        }

        public void close(){
            closed = true;
        }
    }

    public void trim(){
        resize(size(),size());
    }

    public String bitToString(int start){
        StringBuilder builder = new StringBuilder();
        if(size>0){
            for(int i = start; i<size;i++){
                builder.append(getBit(i)?"1":"0");
            }
        }
        return builder.toString();
    }

    public String toString(){
        return bitToString(0);
    }

    public String toStringWithCapacity(){
        return bits.toString();
    }
}
