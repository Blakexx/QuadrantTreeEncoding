import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
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
                int deleteStart = dataIndex+1, deleteEnd = dataIndex+bitsPerData()+1;
                while(baseFrame.parent!=null){
                    baseFrame = baseFrame.parent;
                    int ignoreIndex = cacheQueue.getLast().value;
                    if(!hasData(baseFrame,ignoreIndex,removed,dataIndex-=ignoreIndex)){
                        int children = baseFrame.xLen==1||baseFrame.yLen==1?2:4;
                        deleteStart = Math.min(dataIndex+1,deleteStart);
                        deleteEnd = Math.max(dataIndex+1+removed+children,deleteEnd);
                        while(cacheQueue.getLast().key.parent==baseFrame){
                            cacheQueue.removeLast();
                        }
                        LinkedList<StackFrame> frames = new LinkedList<>();
                        frames.add(baseFrame);
                        StackFrame.pushFrame(frames);
                        for(StackFrame frame : frames){
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
        int iterations = frame.xLen==1||frame.yLen==1?2:4;
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

    private MemoryController encodeChunk(StackFrame currentFrame, int r, int c, E data, int dataIndex){
        MemoryController chunk = new MemoryController();
        MemoryController.MemoryBitOutputStream writer = chunk.outputStream();
        LinkedList<StackFrame> frames = new LinkedList<>();
        frames.add(currentFrame);
        int parentIndex = dataIndex-getIndexFromCache(currentFrame,dataIndex-1);
        while(frames.size()>0){
            currentFrame = frames.getLast();
            if(currentFrame.contains(r,c)){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
                writer.writeBit(true);
                if(currentFrame.size()==1){
                    writer.writeBits(bitsPerData(),data,bitEncoder);
                    frames.removeLast();
                    dataIndex+=bitsPerData();
                }else{
                    StackFrame.pushFrame(frames);
                }
            }else{
                writer.writeBit(false);
                frames.removeLast();
            }
            dataIndex++;
        }
        return chunk;
    }

    private void putIntoCache(StackFrame frame, int index){
        if(frame.parent!=null&&frame.quadrant==0){
            return;
        }
        cache.put(frameHash(frame),index);
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

    private Pair<StackFrame,Integer> decodeUntil(int r, int c, FrameCollection collection){
        LinkedList<StackFrame> frames = collection.frames;
        int dataIndex = collection.index;
        StackFrame currentFrame = frames.getLast();
        StackFrame goal = new StackFrame(r,c,1,1);
        int parentIndex = dataIndex-getIndexFromCache(currentFrame,dataIndex-1);
        while(dataIndex<encodedMatrix.size()&&!goal.equals(currentFrame)){
            if(currentFrame.contains(goal.yOffset,goal.xOffset)){
                cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
                parentIndex = dataIndex;
            }
            if(encodedMatrix.getBit(dataIndex)){
                if(currentFrame.size()<=1){
                    frames.removeLast();
                    dataIndex+=bitsPerData();
                }else{
                    StackFrame.pushFrame(frames);
                }
            }else{
                if(currentFrame.contains(goal.yOffset,goal.xOffset)){
                    return new Pair<>(currentFrame,dataIndex);
                }
                frames.removeLast();
            }
            dataIndex++;
            currentFrame = frames.getLast();
        }
        cacheQueue.add(new Pair<>(currentFrame,dataIndex-parentIndex));
        return new Pair<>(currentFrame,dataIndex);
    }

    private FrameCollection getClosestIndexFromCache(int r, int c){
        StackFrame baseFrame = new StackFrame(0,0,height(),width());
        cacheQueue.add(new Pair<>(baseFrame,0));
        int dataIndex = headerSize();
        while(true){
            StackFrame current = baseFrame.getChildContaining(r,c);
            int cacheIndex = getIndexFromCache(current,dataIndex);
            if(cacheIndex!=-1){
                dataIndex+=cacheIndex;
                if(current.size()==1){
                    LinkedList<StackFrame> toReturn = new LinkedList<>();
                    toReturn.add(current);
                    return new FrameCollection(dataIndex,toReturn);
                }else{
                    cacheQueue.add(new Pair<>(current,cacheIndex));
                    baseFrame = current;
                }
            }else{
                LinkedList<StackFrame> toReturn = new LinkedList<>();
                toReturn.add(current);
                LinkedList<StackFrame> frames = baseFrame.getChildrenBefore(r,c);
                ListIterator<StackFrame> iterator = frames.listIterator(frames.size());
                while(iterator.hasPrevious()){
                    StackFrame frame = iterator.previous();
                    toReturn.add(frame);
                    cacheIndex = getIndexFromCache(frame,dataIndex);
                    if(cacheIndex!=-1){
                        cacheQueue.add(new Pair<>(frame,cacheIndex));
                        dataIndex+=cacheIndex;
                        break;
                    }
                }
                if(cacheIndex==-1){
                    toReturn = new LinkedList<>();
                    toReturn.add(baseFrame);
                    cacheQueue.removeLast();
                }
                return new FrameCollection(dataIndex,toReturn);
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
        return frame.yOffset*width()+frame.xOffset;
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

    private static class FrameCollection{
        private final LinkedList<StackFrame> frames;
        private final int index;
        private FrameCollection(int index, LinkedList<StackFrame> frames){
            this.index = index;
            this.frames = frames;
        }
        public String toString(){
            return index+" "+frames.toString();
        }
    }

    public Iterator<DataPoint<E>> iterator(){
        return new TreeIterator<>(this);
    }

    private static class TreeIterator<V> implements Iterator<DataPoint<V>>{

        private final CachedTreeMatrix<V> matrix;
        private int index, dataCount;
        private final LinkedList<StackFrame> frames;

        private TreeIterator(CachedTreeMatrix<V> matrix){
            this.matrix = matrix;
            frames = new LinkedList<>();
            frames.add(new StackFrame(0,0,matrix.height(),matrix.width()));
            index = matrix.headerSize();
        }

        public boolean hasNext(){
            return frames.size()>0;
        }

        public DataPoint<V> next(){
            if(!hasNext()){
                throw new NoSuchElementException("Iterator has no more elements");
            }
            MemoryController data = matrix.encodedMatrix;
            StackFrame current = frames.getLast();
            while(true){
                if(index>=data.size()||!data.getBit(index)){
                    int prevCount = dataCount;
                    dataCount++;
                    if(dataCount==current.size()){
                        dataCount = 0;
                        index++;
                        frames.removeLast();
                    }
                    return new DataPoint<>(matrix.defaultItem(),current.yOffset+prevCount/current.xLen,current.xOffset+prevCount%current.xLen);
                }else{
                    index++;
                    if(current.size()==1){
                        V datum = data.getBits(index,matrix.bitsPerData(),matrix.bitDecoder);
                        frames.removeLast();
                        index+=matrix.bitsPerData();
                        return new DataPoint<>(datum,current.yOffset,current.xOffset);
                    }else{
                        StackFrame.pushFrame(frames);
                    }
                }
                current = frames.getLast();
            }
        }
    }

    public Iterator<DataPoint<E>> genericIterator(int startR, int startC, BiConsumer<Point,Point> incrementer, BiPredicate<Point,Point> nextChecker){
        return new GenericIterator<>(this,startR,startC,incrementer, nextChecker);
    }

    private static class GenericIterator<V> implements Iterator<DataPoint<V>>{

        private final Object[][] cache;
        private final Iterator<DataPoint<V>> treeIterator;
        private final BiConsumer<Point,Point> incrementer;
        private final BiPredicate<Point,Point> nextChecker;
        private final Point point, dimensions;

        private GenericIterator(CachedTreeMatrix<V> matrix, int startR, int startC, BiConsumer<Point,Point> incrementer, BiPredicate<Point,Point> nextChecker){
            if(matrix==null||incrementer==null){
                throw new IllegalArgumentException("Cannot have null arguements");
            }
            treeIterator = matrix.iterator();
            cache = new Byte[matrix.height()][matrix.width()];
            this.incrementer = incrementer;
            point = new Point(startR,startC);
            dimensions = new Point(matrix.height(),matrix.width());
            this.nextChecker = nextChecker;
        }

        public boolean hasNext(){
            return nextChecker.test(point,dimensions);
        }

        public DataPoint<V> next(){
            if(!hasNext()){
                throw new NoSuchElementException("Iterator has no more elements");
            }
            int currentR = point.row, currentC = point.column;
            V data = (V)cache[currentR][currentC];
            if(data==null){
                while(treeIterator.hasNext()){
                    DataPoint<V> dataPoint = treeIterator.next();
                    cache[dataPoint.row][dataPoint.column] = dataPoint.data;
                    if(dataPoint.row==currentR&&dataPoint.column==currentC){
                        data = dataPoint.data;
                        break;
                    }
                }
            }
            incrementer.accept(point,dimensions);
            return new DataPoint<>(data,currentR,currentC);
        }
    }
}