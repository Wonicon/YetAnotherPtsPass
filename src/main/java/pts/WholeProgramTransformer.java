package pts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.util.queue.QueueReader;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class WholeProgramTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {

        TreeMap<Integer, Local> queries = new TreeMap<>();
        Anderson anderson = new Anderson();

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> qr = reachableMethods.listener();

        DebugLogger dl = new DebugLogger();
        boolean breakFlag = false;

        while (qr.hasNext()) {
            SootMethod method = qr.next().method();
            if (!method.hasActiveBody()) {
                continue;
            }

            int allocId = 0;
            for (Unit u : method.getActiveBody().getUnits()) {
                dl.log(dl.intraProc, u.toString() + "\n");
//                dl.log(dl.fieldSensitive, u.toString() + "\n");
                if (u instanceof InvokeStmt) {
//                    System.out.println("Reached here 0");

                    InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                    }
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                        Value v = ie.getArgs().get(1);
                        int id = ((IntConstant)ie.getArgs().get(0)).value;
                        queries.put(id, (Local)v);
                    }
//                    if (ie instanceof SpecialInvokeExpr) {
////                        dl.log(dl.fieldSensitive, "expr is special, alloID is");
////                        dl.log(dl.fieldSensitive, "unit is : %s , ie is : %s \n", u.toString(), ie.toString());
////                        System.out.println(ie.getArgBox(0));
////                        anderson.addNewConstraint(allocId, (Local)lop);
//
//                    }
                }
                if (u instanceof DefinitionStmt) {
                    dl.log(dl.disasm, "-> Definition stmt -> %s\n",
                            u.getClass().getSimpleName());
                    Object lop = ((DefinitionStmt)u).getLeftOp(),
                            rop = ((DefinitionStmt)u).getRightOp();

                    if (u instanceof JAssignStmt) {
                        if (rop instanceof NewExpr) {
                            if (rop.toString().equals("new java.util.Properties")) {
                                // This expr might be the end of user codes,
                                // break to lessen outputs;
                                breakFlag = true;
                                break;
                            }
                            // lop must be local, not ref
                            anderson.addNewConstraint(allocId, (Local)lop);
                            dl.log(dl.fieldSensitive, "newexpr : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.getClass().getSimpleName());
                        } else if (rop instanceof NewArrayExpr) {
                            anderson.addNewConstraint(allocId, (Local)lop);
                            // TODO: Add constraint for contents
//                            dl.log(dl.fieldSensitive, "left op of array is : %s\n", lop.toString());
                            anderson.addArrayConstraint(allocId);
                            dl.log(dl.fieldSensitive, "newarray : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        } else if (lop instanceof Local && rop instanceof Local) {
                            anderson.addAssignConstraint((Local) rop, (Local)lop);
                            dl.log(dl.fieldSensitive, "l->l : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        } else if (lop instanceof Local && rop instanceof Ref) {
                            anderson.addRef2LocalAssign((Ref) rop, (Local) lop);
                            dl.log(dl.fieldSensitive, "r->l: %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        } else if (lop instanceof Ref && rop instanceof Local) {
                            anderson.addLocal2RefAssign((Local) rop, (Ref) lop);
                            dl.log(dl.fieldSensitive, "l->r : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        } else if (lop instanceof Ref && rop instanceof Ref) {
                            dl.loge(dl.debug_all,"Impossible case, ignored!\n");

                        } else {
                            dl.loge(dl.intraProc, "lop: %s, rop: %s\n",
                                    lop.getClass().getSimpleName(), rop.getClass().getSimpleName());
                            dl.loge(dl.debug_all,"Not implemented case, ignored!\n");
//                            throw new NotImplementedException();
                        }
                    }
                }
            }
            if (breakFlag) {
                break;
            }
        }

        anderson.run();

        // Format result
        StringBuilder answer = new StringBuilder();
        for (Entry<Integer, Local> q : queries.entrySet()) {
            TreeSet<Integer> result = anderson.getPointsToSet(q.getValue());
            answer.append(q.getKey().toString()).append(":");
            for (Integer i: result) {
                answer.append(" ").append(i.toString());
            }
            answer.append("\n");
        }

        // Output result
        try (PrintStream ps = new PrintStream(new FileOutputStream(new File("result.txt")))) {
            ps.print(answer);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
