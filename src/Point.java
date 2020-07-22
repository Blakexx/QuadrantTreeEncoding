class Point{

    public int row,column;

    public Point(int row, int column){
        this.row = row;
        this.column = column;
    }

    public String toString(){
        return "("+row+","+column+")";
    }
}