package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class ArrayTest1{

    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a[] = new A[4];

        Benchmark.alloc(2);
        A b = new A();

        a[2] = b;

        Benchmark.alloc(3);
        A c = new A();

        Benchmark.alloc(4);
        A d[] = new A[4];

        A e = a[1];

        if (args.length > 1) d[1] = c;
        if (args.length < 0) d[1] = a[0];
        b = d[3];
        c = a[0];

        Benchmark.test(1, a);
        Benchmark.test(2, b);
        Benchmark.test(3, c);
        Benchmark.test(4, d);
    }
}
