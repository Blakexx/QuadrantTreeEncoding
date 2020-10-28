import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class QuadrantTreeEncoder<E> implements MatrixEncoder<E> {

    private E[][] matrix;
    private BiFunction<E,Integer,byte[]> encoder;
    private BiFunction<byte[],Integer,E> decoder;
    private E defaultItem;
    private MemoryController controller;
    private MemoryController.MemoryBitOutputStream writer;
    private int refSize, dataSize, headerSize;
    private int bitsPerData, longestX, remainingItems;

    public QuadrantTreeEncoder(E[][] m, int bitsPerData, BiFunction<E,Integer,byte[]> e, BiFunction<byte[],Integer,E> d){
        matrix = m;
        encoder = e;
        decoder = d;
        this.bitsPerData = bitsPerData;
    }

    public String getName(){
        return "QTE";
    }

    public void setMatrix(E[][] m){
        matrix = m;
    }

    public void setEncoder(BiFunction<E,Integer,byte[]> e){
        encoder = e;
    }

    public void setDecoder(BiFunction<byte[],Integer,E> d){
        decoder = d;
    }

    public int refSize(){
        return refSize;
    }

    public int dataSize(){
        return dataSize;
    }

    public int headerSize(){
        return headerSize;
    }

    public MemoryController encodeMatrix(MemoryController controller){
        controller.clear();
        this.controller = controller;
        writer = controller.outputStream();
        refSize = 0;
        dataSize = 0;
        headerSize = 0;
        longestX = 0;
        HashMap<E,Integer> countMap = new HashMap<>();
        int maxCount = 0, itemCount = 0;
        defaultItem = null;
        for(int r = 0; r<matrix.length;r++){
            longestX = Math.max(longestX,matrix[r].length);
            itemCount+=matrix[r].length;
            for(int c = 0; c<matrix[r].length;c++){
                E item = matrix[r][c];
                Integer val = countMap.get(matrix[r][c]);
                if(val==null){
                    val = 0;
                }
                if(++val > maxCount){
                    maxCount = val;
                    defaultItem = item;
                }
                countMap.put(matrix[r][c], val);
            }
        }
        remainingItems = itemCount-maxCount;
        dataSize = remainingItems*bitsPerData;
        BiFunction<Integer,Integer,byte[]> intEncoder = BitEncoders.intEncoder;
        writer.writeBits(8,bitsPerData,intEncoder);
        writer.writeBits(bitsPerData,defaultItem,encoder);
        int height = matrix.length, width = longestX;
        int heightBits = Main.logBaseCeil(height+1,2);
        int widthBits = Main.logBaseCeil(width+1,2);
        writer.writeBits(5,heightBits-1,intEncoder);
        writer.writeBits(heightBits,height,intEncoder);
        writer.writeBits(5,widthBits-1,intEncoder);
        writer.writeBits(widthBits,width,intEncoder);
        headerSize=8+bitsPerData+5+heightBits+5+widthBits;
        if((longestX>1||matrix.length>1)&&dataSize>0){
            doPathSetup(new StackFrame(0,0,height,width));
            //controller.delete(lastData,controller.size());
        }else{
            writer.writeBit(false);
        }
        refSize = controller.size()-dataSize-headerSize;
        controller.trim();
        return controller;
    }

    private boolean encodeHelper(StackFrame frame){
        int yPos = frame.yPos, xPos = frame.xPos;
        if(yPos>=matrix.length){
            return false;
        }
        if(frame.size()<=1){
            if(xPos>=matrix[yPos].length){
                if(xPos<longestX){
                    writer.writeBit(false);
                }
                return false;
            }
            E item = matrix[yPos][xPos];
            if(item.equals(defaultItem)){
                writer.writeBit(false);
                return false;
            }
            remainingItems--;
            writer.writeBit(true);
            writer.writeBits(bitsPerData,item,encoder);
            return true;
        }else{
            boolean foundData = false;
            for(StackFrame child : frame.getChildren()){
                foundData|=doPathSetup(child);
            }
            return foundData;
        }
    }

    private boolean doPathSetup(StackFrame frame){
        if(remainingItems==0){
            writer.writeBit(false);
            return false;
        }
        int prevLength = controller.size();
        if(frame.size()>1){
            writer.writeBit(true);
        }
        boolean gotData = encodeHelper(frame);
        if(frame.size()>1&&!gotData){
            controller.delete(prevLength,controller.size());
            writer.writeBit(false);
        }
        return gotData;
    }

    public E[][] decodeMatrix(MemoryController controller){
        return decodeMatrix(controller,decoder);
    }

    public static <V> V[][] decodeMatrix(MemoryController controller, BiFunction<byte[],Integer,V> decoder){
        MemoryController.MemoryBitInputStream input = controller.inputStream();
        BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
        int bitsPerData = input.readBits(8,intDecoder);
        V defaultItem = input.readBits(bitsPerData,decoder);
        int heightBits = input.readBits(5,intDecoder)+1;
        int height = input.readBits(heightBits,intDecoder);
        int widthBits = input.readBits(5,intDecoder)+1;
        int width = input.readBits(widthBits,intDecoder);
        StackFrame current = new StackFrame(0,0,height,width);
        V[][] matrix = (V[][])new Object[height][width];
        while(current!=null&&input.hasNext()){
            boolean nextInst = input.readBit();
            boolean readMode = current.width<=1&&current.height<=1;;
            if(nextInst){
                if(readMode){
                    V data = input.readBits(bitsPerData,decoder);
                    matrix[current.yPos][current.xPos] = data;
                }
                current = current.getNext();
            }else{
                for(int r = 0; r<current.height; r++){
                    for (int c = 0; c<current.width; c++){
                        matrix[current.yPos+r][current.xPos+c] = defaultItem;
                    }
                }
                current = current.skipChildren();
            }
        }
        return matrix;
    }
}