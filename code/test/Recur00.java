package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Recur00{
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a[] = new A[1];
        Benchmark.alloc(2);
        A b[] = new A[1];

        Benchmark.alloc(3);
        a[0] = new A();
        Benchmark.alloc(4);
        b[0] = new A();

        A c = guess(a, b, 7);

        Benchmark.test(1, a);
        Benchmark.test(2, b);
        Benchmark.test(3, c);
    }
    public static A guess(A[] a1, A[] a2, int x) {
        if (x == 0) {
            return a1[0];
        } else if (x == 1) {
            return a2[0];
        }
        return guess(a1, a2, x-2);
    }
}
