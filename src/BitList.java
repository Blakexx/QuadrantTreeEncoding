import java.io.File;
import java.util.function.BiFunction;

class BitList<E>{

    private ByteArray container;
    public static final byte bitsPerCon = 8;
    private int bitsPerData;
    public final int length;
    private final BiFunction<E,Integer,byte[]> bitEncoder;
    private final BiFunction<byte[],Integer,E> bitDecoder;

    public BitList(int length, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        if(bitsPerData<0){
            throw new IllegalArgumentException("BitsPerData must be >=0");
        }
        if(length<0){
            throw new IllegalArgumentException("Length must be >=0");
        }
        this.length = length;
        this.bitsPerData = bitsPerData;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        container = new MemoryByteArray(Main.roundUpDiv(length*bitsPerData,bitsPerCon));
    }

    public BitList(int length, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, File source){
        if(bitsPerData<0){
            throw new IllegalArgumentException("BitsPerData must be >=0");
        }
        if(length<0){
            throw new IllegalArgumentException("Length must be >=0");
        }
        this.length = length;
        this.bitsPerData = bitsPerData;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        container = new DiskByteArray(Main.roundUpDiv(length*bitsPerData,bitsPerCon),source);
    }

    public int bitsPerData(){
        return bitsPerData;
    }

    private void set(int index, int length, byte[] bitData){
        int bitIndex = (index*bitsPerData)%bitsPerCon;
        int conIndex = (index*bitsPerData)/bitsPerCon;
        int freeBits;
        int toWrite = bitsPerData*length;
        for(int i = 0; i<toWrite;i+=freeBits){
            if(bitIndex==bitsPerCon){
                conIndex++;
                bitIndex = 0;
            }
            int dataConIndex = i/bitsPerCon;
            int dataBitIndex = i%bitsPerCon;
            freeBits = Math.min(toWrite-i,Math.min(bitsPerCon-dataBitIndex,bitsPerCon-bitIndex));
            int dataByte = bitData[dataConIndex]&255;
            dataByte>>>=bitIndex;
            int mask = (Integer.MIN_VALUE>>(freeBits-1));
            mask>>>=(32-bitsPerCon)+bitIndex;
            byte data = container.get(conIndex);
            data&=~mask;
            data|=dataByte;
            container.set(conIndex,data);
            bitData[dataConIndex]<<=freeBits;
            bitIndex+=freeBits;
        }
    }

    public void set(int index, E data){
        if(index<0||index>=length){
            throw new ArrayIndexOutOfBoundsException("Index cannot be <0 or >=length");
        }
        set(index,1,bitEncoder.apply(data,bitsPerData));
    }

    public void setMany(int index, int length, byte[] data){
        if(index+length>this.length){
            throw new ArrayIndexOutOfBoundsException();
        }
        if(data.length*8<length*bitsPerData){
            throw new IllegalArgumentException();
        }
        set(index,length,data);
    }

    private byte[] get(int index, int length){
        int toWrite = bitsPerData*length;
        byte[] returned = new byte[(int)Math.ceil(toWrite/(double)bitsPerCon)];
        int bitIndex = (index*bitsPerData)%bitsPerCon;
        int conIndex = (index*bitsPerData)/bitsPerCon;
        int freeBits;
        for(int i = 0; i<toWrite;i+=freeBits){
            if(bitIndex==bitsPerCon){
                conIndex++;
                bitIndex = 0;
            }
            int dataConIndex = i/bitsPerCon;
            int dataBitIndex = i%bitsPerCon;
            freeBits = Math.min(toWrite-i,Math.min(bitsPerCon-dataBitIndex,bitsPerCon-bitIndex));
            int mask = (Integer.MIN_VALUE>>(freeBits-1));
            mask>>>=(32-bitsPerCon)+bitIndex;
            int data = container.get(conIndex)&mask;
            data<<=bitIndex;
            data>>>=dataBitIndex;
            returned[dataConIndex]|=data;
            bitIndex+=freeBits;
        }
        return returned;
    }

    public E get(int index){
        if(index<0||index>=length){
            throw new ArrayIndexOutOfBoundsException("Index cannot be <0 or >=length");
        }
        return bitDecoder.apply(get(index,1),bitsPerData);
    }

    public byte[] getMany(int index, int length){
        if(index+length>this.length){
            throw new ArrayIndexOutOfBoundsException("Index "+(index+length)+" is greater than length "+this.length);
        }
        return get(index,length);
    }

    public void ensureCapacity(int newDataSize){
        if(newDataSize>bitsPerData){
            BitList<E> newList = new BitList<>(length,newDataSize,bitEncoder,bitDecoder);
            for(int i = 0; i<length;i++){
                newList.set(i,get(i));
            }
            bitsPerData = newDataSize;
            if(container.getClass()==newList.container.getClass()){
                container = newList.container;
            }else{
                container = new DiskByteArray(Main.roundUpDiv(length*bitsPerData,bitsPerCon),((DiskByteArray)container).source());
                for(int i = 0; i<length;i++){
                    set(i,newList.get(i));
                }
            }
        }
    }

    public String toString(){
        StringBuilder builder = new StringBuilder("[");
        for(int i = 0; i<length;i++){
            builder.append(get(i));
            builder.append(", ");
        }
        builder.delete(builder.length()-2,builder.length());
        builder.append("]");
        return builder.toString();
    }

}
