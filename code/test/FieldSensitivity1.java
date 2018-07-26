package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

/*
 * @testcase FieldSensitivity2
 * 
 * @version 1.0
 * 
 * @author Johannes Späth, Nguyen Quang Do Lisa (Secure Software Engineering Group, Fraunhofer
 * Institute SIT)
 * 
 * @description Field Sensitivity without static method
 */
public class FieldSensitivity1 {

  public static void main(String[] args) {

    Benchmark.alloc(1);
    B b = new B();
    A c = new A();

    Benchmark.alloc(2);
    B b1 = new B();
    A a = new A(b);
    a.f = b;

    Benchmark.alloc(3);
    B b3 = new B();
    c.f = b1

    Benchmark.alloc(4);
    B b4 = new B();

    c.f = a.f;

    B d = c.f;
    B e = a.f;



    Benchmark.test(1, d); // expected: 1
    Benchmark.test(2, a); // expected: 1

  }

}
