package pts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JLengthExpr;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.shimple.PhiExpr;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.util.queue.QueueReader;

public class WholeProgramTransformer extends SceneTransformer {
    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {

        TreeMap<Integer, Local> queries = new TreeMap<>();
        Anderson anderson = new Anderson();

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> qr = reachableMethods.listener();

        DebugLogger dl = new DebugLogger();
        boolean breakFlag = false;

        // Iterate over methods
        while (qr.hasNext()) {
            SootMethod method = qr.next().method();
            dl.log(dl.debug_all, "Visit method " + method.getName());
            if (!method.hasActiveBody()) {
                continue;
            }

            // Get SSA format
            ShimpleBody sb = Shimple.v().newBody(method.getActiveBody());

            int allocId = 0;

            for (Unit u : sb.getUnits()) {
                dl.log(dl.intraProc, u.toString() + ": " + u.getClass().getName());
                if (u instanceof InvokeStmt) {
                    InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
                    dl.log(dl.interProc, "invoke method " + ie.getMethod().getName());
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                    }
                    else if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                        Value v = ie.getArgs().get(1);
                        int id = ((IntConstant)ie.getArgs().get(0)).value;
                        queries.put(id, (Local)v);
                    }
                    else {
                        anderson.addCallSite(ie);
                        // No need to pass return value
                    }
                }
                if (u instanceof IdentityStmt && ((IdentityStmt) u).getRightOp() instanceof ParameterRef) {
                    ParameterRef param = (ParameterRef) ((IdentityStmt) u).getRightOp();
                    dl.log(dl.debug_all, "pass parameter " + param.getIndex());
                }
                if (u instanceof DefinitionStmt) {
                    dl.log(dl.disasm, "-> Definition stmt -> %s",
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
                        }
                        else if (rop instanceof PhiExpr) {
                            PhiExpr phi = ((PhiExpr) rop);
                            if (lop instanceof Ref) {
                                anderson.addPhi2Ref(phi, (Ref) lop);
                            }
                            else if (lop instanceof Local) {
                                anderson.addPhi2Local(phi, (Local) lop);
                            }
                        }
                        else if (rop instanceof NewArrayExpr) {
                            anderson.addNewConstraint(allocId, (Local)lop);
                            // TODO: Add constraint for contents
//                            dl.log(dl.fieldSensitive, "left op of array is : %s\n", lop.toString());
                            anderson.addArrayConstraint(allocId);
                            dl.log(dl.fieldSensitive, "newarray : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        }
                        else if (lop instanceof Local && rop instanceof Local) {
                            anderson.addAssignConstraint((Local) rop, (Local)lop);
                            dl.log(dl.fieldSensitive, "l->l : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        }
                        else if (lop instanceof Local && rop instanceof Ref) {
                            anderson.addRef2LocalAssign((Ref) rop, (Local) lop);
                            dl.log(dl.fieldSensitive, "r->l: %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        }
                        else if (lop instanceof Ref && rop instanceof Local) {
                            anderson.addLocal2RefAssign((Local) rop, (Ref) lop);
                            dl.log(dl.fieldSensitive, "l->r : %s, left is: %s, right is: %s\n", u.toString(), lop.toString(), rop.toString());

                        }
                        else if (lop instanceof Ref && rop instanceof Ref) {
                            dl.loge(dl.debug_all,"Impossible case, ignored!\n");

                        }
                        else if (rop instanceof InvokeExpr) {
                            InvokeExpr invoke = (InvokeExpr) rop;
                            anderson.addCallSite(invoke);
                            if (lop instanceof Ref) {
                                anderson.addReturn2RefAssign(invoke, (Ref) lop);
                            }
                            else if (lop instanceof Local) {
                                anderson.addReturn2LocalAssign(invoke, (Local) lop);
                            }
                            else {
                                dl.loge(dl.debug_all, "Unexpected left value type: " + lop.getClass().getName());
                            }
                        }
                        else if (rop instanceof JLengthExpr) {
                            dl.log(dl.debug_all, "Ignore x = lengthof y (for bound check)");
                        }
                        else {
                            dl.loge(dl.intraProc, "lop: %s, rop: %s",
                                    lop.getClass().getSimpleName(), rop.getClass().getSimpleName());
                            dl.loge(dl.debug_all,"Not implemented case, ignored!");
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
            Set<Integer> result = anderson.getPointsToSet(q.getValue());
            if (result != null) {
                answer.append(q.getKey().toString()).append(":");
                for (Integer i : result) {
                    answer.append(" ").append(i.toString());
                }
                answer.append("\n");
            }
            else {
                dl.log(dl.debug_all, "empty result for " + q.getValue());
            }
        }

        // Output result
        try (PrintStream ps = new PrintStream(new FileOutputStream(new File("result.txt")))) {
            ps.print(answer);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
