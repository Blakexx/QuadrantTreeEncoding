import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;

public class CachedTreeMatrix<E> implements Matrix<E>{
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
        nextRowInfo = getClosestIndexFromCache(0,0,false);
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
        Pair<StackFrame,Integer> frameInfo = decodeUntil(r,c,getClosestIndexFromCache(r,c,true));
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
        int dataIndex = decodeUntil(r,c,getClosestIndexFromCache(r,c,true)).value;
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
            boolean contains = currentFrame.contains(goal.yPos,goal.xPos);
            if(contains){
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

    private Pair<StackFrame,Integer> getClosestIndexFromCache(int r, int c, boolean doCache){
        StackFrame baseFrame = new StackFrame(0,0,height(),width());
        if(doCache){
            cacheQueue.add(new Pair<>(baseFrame,0));
        }
        int dataIndex = headerSize();
        while(true){
            StackFrame current = baseFrame.getChildContaining(r,c);
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

    private int nextRow = 0;
    private Pair<StackFrame,Integer> nextRowInfo;

    public E[] getRow(int r, Class<E> type){
        if(r<0||r>=height()){
            throw new IllegalArgumentException("Invalid Dimensions");
        }
        E[] ret = bulkGet(r,0,1,width(),type,nextRow==r&&nextRowInfo!=null?nextRowInfo:getClosestIndexFromCache(r,0,false))[0];
        nextRow++;
        if(nextRow==height()){
            nextRow = 0;
            nextRowInfo = getClosestIndexFromCache(0,0,false);
        }
        return ret;
    }

    public E[][] bulkGet(int r, int c, int height, int width, Class<E> type){
        StackFrame baseFrame = new StackFrame(0,0,height(),width());
        StackFrame readFrame = new StackFrame(r,c,height,width);
        if(!baseFrame.contains(readFrame)){
            throw new IllegalArgumentException("Invalid Dimensions");
        }
        return bulkGet(r,c,height,width,type,getClosestIndexFromCache(r,c,false));
    }

    private E[][] bulkGet(int r, int c, int height, int width, Class<E> type,Pair<StackFrame,Integer> frameInfo){
        StackFrame readFrame = new StackFrame(r,c,height,width);
        E[][] container = (E[][])Array.newInstance(type,height,width);
        StackFrame current = frameInfo.key;
        int index = frameInfo.value;
        int readCount = 0, toRead = height*width;
        E defItem = defaultItem();
        int bpd = bitsPerData();
        while(readCount<toRead){
            if(current.yPos==nextRow&&current.xPos==0){
                nextRowInfo = new Pair<>(new StackFrame(current.yPos,current.xPos,current.height,current.width,current.parent,current.quadrant),index);
            }
            if(encodedMatrix.getBit(index)){
                index++;
                if(current.size()==1){
                    int row = current.yPos, col = current.xPos;
                    if(readFrame.contains(row,col)){
                        E data = encodedMatrix.getBits(index,bpd,bitDecoder);
                        container[row-r][col-c] = data;
                        readCount++;
                    }
                    index+=bpd;
                }
                current = current.getNext();
            }else{
                index++;
                int rStart = Math.max(r,current.yPos), rEnd = Math.min(r+height,current.yPos+current.height);
                int cStart = Math.max(c,current.xPos), cEnd = Math.min(c+width,current.xPos+current.width);
                readCount+=Math.max(0,(rEnd-rStart)*(cEnd-cStart));
                for(;rStart<rEnd;rStart++){
                    for(;cStart<cEnd;cStart++){
                        container[rStart-r][cStart-c] = defItem;
                    }
                }
                current = current.skipChildren();
            }
        }
        return container;
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
            Pair<StackFrame,Integer> frameInfo = matrix.getClosestIndexFromCache(readFrame.yPos,readFrame.xPos,false);
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
}