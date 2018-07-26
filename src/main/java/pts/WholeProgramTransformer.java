package pts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;

import soot.Local;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JLengthExpr;
import soot.shimple.PhiExpr;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;

public class WholeProgramTransformer extends SceneTransformer {
    private Queue<SootMethod> methodToVisit = new LinkedList<>();

    private Set<SootMethod> methodVisited = new HashSet<>();

    private DebugLogger dl = new DebugLogger();

    private Map<Integer, Local> queries = null;

    private Anderson anderson = null;

    private int allocId = -1;

    private SootMethod method;

    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        SootMethod mainEntry = Scene.v().getMainMethod();
        methodToVisit.offer(mainEntry);
        Anderson.pool.put(mainEntry, new Anderson(mainEntry));
        System.out.println("mainEntry " + mainEntry.hashCode());

        // Iterate over methods
        while (!methodToVisit.isEmpty()) {
            method = methodToVisit.poll();
            System.out.println("method " + method.hashCode());
            methodVisited.add(method);

            dl.log(dl.debug_all, "Visit method " + method);
            if (!method.hasActiveBody()) {
                dl.log(true, "Discard method " + method);
                continue;
            }

            if (Anderson.pool.containsKey(method)) {
                System.out.println("contain!");
            }
            anderson = Anderson.pool.get(method);
            queries = anderson.queries;
            // TODO Detect recursion!!!

            // Get SSA format
            ShimpleBody sb = Shimple.v().newBody(method.getActiveBody());

            for (Unit u : sb.getUnits()) {
                dl.log(dl.intraProc, "Visit unit:: " + u.toString() + ": " + u.getClass().getName());
                if (dispatchUnit(u)) {
                    break;
                }
            }

            anderson = null;
            queries = null;
        }

        // TODO many anderson run()
        boolean next = true;
        while (next) {
            next = false;
            for (Anderson andersonPerMethod : Anderson.pool.values()) {
                if (andersonPerMethod.enabled()) {
                    andersonPerMethod.run();
                }
            }
            // Some already runned anderson is waken by others.
            for (Anderson anderson : Anderson.pool.values()) {
                if (anderson.enabled()) {
                    next = true;
                }
            }
        }

        // Format result
        StringBuilder answer = new StringBuilder();
        for (Anderson ads : Anderson.pool.values()) {
            for (Entry<Integer, Local> q : ads.queries.entrySet()) {
                Set<Integer> result = ads.getPointsToSet(q.getValue());
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
        }

        // Output result
        try (PrintStream ps = new PrintStream(new FileOutputStream(new File("result.txt")))) {
            ps.print(answer);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean dispatchUnit(Unit u) {
        if (u instanceof InvokeStmt) {
            InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
            dl.log(dl.interProc, "invoke method " + ie.getMethod().getName());
            switch (ie.getMethod().toString()) {
                case "<benchmark.internal.Benchmark: void alloc(int)>":
                    allocId = ((IntConstant) ie.getArgs().get(0)).value;
                    return false;
                case "<benchmark.internal.Benchmark: void test(int,java.lang.Object)>":
                    Value v = ie.getArgs().get(1);
                    int id = ((IntConstant) ie.getArgs().get(0)).value;
                    queries.put(id, (Local) v);
                    return false;
            }

            if (!methodVisited.contains(ie.getMethod()) && !methodToVisit.contains(ie.getMethod())) {
                methodToVisit.offer(ie.getMethod());
                Anderson.pool.put(ie.getMethod(), new Anderson(ie.getMethod()));
                anderson.calleeList.add(Anderson.pool.get(ie.getMethod()));
                dl.log(dl.debug_all, "Prepare to visit method " + ie.getMethod());
            }
            Anderson.pool.get(ie.getMethod()).addCallSite(ie, method);
            // No need to pass return value
        }

        if (u instanceof IdentityStmt && ((IdentityStmt) u).getRightOp() instanceof ParameterRef) {
            ParameterRef param = (ParameterRef) ((IdentityStmt) u).getRightOp();
            dl.log(dl.debug_all, "pass parameter " + param.getIndex());
            anderson.addParamAssign(param, (Local)((IdentityStmt) u).getLeftOp());
        }

        if (u instanceof DefinitionStmt) {
            dl.log(dl.disasm, "-> Definition stmt -> %s", u.getClass().getSimpleName());

            if (u instanceof JAssignStmt) {
                return dispatchAssignment((JAssignStmt) u);
            }
        }

        if (u instanceof ReturnStmt) {
            ReturnStmt rtn = (ReturnStmt) u;
            Value rtnVal = rtn.getOp();
            if (rtnVal instanceof Local) {
                Local local = (Local) rtnVal;
                anderson.addReturn(local);
            }
        }

        return false;
    }

    private boolean dispatchAssignment(JAssignStmt stmt) {
        Value lop = stmt.getLeftOp(), rop = stmt.getRightOp();

        if (rop instanceof NewExpr) {
            if (rop.toString().equals("new java.util.Properties")) {
                // This expr might be the end of user codes,
                // break to lessen outputs;
                return true;
            }
            // lop must be local, not ref
            anderson.addNewConstraint(allocId, (Local)lop);
            allocId = -1;
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
        }
        else if (lop instanceof Local && rop instanceof Local) {
            anderson.addAssignConstraint((Local) rop, (Local)lop);
        }
        else if (lop instanceof Local && rop instanceof Ref) {
            anderson.addRef2LocalAssign((Ref) rop, (Local) lop);
        }
        else if (lop instanceof Ref && rop instanceof Local) {
            anderson.addLocal2RefAssign((Local) rop, (Ref) lop);
        }
        else if (lop instanceof Ref && rop instanceof Ref) {
            dl.loge(dl.debug_all, "Impossible case, ignored!");
        }
        else if (rop instanceof InvokeExpr) {
            InvokeExpr invoke = (InvokeExpr) rop;
            SootMethod callee = invoke.getMethod();
            if (!methodVisited.contains(callee) && !methodToVisit.contains(callee)) {
                methodToVisit.offer(callee);
                Anderson.pool.put(callee, new Anderson(callee));
                anderson.calleeList.add(Anderson.pool.get(callee));
                dl.log(dl.debug_all, "Prepare to visit method " + invoke.getMethod().getName());
            }
            Anderson.pool.get(callee).addCallSite(invoke, method);
            if (lop instanceof Ref) {
                dl.log(true, "HIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIT");
                anderson.addReturn2RefAssign(invoke, (Ref) lop);
            }
            else if (lop instanceof Local) {
                anderson.addInvoke2Local(invoke, (Local) lop);
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
        }

        return false;
    }
}
