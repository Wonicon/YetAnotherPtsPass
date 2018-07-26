package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Hello {
  public static void main(String[] args) {
    Benchmark.alloc(1);
    A a = new A();
    Benchmark.alloc(2);
    A b = new A();
    Benchmark.alloc(3);
    A c = new A();
    Benchmark.alloc(5);
    B m = new B();
    Benchmark.alloc(6);
    A[] arr = new A[10];
    A d = foo(a, c, m, m);
    d = foo(a, b, m, m);
    if (args.length > 1) a = b;
    if (args.length < 0) c = a;
    arr[1] = c;
    Benchmark.test(1, a);
    Benchmark.test(2, b);
    Benchmark.test(3, c);
    Benchmark.test(5, d);
  }
    public static A foo(A a1, A a2, B b1, B b2) {
        Benchmark.alloc(4);
        A a = new A();
        if (b1 == b2) {
            a = a1;
        }
        if (a1 == a2) {
            a = a2;
        }
        Benchmark.test(4, a);
        return a;
    }
}
