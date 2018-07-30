package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Swap00{
  public static void main(String[] args) {
    Benchmark.alloc(3);
    A a[] = new A[1];
    Benchmark.alloc(4);
    A b[] = new A[1];

    Benchmark.alloc(1);
    a[0] = new A();
    Benchmark.alloc(2);
    b[0] = new A();

    swap(a, b);

    A c = a[0];
    A d = b[0];

    Benchmark.test(1, c);
    Benchmark.test(2, d);
  }
    public static void swap(A[] a1, A[] a2) {
        A b = a1[0];
        a1[0] = a2[0];
        a2[0] = b;
        return;
    }
}
