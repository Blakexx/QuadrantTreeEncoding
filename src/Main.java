import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.lang.reflect.Array;
import java.util.function.*;
import java.util.stream.*;

public class Main {

    private static final double byteFactor = Math.pow(10,3); //Kilobytes
    private static final double timeFactor = Math.pow(10,6); //Milliseconds
    private static final String[] schemes = {"QTE","CRS","CCS","DEF","ZIP"};
    private static final boolean[] needsCache = {true,false,false,false,false};

    private static double bitsToFormat(long bits){
        return roundUpDiv(bits,8)/byteFactor;
    }

    public static void main(String[] args){
        Scanner in = new Scanner(System.in);
        System.out.println("1: Disk Size Tester");
        System.out.println("2: Read Tester");
        int type = readInt("Test Type",in,(i)->i>=1&&i<=2);
        System.out.println();
        if(type==1){
            System.out.println("1: QTE");
            System.out.println("2: QTE/Dense Hybrid");
            System.out.println("3: QTE/Dense/CRS Hybrid");
            System.out.println("4: CRS");
            System.out.println("5: CCS");
            System.out.println("6: ZIP");
            type = readInt("Encoding Type",in,(i)->i>=1&&i<=6);
            System.out.println();
            diskSizeTester(getEncoder(type));
        }else if(type==2){
            for(int i = 1; i<=schemes.length; i++){
                System.out.println(i+". "+schemes[i-1]+" Matrix");
            }
            type = readInt("Encoding Type",in,(i)->i>=1&&i<=schemes.length);
            System.out.println();
            System.out.println("1. Memory");
            System.out.println("2. Disk");
            int loc = readInt("Data Location",in,(i)->i>=1&&i<=2);
            System.out.println();
            double cachePercent = needsCache[type-1]?readDouble("Cache %",in,(i)->i>=0&&i<=1):0;
            System.out.println();
            readWriteTester(cachePercent, type, loc==2);
        }
    }

    private static MatrixEncoder<Byte> getEncoder(int type){
        BiFunction<Byte, Integer, byte[]> enc = (num, bitsPerData)->new byte[]{(byte)(num<<(8-bitsPerData))};
        BiFunction<byte[], Integer, Byte> dec = (bytes, bitsPerData)->(byte)(bytes[0]>>>(8-bitsPerData));
        return switch(type){
            case 1 -> new QuadrantTreeEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            );
            case 2 -> new DoubleHybridEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            );
            case 3 -> new TripleHybridEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            );
            case 4 -> new CRSEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            );
            case 5 -> new CCSEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            );
            case 6 -> new ZipEncoder<>(new DirectEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            ));
            default -> throw new IllegalArgumentException("Invalid Type");
        };
    }

    private static long runTest(Consumer<Matrix<Byte>> test, Matrix<Byte> matrix){
        long time = System.nanoTime();
        test.accept(matrix);
        return System.nanoTime()-time;
    }

    private static Matrix<Byte> getByteMatrix(double fullness, double cachePercent, int r, int c, int type, boolean onDisk){
        if(type<1 || type>schemes.length){
            throw new IllegalArgumentException("Invalid Type");
        }
        MemoryController controller = onDisk?new MemoryController(new File("matrices/matrix."+schemes[type-1].toLowerCase())):new MemoryController();
        Byte[][] matrix = generateMatrix(r,c,fullness);
        BiFunction<Byte, Integer, byte[]> encoder = (b,bpd)->new byte[]{b};
        BiFunction<byte[], Integer, Byte> decoder = (b,bpd)->b[0];
        MatrixEncoder<Byte> matrixEncoder = switch(type){
            case 1 -> new QuadrantTreeEncoder<>(matrix,8,encoder,decoder);
            case 2 -> new CRSEncoder<>(matrix,8,encoder,decoder);
            case 3 -> new CCSEncoder<>(matrix,8,encoder,decoder);
            case 4 -> new DirectEncoder<>(matrix,8,encoder,decoder);
            case 5 -> new ZipEncoder<>(new DirectEncoder<>(
                    matrix,8,encoder,decoder
            ));
            default -> null;
        };
        matrixEncoder.encodeMatrix(controller);
        return matrixEncoder.getMatrix(controller,cachePercent);
    }

    private static void runTests(List<Consumer<Matrix<Byte>>> toRun, int dim, double fullness, double cachePercent, int type, boolean onDisk, StringBuilder data){
        final int runsPerTest = 1;
        long avgBits = 0;
        double[] timeData = new double[toRun.size()];
        for(int i = 0; i<runsPerTest;i++){
            long encodeTime = System.nanoTime();
            Matrix<Byte> matrix = getByteMatrix(fullness,cachePercent,dim,dim,type,onDisk);
            encodeTime = System.nanoTime() - encodeTime;
            avgBits+=matrix.estimateBitSize();
            encodeTime = 0;
            for(int r = 0; r<toRun.size();r++){
                timeData[r] += runTest(toRun.get(r),matrix) + encodeTime;
            }
        }
        avgBits/=runsPerTest;
        StringBuilder tempString = new StringBuilder();
        tempString.append(dim*dim).append(" ").append(String.format("%.2f", fullness)).append(" ").append(String.format("%.2f", cachePercent)).append(" ").append(String.format("%.2f",bitsToFormat(avgBits)));
        for(int i = 0; i<timeData.length;i++){
            timeData[i]/=runsPerTest;
            timeData[i]/=timeFactor;
            tempString.append(" ").append(String.format("%.0f",timeData[i]));
        }
        System.out.println(tempString);
        tempString.append("\n");
        data.append(tempString);
    }

    public static void readWriteTester(double cachePercent, int type, boolean onDisk){
        List<Consumer<Matrix<Byte>>> toRun = Arrays.asList(
                Main::sequentialRowTest,
                Main::randomRowTest,
                Main::sequentialColTest,
                Main::randomColTest,
                Main::randomTest
        );
        StringBuilder data = new StringBuilder();
        String header = "Elements Fullness Cache_Size Total_Size SeqRow RanRow SeqCol RanCol Random";
        System.out.println("Size Test:");
        data.append("Size Test:\n");
        data.append(header);
        data.append("\n");
        System.out.println(header);
        for(int dim = 16; dim <= 1024;dim *= 2){
            runTests(toRun,dim,.3,cachePercent,type,onDisk,data);
        }
        System.out.println("\nFullness Test:");
        data.append("\nFullness Test:\n");
        data.append(header);
        data.append("\n");
        System.out.println(header);
        for(double fullness = .1; fullness<=.5; fullness+=.1){
            runTests(toRun,1024,fullness,cachePercent,type,onDisk,data);
        }
        try{
            File output = new File("runTimeResults/"+schemes[type-1]+".txt");
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter pw = new PrintWriter(output);
            pw.write(data.toString());
            pw.flush();
        }catch(Exception e){
            throw new RuntimeException("Could not write to file");
        }
    }

    private static void sequentialRowTest(Matrix<Byte> matrix){
        Iterator<DataPoint<Byte>> iterator = matrix.iterator(IteratorType.BY_ROW);
        while(iterator.hasNext()){
            iterator.next();
        }
    }

    private static void randomRowTest(Matrix<Byte> matrix){
        Random rand = new Random();
        List<Integer> rows = IntStream.rangeClosed(0,matrix.height()-1).boxed().collect(Collectors.toList());
        int width = matrix.width();
        while(rows.size()>0){
            int row = rows.remove(rand.nextInt(rows.size()));
            Iterator<DataPoint<Byte>> iterator = matrix.iterator(row,0,1,width,IteratorType.BY_ROW);
            while(iterator.hasNext()){
                iterator.next();
            }
        }
    }

    private static void sequentialColTest(Matrix<Byte> matrix){
        Iterator<DataPoint<Byte>> iterator = matrix.iterator(IteratorType.BY_COL);
        while(iterator.hasNext()){
            iterator.next();
        }
    }

    private static void randomColTest(Matrix<Byte> matrix){
        Random rand = new Random();
        List<Integer> cols = IntStream.range(0,matrix.width()).boxed().collect(Collectors.toList());
        int height = matrix.height();
        while(cols.size()>0){
            int col = cols.remove(rand.nextInt(cols.size()));
            Iterator<DataPoint<Byte>> iterator = matrix.iterator(0,col,height,1,IteratorType.BY_COL);
            while(iterator.hasNext()){
                iterator.next();
            }
        }
    }

    private static void randomTest(Matrix<Byte> matrix){
        Random rand = new Random();
        int height = matrix.height();
        int width = matrix.width();
        List<Integer> points = IntStream.range(0,height*width).boxed().collect(Collectors.toList());
        while(points.size()>0){
            int point = points.remove(rand.nextInt(points.size()));
            matrix.get(point/width,point%width);
        }
    }

    private static int readInt(String name, Scanner in, Function<Integer,Boolean> tester){
        Integer returned = null;
        do{
            System.out.print(name+": ");
            try{
                returned = in.nextInt();
            }catch(Exception e){
                in.nextLine();
            }
        }while(returned==null||!tester.apply(returned));
        return returned;
    }

    private static double readDouble(String name, Scanner in, Function<Double,Boolean> tester){
        Double returned = null;
        do{
            System.out.print(name+": ");
            try{
                returned = in.nextDouble();
            }catch(Exception e){
                in.nextLine();
            }
        }while(returned==null||!tester.apply(returned));
        return returned;
    }

    public static void diskSizeTester(MatrixEncoder<Byte> encoder){
        String formatName = encoder.getName();
        StringBuilder data = new StringBuilder("Rows Columns Fullness "+formatName+"_Size Data_Size Total_Size");
        System.out.println(data);
        data.append("\n");
        for(int d = 10; d<=1000;d*=10){
            Byte[][] matrix = null;
            for(double sparse = 0; sparse<=1;sparse+=sparse<=.09?.01:.1){
                long avgBits = 0;
                long averageTotal = 0;
                int runsPerSize = 3;
                MemoryController controller = new MemoryController(new File("encoded/"+d+"x"+d+"-"+String.format("%.0f",sparse*100)));
                for(int i = 0; i<runsPerSize;i++){
                    matrix = generateMatrix(d,d,sparse);
                    encoder.setMatrix(matrix);
                    encoder.encodeMatrix(controller);
                    Object[][] decoded = encoder.decodeMatrix(controller);
                    if(!equal(decoded,matrix)){
                        System.out.println("\nOriginal: ");
                        printMatrix(matrix);
                        System.out.println("\nDecoded: ");
                        printMatrix(decoded);
                        System.out.println("Encoding error...");
                        return;
                    }
                    avgBits+=encoder.refSize();
                    averageTotal+=controller.size();
                }
                try{
                    File dense = new File("dense/"+d+"x"+d+"-"+String.format("%.0f",sparse*100));
                    dense.getParentFile().mkdirs();
                    dense.createNewFile();
                    FileBitOutputStream stream = new FileBitOutputStream(dense,false);
                    for(int r = 0; r<matrix.length;r++){
                        for(int c = 0; c<matrix[r].length;c++){
                            stream.writeBits(8,new byte[]{matrix[r][c]});
                        }
                    }
                }catch(Exception e){
                    throw new RuntimeException("Could not write to file");
                }
                avgBits/=runsPerSize;
                averageTotal/=runsPerSize;
                String str = d+" "+d+" "+String.format("%.2f",sparse)+" "+String.format("%.2f",bitsToFormat(avgBits))+" "+String.format("%.2f",bitsToFormat(averageTotal));
                System.out.println(str);
                str+="\n";
                data.append(str);
            }
        }
        try{
            File output = new File("sizeResults/"+encoder.getName()+".txt");
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter pw = new PrintWriter(output);
            pw.write(data.toString());
            pw.flush();
        }catch(Exception e){
            throw new RuntimeException("Could not write to file");
        }
    }

    public static Byte[][] generateMatrix(int h, int w, double sparse){
        Random ran = new Random();
        return generateMatrix(h,w,sparse,(r,c)->(byte)(ran.nextInt(127)+1),(byte)0, Byte.class);
    }

    public static <T> T[][] generateMatrix(int h, int w, double sparse, BiFunction<Integer,Integer,T> generator, T defaultVal, Class<T> type){
        int numItems = (int)Math.round(h*w*sparse);
        ArrayList<int[]> points = new ArrayList<>();
        for(int r = 0; r<h;r++){
            for(int c = 0; c<w;c++){
                points.add(new int[]{r,c});
            }
        }
        Random ran = new Random();
        T[][] matrix = (T[][])Array.newInstance(type,h,w);
        for(int i = 0; i<numItems;i++){
            int[] point = points.remove(ran.nextInt(points.size()));
            matrix[point[0]][point[1]] = generator.apply(point[0],point[1]);
        }
        for(int[] point : points){
            matrix[point[0]][point[1]] = defaultVal;
        }
        return matrix;
    }

    public static <T> String matrixToString(T[][] matrix){
        String data = Arrays.deepToString(matrix);
        return data.substring(1,data.length()-1).replaceAll("\\], ","\\]\n");
    }

    public static <T> void printMatrix(T[][] matrix){
        System.out.println(matrixToString(matrix));
    }

    public static int logBaseCeil(int num, int base){
        return (int)Math.ceil(logBase(num,base));
    }

    public static boolean equal(Object[][] matrix1, Object[][] matrix2){
        for(int r = 0; r<matrix1.length;r++){
            for(int c = 0; c<matrix1[r].length;c++){
                if(!matrix1[r][c].equals(matrix2[r][c])){
                    return false;
                }
            }
        }
        return true;
    }

    public static int roundUpDiv(int num, int den){
        return (num + den - 1) / den;
    }

    public static long roundUpDiv(long num, long den){
        return (num + den - 1) / den;
    }

    public static double logBase(double num, double base){
        return Math.log(num)/Math.log(base);
    }
}