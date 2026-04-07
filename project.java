import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    // ================= TASK =================
    static class MonteCarloTask implements Callable<Long> {
        private final long samples;
        private final long seed;
        private final List<double[]> data;
        private final boolean useDataset;

        // Random constructor
        MonteCarloTask(long samples, long seed) {
            this.samples = samples;
            this.seed = seed;
            this.data = null;
            this.useDataset = false;
        }

        // Dataset constructor
        MonteCarloTask(List<double[]> data) {
            this.data = data;
            this.samples = 0;
            this.seed = 0;
            this.useDataset = true;
        }

        @Override
        public Long call() {
            long inside = 0;

            if (useDataset) {
                for (double[] p : data) {
                    double x = p[0];
                    double y = p[1];
                    if (x * x + y * y <= 1.0)
                        inside++;
                }
            } else {
                Random rng = new Random(seed);
                for (long i = 0; i < samples; i++) {
                    double x = rng.nextDouble();
                    double y = rng.nextDouble();
                    if (x * x + y * y <= 1.0)
                        inside++;
                }
            }
            return inside;
        }
    }

    // ================= LOAD DATASET =================
    static List<double[]> loadData(String filePath) {
        List<double[]> data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                data.add(new double[]{x, y});
            }

            System.out.println("Dataset loaded: " + data.size() + " points");

        } catch (Exception e) {
            System.out.println("Dataset not found. Using RANDOM mode.");
        }

        return data;
    }

    // ================= SEQUENTIAL =================
    static double sequentialPi(long samples) {
        Random rng = new Random(42);
        long inside = 0;

        for (long i = 0; i < samples; i++) {
            double x = rng.nextDouble();
            double y = rng.nextDouble();
            if (x * x + y * y <= 1.0)
                inside++;
        }
        return 4.0 * inside / samples;
    }

    // ================= PARALLEL =================
    static double parallelPi(long samples, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Long>> futures = new ArrayList<>();

        long perThread = samples / threads;

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new MonteCarloTask(perThread, 42 + i)));
        }

        long inside = 0;
        for (Future<Long> f : futures)
            inside += f.get();

        pool.shutdown();
        return 4.0 * inside / (perThread * threads);
    }

    // ================= DATASET PARALLEL =================
    static double datasetParallel(List<double[]> data, int threads) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Long>> futures = new ArrayList<>();

        int chunk = data.size() / threads;

        for (int i = 0; i < threads; i++) {
            int start = i * chunk;
            int end = (i == threads - 1) ? data.size() : start + chunk;
            futures.add(pool.submit(new MonteCarloTask(data.subList(start, end))));
        }

        long inside = 0;
        for (Future<Long> f : futures)
            inside += f.get();

        pool.shutdown();
        return 4.0 * inside / data.size();
    }

    // ================= MAIN =================
    public static void main(String[] args) throws Exception {

        int threads = Runtime.getRuntime().availableProcessors();
        String filePath = "monte_carlo_pi_dataset.csv";

        List<double[]> data = loadData(filePath);

        System.out.println("Available processors : " + threads);
        System.out.println("Actual Pi            : " + Math.PI);
        System.out.println();

        System.out.printf("%-14s %-14s %-14s %-14s %-12s %-14s%n",
                "Samples", "Seq Pi", "Par Pi", "Seq(ms)", "Par(ms)", "Speedup");

        System.out.println("------------------------------------------------------------------------------------");

        long[] sampleSizes = {
                1_000_000L,
                10_000_000L,
                100_000_000L,
                1_000_000_000L
        };

        for (long total : sampleSizes) {

            double seqPi, parPi;
            long seqTime, parTime;

            // 🔥 If dataset is large enough → use dataset
            if (data.size() >= total) {

                List<double[]> subset = data.subList(0, (int) total);

                long t1 = System.currentTimeMillis();
                long inside = 0;
                for (double[] p : subset)
                    if (p[0]*p[0] + p[1]*p[1] <= 1.0) inside++;
                seqPi = 4.0 * inside / subset.size();
                seqTime = System.currentTimeMillis() - t1;

                long t2 = System.currentTimeMillis();
                parPi = datasetParallel(subset, threads);
                parTime = System.currentTimeMillis() - t2;

            } else {
                // 🔥 FALLBACK → RANDOM MODE
                long t1 = System.currentTimeMillis();
                seqPi = sequentialPi(total);
                seqTime = System.currentTimeMillis() - t1;

                long t2 = System.currentTimeMillis();
                parPi = parallelPi(total, threads);
                parTime = System.currentTimeMillis() - t2;
            }

            double speedup = (double) seqTime / Math.max(parTime, 1);

            System.out.printf("%-14s %-14.8f %-14.8f %-14d %-12d %-14.2f%n",
                    String.format("%,d", total),
                    seqPi,
                    parPi,
                    seqTime,
                    parTime,
                    speedup);
        }
    }
}
