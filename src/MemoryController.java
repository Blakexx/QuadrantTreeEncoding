import java.util.function.BiFunction;

public class MemoryController{

    private BitList<Boolean> bits;
    private int size;
    private BiFunction<Boolean,Integer,byte[]> bitEncoder = (bool,len)->new byte[]{(byte)(bool?-128:0)};
    private BiFunction<byte[],Integer,Boolean> bitDecoder = (bits,len)->bits[0]!=0;

    public MemoryController(){
        size = 0;
        bits = new BitList<>(8,1,
                bitEncoder,
                bitDecoder
        );
    }

    private void ensureCapacity(int toAdd){
        while(size()+toAdd>bits.length){
            BitList<Boolean> newList = new BitList<>(bits.length*2,1,
                    bitEncoder,
                    bitDecoder
            );
            newList.setMany(0,size(),bits.getMany(0,size()));
            bits = newList;
        }
        size+=toAdd;
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

    public static class MemoryBitOutputStream implements BitWriter{

        private MemoryController controller;
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

        public <E> void writeBits(int length, E data, BiFunction<E, Integer, byte[]> encoder){
            writeBits(length,encoder.apply(data,length));
        }

        public void writeBit(boolean bit){
            checkOpen();
            controller.setBit(controller.size(),bit);
        }

        public void close(){
            closed = true;
        }
    }

    public MemoryBitInputStream inputStream(){
        return new MemoryBitInputStream(this);
    }

    public static class MemoryBitInputStream implements BitReader{

        private int readIndex;
        private MemoryController controller;
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
        BitList<Boolean> trimmed = new BitList<>(size(),1,
                bitEncoder,
                bitDecoder
        );
        trimmed.setMany(0,size(),bits.getMany(0,size()));
        bits = trimmed;
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
