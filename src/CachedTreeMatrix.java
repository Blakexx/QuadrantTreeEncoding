import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class CachedTreeMatrix<E> implements Iterable<DataPoint<E>>{
    private final MemoryController encodedMatrix;
    private final CacheManager<Integer,Integer> cache;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final LinkedList<Pair<StackFrame,Integer>> cacheQueue;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;
    public final double cachePercent;

    public CachedTreeMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, double cachePercent){
        this.cachePercent = cachePercent;
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        StackFrame baseFrame = new StackFrame(0,0,height(),width());
        cache = new LRUCache<>((int)Math.round(baseFrame.size()*cachePercent));
        cacheQueue = new LinkedList<>();
        putIntoCache(baseFrame,0);
        trim();
    }

    public CachedTreeMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, double cachePercent){
        this(new QuadrantTreeEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(),bitEncoder,bitDecoder,cachePercent);
    }

    public int estimateBitSize(){
        int cacheSize = 0, referenceSize = 32, intSize = 32;
        for(Integer key : cache.keySet()){
            cacheSize+=referenceSize+referenceSize;
            cacheSize+=referenceSize+intSize;
        }
        return encodedMatrix.size()+cacheSize;
    }

    public void trim(){
        encodedMatrix.trim();
    }

    private void cacheQueue(){
        while(cacheQueue.size()>0){
            Pair<StackFrame,Integer> toCache = cacheQueue.removeLast();
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
        Pair<StackFrame,Integer> frameInfo = decodeUntil(r,c,getClosestIndexFromCache(r,c));
        int dataIndex = frameInfo.value;
        StackFrame baseFrame = frameInfo.key;
        if(!defaultItem().equals(data)){
            if(encodedMatrix.getBit(dataIndex)){
                encodedMatrix.setBits(dataIndex+1,bitsPerData(),data,bitEncoder);
            }else{
                MemoryController toAdd = encodeChunk(frameInfo.key,r,c,data,dataIndex);
                encodedMatrix.setBits(dataIndex+toAdd.size(),encodedMatrix.size()-dataIndex-1,encodedMatrix.getBits(dataIndex+1,encodedMatrix.size()-dataIndex-1));
                encodedMatrix.setBits(dataIndex,toAdd.size(),toAdd.getBits(0,toAdd.size()));
                while(baseFrame.parent!=null){
                    baseFrame = baseFrame.parent;
                    for(StackFrame frame : baseFrame.getChildrenAfter(r,c)){
                        int index = getIndexFromCache(frame,0);
                        if(index!=-1){
                            putIntoCache(frame,index+toAdd.size()-1);
                        }
                    }
                }
            }
        }else{
            if(encodedMatrix.getBit(dataIndex)){
                int removed = bitsPerData();
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
                        for(StackFrame frame : baseFrame.getChildren()){
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
                    for(StackFrame frame : baseFrame.getChildrenAfter(r,c)){
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
        System.out.println(encodedMatrix.bitToString(headerSize()));
    }

    private boolean hasData(StackFrame frame, int ignore, int size, int parentIndex){
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

    private MemoryController encodeChunk(StackFrame baseFrame, int r, int c, E data, int dataIndex){
        MemoryController chunk = new MemoryController();
        MemoryController.MemoryBitOutputStream writer = chunk.outputStream();
        int parentIndex = dataIndex-getIndexFromCache(baseFrame,dataIndex-1);
        StackFrame currentFrame = baseFrame;
        while(currentFrame!=null&&baseFrame.contains(currentFrame)){
            if(currentFrame.contains(r,c)){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
                writer.writeBit(true);
                if(currentFrame.size()<=1){
                    writer.writeBits(bitsPerData(),data,bitEncoder);
                    dataIndex+=bitsPerData();
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

    private void putIntoCache(StackFrame frame, int index){
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
        int dataIndex = decodeUntil(r,c,getClosestIndexFromCache(r,c)).value;
        cacheQueue();
        if(dataIndex<encodedMatrix.size()&&encodedMatrix.getBit(dataIndex)){
            return encodedMatrix.getBits(dataIndex+1,bitsPerData(),bitDecoder);
        }
        return defaultItem();
    }

    private Pair<StackFrame,Integer> decodeUntil(int r, int c, Pair<StackFrame,Integer> frameInfo){
        int dataIndex = frameInfo.value;
        StackFrame currentFrame = frameInfo.key;
        StackFrame goal = new StackFrame(r,c,1,1);
        int parentIndex = dataIndex-getIndexFromCache(currentFrame,dataIndex-1);
        while(dataIndex<encodedMatrix.size()&&!goal.equals(currentFrame)){
            if(currentFrame.contains(goal.yPos,goal.xPos)){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
            }
            if(encodedMatrix.getBit(dataIndex)){
                if(currentFrame.size()<=1){
                    dataIndex+=bitsPerData();
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

    private Pair<StackFrame,Integer> getClosestIndexFromCache(int r, int c){
        StackFrame baseFrame = new StackFrame(0,0,height(),width());
        cacheQueue.add(new Pair<>(baseFrame,0));
        int dataIndex = headerSize();
        while(true){
            StackFrame current = baseFrame.getChildContaining(r,c);
            int cacheIndex = getIndexFromCache(current,dataIndex);
            if(cacheIndex!=-1){
                dataIndex+=cacheIndex;
                if(current.size()==1){
                    return new Pair<>(current,dataIndex);
                }else{
                    cacheQueue.add(new Pair<>(current,cacheIndex));
                    baseFrame = current;
                }
            }else{
                while(current.quadrant>0){
                    current = current.prevSibling();
                    cacheIndex = getIndexFromCache(current,dataIndex);
                    if(cacheIndex!=-1){
                        cacheQueue.add(new Pair<>(current,cacheIndex));
                        dataIndex+=cacheIndex;
                        break;
                    }
                }
                if(cacheIndex==-1){
                    cacheQueue.removeLast();
                    return new Pair<>(baseFrame,dataIndex);
                }
                return new Pair<>(current,dataIndex);
            }
        }
    }

    private int getIndexFromCache(StackFrame frame, int parentIndex, boolean doCache){
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

    private int frameHash(StackFrame frame){
        return frame.yPos*width()+frame.xPos;
    }

    private int getIndexFromCache(StackFrame frame, int parentIndex){
        return getIndexFromCache(frame,parentIndex,false);
    }

    public int bitsPerData(){
        return encodedMatrix.getBits(0,8,intDecoder);
    }

    public E defaultItem(){
        return encodedMatrix.getBits(8,bitsPerData(),bitDecoder);
    }

    private int bitsPerHeight(){
        return encodedMatrix.getBits(8+bitsPerData(),5,intDecoder)+1;
    }

    public int height(){
        return encodedMatrix.getBits(8+bitsPerData()+5,bitsPerHeight(),intDecoder);
    }

    private int bitsPerWidth(){
        return encodedMatrix.getBits(8+bitsPerData()+5+bitsPerHeight(),5,intDecoder)+1;
    }

    public int width(){
        return encodedMatrix.getBits(8+bitsPerData()+5+bitsPerHeight()+5,bitsPerWidth(),intDecoder);
    }

    private int headerSize(){
        return 8+bitsPerData()+5+bitsPerHeight()+5+bitsPerWidth();
    }

    public int size(){
        return height()*width();
    }

    public E[][] toRawMatrix() throws IOException {
        return QuadrantTreeEncoder.decodeMatrix(encodedMatrix.inputStream(),bitDecoder);
    }

    public String toString(){
        Object[][] mat;
        try{
            mat = toRawMatrix();
        }catch(Exception e){
            return e.toString();
        }
        return Main.matrixToString(mat);
    }

    public enum IteratorType{
        BY_ROW,
        BY_COL,
        DEFAULT
    }

    public Iterator<DataPoint<E>> iterator(){
        return iterator(IteratorType.DEFAULT);
    }

    public Iterator<DataPoint<E>> iterator(IteratorType type){
        return iterator(0,0,height(),width(),type);
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        StackFrame toIterate = new StackFrame(r,c,h,w);
        if(type==IteratorType.DEFAULT){
            return new TreeIterator<>(this, toIterate);
        }
        return new GenericIterator<>(this, toIterate,type);
    }

    private static class TreeIterator<V> implements Iterator<DataPoint<V>>{

        private final CachedTreeMatrix<V> matrix;
        private int index, readCount, defaultsRead;
        private StackFrame current;
        private final StackFrame readFrame;

        private TreeIterator(CachedTreeMatrix<V> matrix, StackFrame readFrame){
            if(matrix==null||readFrame==null||!new StackFrame(0,0, matrix.height(), matrix.width()).contains(readFrame)){
                throw new IllegalArgumentException("Illegal Arguments");
            }
            this.matrix = matrix;
            this.readFrame = readFrame;
            Pair<StackFrame,Integer> frameInfo = matrix.getClosestIndexFromCache(readFrame.yPos,readFrame.xPos);
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
                    StackFrame prev = current;
                    if(defaultsRead==current.size()){
                        defaultsRead = 0;
                        index++;
                        current = current.skipChildren();
                    }
                    if(readFrame.contains(prev)){
                        readCount++;
                        return new DataPoint<>(matrix.defaultItem(),prev.yPos+prevCount/prev.width,prev.xPos+prevCount%prev.width);
                    }
                }else{
                    index++;
                    if(current.size()==1){
                        V datum = data.getBits(index,matrix.bitsPerData(),matrix.bitDecoder);
                        index+=matrix.bitsPerData();
                        StackFrame prev = current;
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

    private static class GenericIterator<V> implements Iterator<DataPoint<V>>{

        private final HashMap<Integer,V> cache;
        private final Iterator<DataPoint<V>> treeIterator;
        private int readCount, currentR, currentC;
        private final StackFrame readFrame;
        private final IteratorType type;

        private GenericIterator(CachedTreeMatrix<V> matrix, StackFrame readFrame, IteratorType type){
            if(matrix==null||readFrame==null||type==null){
                throw new IllegalArgumentException("Cannot have null arguments");
            }
            treeIterator = matrix.iterator(readFrame.yPos,readFrame.xPos,readFrame.height,readFrame.width,IteratorType.DEFAULT);
            cache = new HashMap<>();
            this.readFrame = readFrame;
            this.type = type;
            currentR = readFrame.yPos;
            currentC = readFrame.xPos;
        }

        public boolean hasNext(){
            return readCount<readFrame.size();
        }

        public DataPoint<V> next(){
            if(!hasNext()){
                throw new NoSuchElementException("Iterator has no more elements");
            }
            int rawIndex =  currentR * readFrame.width + currentC;
            V data = cache.get(rawIndex);
            if(data==null){
                while(treeIterator.hasNext()){
                    DataPoint<V> dataPoint = treeIterator.next();
                    int row = dataPoint.row;
                    int col = dataPoint.column;
                    if(row==currentR&&col==currentC){
                        data = dataPoint.data;
                        break;
                    }
                    cache.put(row*readFrame.width+col, dataPoint.data);
                }
            }else{
                cache.remove(rawIndex);
            }
            int prevR = currentR, prevC = currentC;
            if(type==IteratorType.BY_ROW){
                currentC++;
                if(currentC==readFrame.xPos+readFrame.width){
                    currentC = readFrame.xPos;
                    currentR++;
                }
            }else if(type==IteratorType.BY_COL){
                currentR++;
                if(currentR==readFrame.yPos+readFrame.height){
                    currentR = readFrame.yPos;
                    currentC++;
                }
            }
            readCount++;
            return new DataPoint<>(data,prevR,prevC);
        }
    }
}