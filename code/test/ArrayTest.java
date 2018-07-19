package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class ArrayTest{

    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = new A();

        Benchmark.alloc(2);
        A b[] = new A[3];

        Benchmark.alloc(3);
        A c = new A();

        if (args.length > 1) a = b[0];
        if (args.length < 0) c = a;

        Benchmark.test(1, a);
        Benchmark.test(2, b);
        Benchmark.test(3, c);
    }
}
