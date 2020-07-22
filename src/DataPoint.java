class DataPoint<E>{
    public final E data;
    public final int row,column;

    public DataPoint(E data, int row, int column){
        this.data = data;
        this.row = row;
        this.column = column;
    }

    public String toString(){
        return "("+row+","+column+")="+data;
    }
}