import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

class GeneticAlg {
    static int dimension;
    static double[][] vertexes;
    static int populationLen = 15;
    static int tourneyLen = 3;
    static int mutationRatio = 5;   // value between 0 and 100
    static ArrayList<Ordered> routes = new ArrayList<>(populationLen);
    static ForkJoinPool pool = ForkJoinPool.commonPool();

    public static void main (String[] args) {
        Scanner scan = new Scanner(System.in);
        for (;;) {
            String line = scan.nextLine();
            if (line.contains("DIMENSION")) {
                String[] splits = line.split("\\s+");
                dimension = Integer.parseInt(splits[splits.length - 1]);
            }
            if (line.contains("NODE_COORD_SECTION"))
                break;
        }
        vertexes = new double[dimension][2];
        for (int i = 0; i < dimension; i++) {
            String line = scan.nextLine();
            String[] splits = line.split("\\s+");
            vertexes[i][0] = Double.parseDouble(splits[splits.length - 2]);
            vertexes[i][1] = Double.parseDouble(splits[splits.length - 1]);
        }
        for (int i = 0; i < populationLen; i++) {
            pool.submit(() -> {
                ArrayList<Integer> notVisited = new ArrayList<>(dimension);
                ArrayList<Integer> route = new ArrayList<>(dimension);
                for (int j = 0; j < dimension; j++) {
                    notVisited.add(j);
                }
                while (notVisited.size() > 0) {
                    int r = ThreadLocalRandom.current().nextInt(notVisited.size());
                    Integer v = notVisited.remove(r);
                    route.add(v);
                }
                double cost = computeRouteCost(route);
                synchronized (routes) {
                    routes.add(new Ordered(cost, route));
                }
            });
        }
        pool.awaitQuiescence(1, TimeUnit.DAYS);
        Collections.sort(routes);
        // set here the execution time for the program
        long instantToStop = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 7);

        for (int cnt = 0; cnt < 1001; cnt++) {
            SimpleDateFormat pattern = new SimpleDateFormat("HH:mm:ss");
            double sum = 0;
            for (Ordered r : routes)
                sum += r.cost;
            double average = sum / populationLen;
            System.out.println(pattern.format(new Date()) + "    " + cnt +
                    "    " + Math.round(routes.get(0).cost) +
                    "    " + Math.round(average));
            if (System.currentTimeMillis() >= instantToStop)
                break;

            ArrayList<Ordered> children = new ArrayList<>(populationLen);
            for (int i = 0; i < populationLen; i++) {
                pool.submit(() -> {
                    int[] tourney = new int[tourneyLen];
                    int[] parents = new int[2];
                    for (int j = 0; j < 2; j++) {
                        for (int k = 0; k < tourney.length; k++) {
                            tourney[k] = ThreadLocalRandom.current().nextInt(populationLen);
                        }
                        Arrays.sort(tourney);
                        parents[j] = tourney[0];
                    }
                    ArrayList<Integer> parent1 = routes.get(parents[0]).route;
                    ArrayList<Integer> parent2 = routes.get(parents[1]).route;
                    int r1 = ThreadLocalRandom.current().nextInt(dimension);
                    int r2 = ThreadLocalRandom.current().nextInt(dimension);
                    int begin = Math.min(r1, r2);
                    int end = Math.max(r1, r2);
                    HashSet<Integer> vertexesFromP1 = new HashSet<>(end - begin);
                    for (int k = begin; k < end; k++) {
                        vertexesFromP1.add(parent1.get(k));
                    }
                    ArrayList<Integer> child = new ArrayList<>(dimension);
                    int k = 0;
                    for (; child.size() < begin; k++) {
                        Integer v = parent2.get(k);
                        if (vertexesFromP1.contains(v))
                            continue;
                        child.add(v);
                    }
                    for (int kk = begin; kk < end; kk++) {
                        child.add(parent1.get(kk));
                    }
                    for (; k < parent1.size(); k++) {
                        Integer v = parent2.get(k);
                        if (vertexesFromP1.contains(v))
                            continue;
                        child.add(v);
                    }
                    for (int i1 = 0; i1 < child.size(); i1++) {
                        if (ThreadLocalRandom.current().nextInt(100) < mutationRatio) {
                            int i2 = ThreadLocalRandom.current().nextInt(child.size());
                            Collections.swap(child, i1, i2);
                        }
                    }
                    pairSwapImpr(child);
                    double cost = computeRouteCost(child);
                    synchronized (children) {
                        children.add(new Ordered(cost, child));
                    }
                });
            }
            pool.awaitQuiescence(7, TimeUnit.DAYS);
            Collections.sort(children);
            for (Ordered c : children) {
                int i = Collections.binarySearch(routes, c);
                if (i < 0) i = Math.abs(i) - 1;
                if (i == routes.size())
                    break;
                routes.add(i, c);
                routes.remove(routes.size() - 1);
            }
        }
    }

    static class Ordered implements Comparable<Ordered> {
        final double cost;
        final ArrayList<Integer> route;
        Ordered(double c, ArrayList<Integer> r) {
            cost = c;
            route = r;
        }
        @Override
        public int compareTo(Ordered o) {
            return Double.compare(this.cost, o.cost);
        }
    }

    static void pairSwapImpr(ArrayList<Integer> route) {
        double cost = computeRouteCost(route);
        for (;;) {
            double prevCost = cost;
            pairSwapBegin:
            for (int i = 0; i < route.size() - 1; i++) {
                for (int j = 1; j < route.size(); j++) {
                    if (i == j) continue;
                    boolean consecutive = Math.abs(i - j) == 1;
                    boolean arrayBorder = i == 0 && j == route.size() - 1;
                    Integer prevVi = i - 1 >= 0 ? route.get(i - 1) : route.get(route.size() - 1);
                    Integer nextVi = route.get(i + 1);
                    Integer prevVj = route.get(j - 1);
                    Integer nextVj = j + 1 < route.size() ? route.get(j + 1) : route.get(0);
                    Integer vi = route.get(i);
                    Integer vj = route.get(j);
                    double[] costsToRemove = consecutive ? new double[2] : new double[4];
                    double[] costsToAdd = consecutive ? new double[2] : new double[4];

                    if (consecutive && i < j) {
                        costsToRemove[0] = Point2D.distance(vertexes[prevVi][0], vertexes[prevVi][1],
                                vertexes[vi][0], vertexes[vi][1]);
                        costsToRemove[1] = Point2D.distance(vertexes[nextVj][0], vertexes[nextVj][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[0] = Point2D.distance(vertexes[prevVi][0], vertexes[prevVi][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[1] = Point2D.distance(vertexes[nextVj][0], vertexes[nextVj][1],
                                vertexes[vi][0], vertexes[vi][1]);
                    }
                    else if ((consecutive && i > j) || arrayBorder) {
                        costsToRemove[0] = Point2D.distance(vertexes[nextVi][0], vertexes[nextVi][1],
                                vertexes[vi][0], vertexes[vi][1]);
                        costsToRemove[1] = Point2D.distance(vertexes[prevVj][0], vertexes[prevVj][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[0] = Point2D.distance(vertexes[nextVi][0], vertexes[nextVi][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[1] = Point2D.distance(vertexes[prevVj][0], vertexes[prevVj][1],
                                vertexes[vi][0], vertexes[vi][1]);
                    }
                    else {
                        costsToRemove[0] = Point2D.distance(vertexes[prevVi][0], vertexes[prevVi][1],
                                vertexes[vi][0], vertexes[vi][1]);
                        costsToRemove[1] = Point2D.distance(vertexes[nextVi][0], vertexes[nextVi][1],
                                vertexes[vi][0], vertexes[vi][1]);
                        costsToRemove[2] = Point2D.distance(vertexes[prevVj][0], vertexes[prevVj][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToRemove[3] = Point2D.distance(vertexes[nextVj][0], vertexes[nextVj][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[0] = Point2D.distance(vertexes[prevVi][0], vertexes[prevVi][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[1] = Point2D.distance(vertexes[nextVi][0], vertexes[nextVi][1],
                                vertexes[vj][0], vertexes[vj][1]);
                        costsToAdd[2] = Point2D.distance(vertexes[prevVj][0], vertexes[prevVj][1],
                                vertexes[vi][0], vertexes[vi][1]);
                        costsToAdd[3] = Point2D.distance(vertexes[nextVj][0], vertexes[nextVj][1],
                                vertexes[vi][0], vertexes[vi][1]);
                    }
                    double costWithSwaps = cost;
                    for (double c : costsToRemove)
                        costWithSwaps -= c;
                    for (double c : costsToAdd)
                        costWithSwaps += c;
                    if (Math.round(costWithSwaps) < Math.round(cost)) {
                        Collections.swap(route, i, j);
                        cost = costWithSwaps;
                        break pairSwapBegin;
                    }
                }
            }
            if (prevCost == cost)
                break;
        }
    }

    static double computeRouteCost(ArrayList<Integer> route) {
        double cost = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            Integer curr = route.get(i);
            Integer next = route.get(i + 1);
            cost += Point2D.distance(vertexes[curr][0], vertexes[curr][1],
                    vertexes[next][0], vertexes[next][1]);
        }
        Integer curr = route.get(route.size() - 1);
        Integer next = route.get(0);
        cost += Point2D.distance(vertexes[curr][0], vertexes[curr][1],
                vertexes[next][0], vertexes[next][1]);
        return cost;
    }
}