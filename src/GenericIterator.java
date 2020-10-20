import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class GenericIterator<V> implements Iterator<DataPoint<V>> {
    private final HashMap<Integer,V> cache;
    private final Iterator<DataPoint<V>> defaultIterator;
    private int readCount, currentR, currentC;
    private final StackFrame readFrame;
    private final IteratorType type;

    public GenericIterator(Matrix<V> matrix, StackFrame readFrame, IteratorType type){
        if(matrix==null||readFrame==null||type==null){
            throw new IllegalArgumentException("Cannot have null arguments");
        }
        if(type==IteratorType.DEFAULT){
            throw new IllegalArgumentException("Cannot use default type in generic iterator");
        }
        defaultIterator = matrix.iterator(readFrame.yPos,readFrame.xPos,readFrame.height,readFrame.width,IteratorType.DEFAULT);
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
            while(defaultIterator.hasNext()){
                DataPoint<V> dataPoint = defaultIterator.next();
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