import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
 
public class Main {
 
    // Each thread generates 'samples' random points and counts
    // how many fall inside the unit quarter-circle (x^2 + y^2 <= 1)
    static class MonteCarloTask implements Callable<Long> {
        private final long samples;
        private final long seed;
 
        MonteCarloTask(long samples, long seed) {
            this.samples = samples;
            this.seed    = seed;
        }
 
        @Override
        public Long call() {
            Random rng = new Random(seed);  // thread-local RNG (no sharing)
            long insideCount = 0;
            for (long i = 0; i < samples; i++) {
                double x = rng.nextDouble(); // [0, 1)
                double y = rng.nextDouble(); // [0, 1)
                if (x * x + y * y <= 1.0)   // inside quarter circle
                    insideCount++;
            }
            return insideCount;
        }
    }
 
    // Sequential Monte Carlo Pi estimation
    static double sequentialPi(long totalSamples) {
        Random rng = new Random(42);
        long inside = 0;
        for (long i = 0; i < totalSamples; i++) {
            double x = rng.nextDouble();
            double y = rng.nextDouble();
            if (x * x + y * y <= 1.0) inside++;
        }
        return 4.0 * inside / totalSamples;
    }
 
    // Parallel Monte Carlo Pi estimation using Fork/Join style ExecutorService
    static double parallelPi(long totalSamples, int numThreads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        List<Future<Long>> futures = new ArrayList<>();
 
        long samplesPerThread = totalSamples / numThreads;
 
        // Submit one task per thread with a unique seed
        for (int t = 0; t < numThreads; t++) {
            long seed = 42L + t * 1000L;  // unique seed per thread
            futures.add(pool.submit(new MonteCarloTask(samplesPerThread, seed)));
        }
 
        // Aggregate inside counts from all threads
        long totalInside = 0;
        for (Future<Long> f : futures)
            totalInside += f.get();
 
        pool.shutdown();
        return 4.0 * totalInside / (samplesPerThread * numThreads);
    }
 
    public static void main(String[] args) throws Exception {
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.println("Available processors : " + threads);
        System.out.println("Actual Pi            : " + Math.PI);
        System.out.println();
        System.out.printf("%-14s %-14s %-14s %-14s %-12s %-14s%n",
            "Samples", "Seq Pi", "Par Pi",
            "Seq(ms)", "Par(ms)", "Speedup");
        System.out.println("-".repeat(84));
 
        long[] sampleSizes = {1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L};
 
        for (long total : sampleSizes) {
            // Sequential
            long t1 = System.currentTimeMillis();
            double seqPi = sequentialPi(total);
            long seqTime = System.currentTimeMillis() - t1;
 
            // Parallel
            long t2 = System.currentTimeMillis();
            double parPi = parallelPi(total, threads);
            long parTime = System.currentTimeMillis() - t2;
 
            double speedup = (double) seqTime / Math.max(parTime, 1);
 
            System.out.printf("%-14s %-14.8f %-14.8f %-14d %-12d %-14.2f%n",
                String.format("%,d", total),
                seqPi, parPi, seqTime, parTime, speedup);
        }
    }
}