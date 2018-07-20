package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class ArrayTest{

    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = new A();

        Benchmark.alloc(2);
        A b[] = new A[3];

        Benchmark.alloc(3);
        b[2] = new A();

        Benchmark.alloc(4);
        A c = new A();

        if (args.length > 1) a = b[0];
        if (args.length < 0) c = a;

        b[2] = b[0];

        Benchmark.test(1, a);
        Benchmark.test(2, b);
        Benchmark.test(3, c);
    }
}
