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
public class FieldSensitivity1
{

    public static void main(String[] args)
    {

        Benchmark.alloc(1);
        B b = new B();

        Benchmark.alloc(2);
        A c = new A();

        Benchmark.alloc(3);
        B b1 = new B();

        Benchmark.alloc(4);
        A a = new A(b);

        Benchmark.alloc(4);
        A d = new A();

        c.f = b1;

        d.f = c.f;

        b = d.f;

        Benchmark.test(1, c.f); // expected: 3
        Benchmark.test(2, d.f); // expected: 3
        Benchmark.test(3, c); // expected: 2 3
        Benchmark.test(4, d); // expected: 4 3
        Benchmark.test(5, b); // expected: 3


    }

}

Benchmark.alloc(1);
A a = new A();

Benchmark.alloc(1);
B b = new B();

a.f = b;

Benchmark.test(1, b);



