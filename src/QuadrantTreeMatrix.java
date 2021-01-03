import java.io.File;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;

public class QuadrantTreeMatrix<E> extends Matrix<E>{
    private final MemoryController encodedMatrix;
    private final CacheManager<Integer,Integer> cache;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final LinkedList<Pair<Quadrant,Integer>> cacheQueue;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;
    public final double cachePercent;
    private final StandardHeader<E> header;

    public QuadrantTreeMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, double cachePercent){
        this.cachePercent = cachePercent;
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        header = new StandardHeader<>(encodedMatrix,bitDecoder);
        int height = height();
        int width = width();
        Quadrant baseFrame = new Quadrant(0,0,height,width);
        cache = new LRUCache<>((int)Math.round(baseFrame.size()*cachePercent));
        cacheQueue = new LinkedList<>();
        putIntoCache(baseFrame,0);
        trim();
        Pair<Quadrant,Integer> info = getClosestIndexFromCache(0,0,true);
        info = decodeUntil(0,0,info);
        for(int c = 0; c<width; c++){
            info = decodeUntil(0,c,info);
        }
        cacheQueue();
        info = getClosestIndexFromCache(0,0,true);
        info = decodeUntil(0,0,info);
        for(int r = 0; r<height;r++){
            info = decodeUntil(r,0,info);
        }
        cacheQueue();
    }

    public QuadrantTreeMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, double cachePercent){
        this(new QuadrantTreeEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController()),bitEncoder,bitDecoder,cachePercent);
    }

    public QuadrantTreeMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, double cachePercent, File source){
        this(new QuadrantTreeEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController(source)),bitEncoder,bitDecoder,cachePercent);
    }

    public int estimateBitSize(){
        int cacheSize = 0;/*, referenceSize = 32, intSize = 32;
        for(Integer key : cache.keySet()){
            cacheSize+=intSize+referenceSize;
            cacheSize+=intSize+intSize;
        }
        */
        return encodedMatrix.size()+cacheSize;
    }

    public void trim(){
        encodedMatrix.trim();
    }

    private void cacheQueue(){
        while(cacheQueue.size()>0){
            Pair<Quadrant,Integer> toCache = cacheQueue.removeLast();
            if(toCache.value==null){
                cache.improveItem(frameHash(toCache.key));
            }else{
                putIntoCache(toCache.key,toCache.value);
            }
        }
    }

    public E set(int r, int c, E data){
        if(data==null||r<0||c<0||r>=height()||c>=width()){
            throw new IllegalArgumentException("Invalid parameters");
        }
        Pair<Quadrant,Integer> frameInfo = decodeUntil(r,c,getClosestIndexFromCache(r,c,true));
        int dataIndex = frameInfo.value;
        Quadrant baseFrame = frameInfo.key;
        if(!header.defaultItem.equals(data)){
            if(encodedMatrix.getBit(dataIndex)){
                encodedMatrix.setBits(dataIndex+1,header.bitsPerData,data,bitEncoder);
            }else{
                MemoryController toAdd = encodeChunk(frameInfo.key,r,c,data,dataIndex);
                encodedMatrix.setBits(dataIndex+toAdd.size(),encodedMatrix.size()-dataIndex-1,encodedMatrix.getBits(dataIndex+1,encodedMatrix.size()-dataIndex-1));
                encodedMatrix.setBits(dataIndex,toAdd.size(),toAdd.getBits(0,toAdd.size()));
                while(baseFrame.parent!=null){
                    baseFrame = baseFrame.parent;
                    for(Quadrant frame : baseFrame.getChildrenAfter(r,c)){
                        int index = getIndexFromCache(frame,0);
                        if(index!=-1){
                            putIntoCache(frame,index+toAdd.size()-1);
                        }
                    }
                }
            }
        }else{
            if(encodedMatrix.getBit(dataIndex)){
                int removed = header.bitsPerData;
                int deleteStart = dataIndex+1, deleteEnd = dataIndex+removed+1;
                while(baseFrame.parent!=null){
                    baseFrame = baseFrame.parent;
                    int ignoreIndex = cacheQueue.getLast().value;
                    if(!hasData(baseFrame,ignoreIndex,removed,dataIndex-=ignoreIndex)){
                        int children = baseFrame.width==1||baseFrame.height==1?2:4;
                        deleteStart = Math.min(dataIndex+1,deleteStart);
                        deleteEnd = Math.max(dataIndex+1+removed+children,deleteEnd);
                        while(cacheQueue.getLast().key.parent==baseFrame){
                            cacheQueue.removeLast();
                        }
                        for(Quadrant frame : baseFrame.getChildren()){
                            putIntoCache(frame,-1);
                        }
                        removed+=children;
                    }else{
                        break;
                    }
                }
                encodedMatrix.delete(deleteStart,deleteEnd);
                encodedMatrix.setBit(deleteStart-1,false);
                while(baseFrame!=null){
                    for(Quadrant frame : baseFrame.getChildrenAfter(r,c)){
                        int index = getIndexFromCache(frame,0);
                        if(index!=-1){
                            putIntoCache(frame,index-removed);
                        }
                    }
                    baseFrame = baseFrame.parent;
                }
                //printBits();
            }
        }
        cacheQueue();
        return data;
    }

    public void printBits(){
        System.out.println(encodedMatrix.bitToString(header.headerSize));
    }

    private boolean hasData(Quadrant frame, int ignore, int size, int parentIndex){
        if(!encodedMatrix.getBit(parentIndex)){
            return false;
        }
        int iterations = frame.width==1||frame.height==1?2:4;
        for(int i = 1; i<=iterations;i++){
            if(i<ignore){
                if(encodedMatrix.getBit(parentIndex+i)){
                    return true;
                }
            }else if(i>ignore){
                if(encodedMatrix.getBit(parentIndex+size+i)){
                    return true;
                }
            }
        }
        return false;
    }

    private MemoryController encodeChunk(Quadrant baseFrame, int r, int c, E data, int dataIndex){
        MemoryController chunk = new MemoryController();
        MemoryController.MemoryBitOutputStream writer = chunk.outputStream();
        int parentIndex = dataIndex-getIndexFromCache(baseFrame,dataIndex-1);
        Quadrant currentFrame = baseFrame;
        while(currentFrame!=null&&baseFrame.contains(currentFrame)){
            if(currentFrame.contains(r,c)){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
                writer.writeBit(true);
                if(currentFrame.size()<=1){
                    writer.writeBits(header.bitsPerData,data,bitEncoder);
                    dataIndex+=header.bitsPerData;
                }
                currentFrame = currentFrame.getNext();
            }else{
                writer.writeBit(false);
                currentFrame = currentFrame.skipChildren();
            }
            dataIndex++;
        }
        return chunk;
    }

    private void putIntoCache(Quadrant frame, int index){
        if(frame.parent!=null&&frame.quadrant==0){
            return;
        }
        if(index>=0){
            cache.put(frameHash(frame),index);
        }else{
            cache.remove(frameHash(frame));
        }
    }

    public E get(int r, int c){
        if(r<0||c<0||r>=height()||c>=width()){
            throw new IllegalArgumentException("Invalid parameters");
        }
        int dataIndex = decodeUntil(r,c,getClosestIndexFromCache(r,c,true)).value;
        cacheQueue();
        if(dataIndex<encodedMatrix.size()&&encodedMatrix.getBit(dataIndex)){
            return encodedMatrix.getBits(dataIndex+1,header.bitsPerData,bitDecoder);
        }
        return header.defaultItem;
    }

    private Pair<Quadrant,Integer> decodeUntil(int r, int c, Pair<Quadrant,Integer> frameInfo){
        int dataIndex = frameInfo.value;
        Quadrant currentFrame = frameInfo.key;
        Quadrant goal = new Quadrant(r,c,1,1);
        int parentIndex = dataIndex-getIndexFromCache(currentFrame,dataIndex-1);
        while(dataIndex<encodedMatrix.size()&&!goal.equals(currentFrame)){
            boolean contains = currentFrame.contains(goal.yPos,goal.xPos);
            if(contains){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
            }
            if(encodedMatrix.getBit(dataIndex)){
                if(currentFrame.size()<=1){
                    dataIndex+=header.bitsPerData;
                }
                currentFrame = currentFrame.getNext();
            }else{
                if(currentFrame.contains(goal.yPos,goal.xPos)){
                    return new Pair<>(currentFrame,dataIndex);
                }
                currentFrame = currentFrame.skipChildren();
            }
            dataIndex++;
        }
        cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
        return new Pair<>(currentFrame,dataIndex);
    }

    private Pair<Quadrant,Integer> getClosestIndexFromCache(int r, int c, boolean doCache){
        Quadrant baseFrame = new Quadrant(0,0,height(),width());
        if(doCache){
            cacheQueue.add(new Pair<>(baseFrame,0));
        }
        int dataIndex = header.headerSize;
        while(true){
            Quadrant current = baseFrame.getChildContaining(r,c);
            int cacheIndex = getIndexFromCache(current,dataIndex);
            if(cacheIndex!=-1){
                dataIndex+=cacheIndex;
                if(current.size()==1){
                    return new Pair<>(current,dataIndex);
                }else{
                    if(doCache){
                        cacheQueue.add(new Pair<>(current,cacheIndex));
                    }
                    baseFrame = current;
                }
            }else{
                while(current.quadrant>0){
                    current = current.prevSibling();
                    cacheIndex = getIndexFromCache(current,dataIndex);
                    if(cacheIndex!=-1){
                        if(doCache){
                            cacheQueue.add(new Pair<>(current,cacheIndex));
                        }
                        dataIndex+=cacheIndex;
                        break;
                    }
                }
                if(cacheIndex==-1){
                    if(doCache){
                        cacheQueue.removeLast();
                    }
                    return new Pair<>(baseFrame,dataIndex);
                }
                return new Pair<>(current,dataIndex);
            }
        }
    }

    private int getIndexFromCache(Quadrant frame, int parentIndex, boolean doCache){
        int hash = frameHash(frame);
        if(doCache){
            cache.improveItem(hash);
        }
        if(frame.parent==null){
            return 0;
        }else if(frame.quadrant==0){
            return encodedMatrix.getBit(parentIndex)?1:-1;
        }
        Integer index = cache.getNoCache(hash);
        return index!=null?index:-1;
    }

    private int frameHash(Quadrant frame){
        return frame.yPos*width()+frame.xPos;
    }

    private int getIndexFromCache(Quadrant frame, int parentIndex){
        return getIndexFromCache(frame,parentIndex,false);
    }

    public int height(){
        return header.height;
    }

    public int width(){
        return header.width;
    }

    public E[][] toRawMatrix(){
        return QuadrantTreeEncoder.decodeMatrix(encodedMatrix,bitDecoder);
    }

    public Iterator<DataPoint<E>> iterator(){
        return iterator(IteratorType.DEFAULT);
    }

    public Iterator<DataPoint<E>> iterator(IteratorType type){
        return iterator(0,0,height(),width(),type);
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        Quadrant toIterate = new Quadrant(r,c,h,w);
        if(type==IteratorType.DEFAULT){
            return new TreeIterator<>(this, toIterate);
        }
        return new GenericIterator<>(this, toIterate,type);
    }

    private static class TreeIterator<V> implements Iterator<DataPoint<V>>{

        private final QuadrantTreeMatrix<V> matrix;
        private int index, readCount, defaultsRead;
        private Quadrant current;
        private final Quadrant readFrame;

        private TreeIterator(QuadrantTreeMatrix<V> matrix, Quadrant readFrame){
            if(matrix==null||readFrame==null||!new Quadrant(0,0, matrix.height(), matrix.width()).contains(readFrame)){
                throw new IllegalArgumentException("Illegal Arguments");
            }
            this.matrix = matrix;
            this.readFrame = readFrame;
            int r = readFrame.yPos, c = readFrame.xPos;
            Pair<Quadrant,Integer> frameInfo = matrix.getClosestIndexFromCache(r,c,true);
            frameInfo = matrix.decodeUntil(r,c,frameInfo);
            matrix.cacheQueue();
            current = frameInfo.key;
            index = frameInfo.value;
        }

        public boolean hasNext(){
            return readCount<readFrame.size();
        }

        public DataPoint<V> next(){
            if(!hasNext()){
                throw new NoSuchElementException("Iterator has no more elements");
            }
            MemoryController data = matrix.encodedMatrix;
            while(true){
                if(index>=data.size()||!data.getBit(index)){
                    int prevCount = defaultsRead;
                    defaultsRead++;
                    Quadrant prev = current;
                    if(defaultsRead==current.size()){
                        defaultsRead = 0;
                        index++;
                        current = current.skipChildren();
                    }
                    int dataR = prev.yPos+prevCount/prev.width;
                    int dataC = prev.xPos+prevCount%prev.width;
                    if(readFrame.contains(dataR,dataC)){
                        readCount++;
                        return new DataPoint<>(matrix.header.defaultItem,dataR,dataC);
                    }
                }else{
                    index++;
                    if(current.size()==1){
                        V datum = data.getBits(index,matrix.header.bitsPerData,matrix.bitDecoder);
                        index+=matrix.header.bitsPerData;
                        Quadrant prev = current;
                        current = current.getNext();
                        if(readFrame.contains(prev)){
                            readCount++;
                            return new DataPoint<>(datum,prev.yPos,prev.xPos);
                        }
                    }else{
                        current = current.getNext();
                    }
                }
            }
        }
    }
}