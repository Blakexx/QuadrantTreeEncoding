import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class DoubleHybridEncoder<E> implements MatrixEncoder<E> {

    private E[][] matrix;
    private BiFunction<E,Integer,byte[]> encoder;
    private BiFunction<byte[],Integer,E> decoder;
    private E defaultItem;
    private MemoryController controller;
    private MemoryController.MemoryBitOutputStream writer;
    private static int refSize, dataSize, headerSize;
    private int bitsPerData, longestX;

    public DoubleHybridEncoder(E[][] m, int bitsPerData, BiFunction<E,Integer,byte[]> e, BiFunction<byte[],Integer,E> d){
        matrix = m;
        encoder = e;
        decoder = d;
        this.bitsPerData = bitsPerData;
    }

    public String getName(){
        return "DoubleH";
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

    public void printAnalytics(){
        System.out.println("Header size: "+headerSize+" bits");
        System.out.println("Data size: "+dataSize+" bits");
        System.out.println("Ref size: "+refSize+" bits");
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

    public void encodeMatrix(String path) throws IOException{
        encodeMatrix();
        byte[] bytes = controller.getBits(0,controller.size());
        FileBitOutputStream fileWriter = new FileBitOutputStream(path,false);
        fileWriter.writeBits(controller.size(),bytes);
    }

    public MemoryController encodeMatrix(){
        controller = new MemoryController();
        writer = controller.outputStream();
        refSize = 0;
        dataSize = 0;
        headerSize = 0;
        longestX = 0;
        HashMap<E,Integer> countMap = new HashMap<>();
        for(int r = 0; r<matrix.length;r++){
            longestX = Math.max(longestX,matrix[r].length);
            for(int c = 0; c<matrix[r].length;c++){
                Integer val = countMap.get(matrix[r][c]);
                if(val==null){
                    val = 0;
                }
                countMap.put(matrix[r][c], val+1);
            }
        }
        int maxCount = 0, totalCount = 0;
        defaultItem = null;
        for(E key : countMap.keySet()){
            int count = countMap.get(key);
            totalCount += count;
            if(count>maxCount){
                defaultItem = key;
                maxCount = count;
            }
        }
        /*
        for(int r = 0; r<matrix.length;r++){
            for(int c = 0; c<matrix[r].length;c++){
                dataSize+=!defaultItem.equals(matrix[r][c])?bitsPerData:0;
            }
        }
        */
        BiFunction<Integer,Integer,byte[]> intEncoder = BitEncoders.intEncoder;
        writer.writeBits(8,bitsPerData,intEncoder);
        writer.writeBits(bitsPerData,defaultItem,encoder);
        int height = matrix.length, width = longestX;
        int heightBits = Integer.toString(height,2).length();
        int widthBits = Integer.toString(width,2).length();
        writer.writeBits(5,heightBits-1,intEncoder);
        writer.writeBits(heightBits,height,intEncoder);
        writer.writeBits(5,widthBits-1,intEncoder);
        writer.writeBits(widthBits,width,intEncoder);
        //headerSize=8+bitsPerData+5+heightBits+5+widthBits;
        if((longestX>1||matrix.length>1)&&(maxCount!=totalCount)){
            doPathSetup(new StackFrame(0,0,height,width));
            //controller.delete(lastData,controller.size());
        }else{
            writer.writeBit(false);
            writer.writeBit(false);
        }
        //refSize = controller.size()-dataSize-headerSize;
        controller.trim();
        return controller;
    }

    private LinkedList<DataPoint<E>> encodeHelper(StackFrame frame){
        int yPos = frame.yPos, xPos = frame.xPos;
        LinkedList<DataPoint<E>> foundData = new LinkedList<>();
        if(yPos>=matrix.length){
            return foundData;
        }
        if(frame.size()<=1){
            if(xPos>=matrix[yPos].length){
                if(xPos<longestX){
                    writer.writeBit(false);
                }
                return foundData;
            }
            E item = matrix[yPos][xPos];
            if(item.equals(defaultItem)){
                writer.writeBit(false);
                return foundData;
            }
            writer.writeBit(true);
            writer.writeBits(bitsPerData,item,encoder);
            foundData.add(new DataPoint<>(item,yPos,xPos));
        }else{
            for(StackFrame child : frame.getChildren()){
                foundData.addAll(doPathSetup(child));
            }
        }
        return foundData;
    }

    private LinkedList<DataPoint<E>> doPathSetup(StackFrame frame){
        int prevLength = controller.size();
        if(frame.size()>1){
            writer.writeBit(false);
            writer.writeBit(true);
        }
        LinkedList<DataPoint<E>> foundData = encodeHelper(frame);
        if(frame.size()>1){
            if(foundData.size()==0){
                controller.delete(prevLength+1,controller.size());
                writer.writeBit(false);
            }else{
                int added = controller.size()-prevLength;
                int dataSize = frame.size()*bitsPerData+2;
                if(added>=dataSize){
                    controller.delete(prevLength,controller.size());
                    writer.writeBit(true);
                    final int startBit = controller.size();
                    Iterator<DataPoint<E>> pointIter = foundData.iterator();
                    DataPoint<E> currentPoint = pointIter.next();
                    int index = 0, pointIndex = 0;
                    while(pointIndex<foundData.size() || index<frame.size()){
                        int currentIndex = (currentPoint.row-frame.yPos)*frame.width+currentPoint.column-frame.xPos;
                        boolean iterIndex = index<frame.size();
                        if(iterIndex){
                            writer.writeBits(bitsPerData,defaultItem,encoder);
                        }
                        if(index>=currentIndex){
                            controller.setBits(startBit+currentIndex*bitsPerData,bitsPerData,currentPoint.data,encoder);
                            if(pointIter.hasNext()){
                                currentPoint = pointIter.next();
                            }else{
                                currentPoint = new DataPoint<>(null,frame.yPos+frame.height,frame.xPos+frame.width);
                            }
                            pointIndex++;
                        }
                        if(iterIndex){
                            index++;
                        }
                    }
                }
            }
        }
        return foundData;
    }

    public E[][] decodeMatrix(BitReader input) throws IOException{
        return decodeMatrix(input,decoder);
    }

    public <V> V[][] decodeMatrix(BitReader input, BiFunction<byte[],Integer,V> decoder) throws IOException{
        BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
        int bitsPerData = input.readBits(8,intDecoder);
        V defaultItem = input.readBits(bitsPerData,decoder);
        int heightBits = input.readBits(5,intDecoder)+1;
        int height = input.readBits(heightBits,intDecoder);
        int widthBits = input.readBits(5,intDecoder)+1;
        int width = input.readBits(widthBits,intDecoder);
        headerSize=8+bitsPerData+5+heightBits+5+widthBits;
        dataSize = 0;
        StackFrame current = new StackFrame(0,0,height,width);
        V[][] matrix = (V[][])new Object[height][width];
        double crsCount = 0, unCount = 0, qteCount = 0;
        while(current!=null&&input.hasNext()){
            boolean readMode = current.width<=1&&current.height<=1;
            if(!readMode&&input.readBit()){
                unCount++;
                for(int r = 0; r<current.height;r++){
                    for(int c = 0; c<current.width; c++){
                        matrix[current.yPos+r][current.xPos+c] = input.readBits(bitsPerData,decoder);
                        dataSize+=bitsPerData;
                    }
                }
                current = current.skipChildren();
            }else{
                qteCount++;
                if(input.readBit()){
                    if(readMode){
                        V data = input.readBits(bitsPerData,decoder);
                        matrix[current.yPos][current.xPos] = data;
                        dataSize+=bitsPerData;
                    }
                    current = current.getNext();
                }else{
                    current = current.skipChildren();
                }
            }
        }
        refSize = input.totalRead()-dataSize-headerSize;
        double total = qteCount+crsCount+unCount;
        //System.out.println("QTE: "+(qteCount/total));
        //System.out.println("CRS: "+(crsCount/total));
        //.out.println("UNE: "+(unCount/total));
        //System.out.println(refSize+" "+input.totalRead());
        //System.out.println();
        for(int r = 0; r<height;r++){
            for(int c = 0; c<width;c++){
                if(matrix[r][c]==null){
                    matrix[r][c] = defaultItem;
                }
            }
        }
        return matrix;
    }
}