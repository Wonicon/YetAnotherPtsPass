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
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.CompleteBlockGraph;

public class WholeProgramTransformer extends SceneTransformer {
    private Queue<SootMethod> methodToVisit = new LinkedList<>();

    private Set<SootMethod> methodVisited = new HashSet<>();

    private DebugLogger dl = new DebugLogger();

    private Map<Integer, Local> queries = null;

    private Anderson anderson = null;

    private int allocId = -1;

    private SootMethod method;

    private List<MemEnv> memEnvList;

    private MemEnv genMemEnv()
    {
        MemEnv env = new MemEnv();
        memEnvList.add(env);
        return env;
    }

    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        SootMethod mainEntry = Scene.v().getMainMethod();
        methodToVisit.offer(mainEntry);
        Anderson.pool.put(mainEntry, new Anderson(mainEntry));
        System.out.println("mainEntry " + mainEntry.hashCode());

        // Iterate over methods
        while (!methodToVisit.isEmpty()) {
            method = methodToVisit.poll();
            methodVisited.add(method);

            dl.log(dl.debug_all, "Visit method " + method);
            if (!method.hasActiveBody()) {
                dl.log(true, "Discard method " + method);
                continue;
            }

            anderson = Anderson.pool.get(method);
            queries = anderson.queries;
            // TODO Detect recursion!!!

            // Get SSA format
            ShimpleBody sb = Shimple.v().newBody(method.getActiveBody());
            BlockGraph g = new CompleteBlockGraph(sb);

            for (Block blk : g.getBlocks()) {
                for (Unit u : blk.getBody().getUnits()) {
                    dl.log(dl.intraProc, "Visit unit:: " + u.toString() + ": " + u.getClass().getName());
                    if (dispatchUnit(u)) {
                        break;
                    }
                }
            }

            buildEnvChain(g);

            anderson = null;
            queries = null;
        }

        // TODO many anderson run()
        boolean next = true;
        for (int round = 0; next; round++) {
            next = false;
            dl.log(dl.debug_all, "Global round " + round);
            for (Anderson andersonPerMethod : Anderson.pool.values()) {
                next |= andersonPerMethod.run();
            }

            for (Anderson andersonPerMethod : Anderson.pool.values()) {
                next |= andersonPerMethod.enabled();
            }

            /*
            dl.log(dl.debug_all, "HEAP SUMMARY >>>");
            for (Entry<Integer, Set<Integer>> entry : Anderson.arrayContentPTS.entrySet()) {
                System.out.print(entry.getKey() + " ->");
                for (Integer i : entry.getValue()) {
                    System.out.print(" " + i);
                }
                System.out.println();
            }
            */
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

        if (u instanceof IdentityStmt) {
            IdentityStmt stmt = (IdentityStmt) u;

            if (stmt.getRightOp() instanceof ParameterRef) {
                ParameterRef param = (ParameterRef) ((IdentityStmt) u).getRightOp();
                dl.log(dl.debug_all, "pass parameter " + param.getIndex());
                anderson.addParamAssign(param, (Local) ((IdentityStmt) u).getLeftOp());
            }

            if (stmt.getRightOp() instanceof ThisRef) {
                ThisRef thisRef = (ThisRef) stmt.getRightOp();
                dl.log(dl.debug_all, "pass this ");
                anderson.addThisAssign(thisRef, (Local)stmt.getLeftOp());
            }
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
            dl.log(dl.debug_all, "add local to ref");
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

    private void buildEnvChain(BlockGraph g)
    {
        Anderson anderson = Anderson.pool.get(method);
        memEnvList = anderson.memEnvList;

        if (g.getHeads().size() != 1) {
            dl.loge(dl.env, "Oops, a method has multiple entrance");
            System.exit(-1);
        }

        Map<Block, MemEnv> envOut = new HashMap<>(), envIn = new HashMap<>();

        // Generate env out for each block.
        for (Block blk : g.getBlocks()) {
            envOut.put(blk, genMemEnv());
        }

        // Generate env in for each block.
        for (Block blk : g.getBlocks()) {
            MemEnv in = genMemEnv();
            for (Block pred : blk.getPreds()) {
                in.addParent(envOut.get(pred));
            }
            if (blk.getPreds().size() == 0) {
                anderson.entryEnv = in;
            }
            envIn.put(blk, in);
        }

        // Dispatch inst
        for (Block block : g.getBlocks()) {
            MemEnv currEnv = envIn.get(block);
            for (Unit u : block.getBody().getUnits()) {
                if (u instanceof InvokeStmt) {
                    InvokeStmt stmt = (InvokeStmt) u;
                    MemEnv env = genMemEnv();
                    // chain
                    dl.log(dl.debug_all, "Handle " + u);
                    if (currEnv == null) {
                        System.out.println("fuck you");
                    }
                    System.out.println(stmt.getInvokeExpr().hashCode());
                    Anderson.expr2EnvIn.put(stmt.getInvokeExpr(), currEnv);
                    Anderson.expr2EnvOut.put(stmt.getInvokeExpr(), env);
                    env.addParent(currEnv);
                    envOut.get(block).resetParent();
                    envOut.get(block).addParent(env);
                    currEnv = env;
                }

                if (u instanceof ReturnStmt) {
                    ReturnStmt rtn = (ReturnStmt) u;
                    Anderson.rtn2EnvIn.put(rtn.getOp(), currEnv);
                }

                if (u instanceof ReturnVoidStmt) {
                    anderson.voidReturnEnvIn.add(currEnv);
                }

                if (!(u instanceof AssignStmt)) {
                    continue;
                }

                AssignStmt assign = (AssignStmt) u;
                Value lop = assign.getLeftOp(), rop = assign.getRightOp();
                if (lop instanceof Ref) {
                    Ref ref = (Ref) lop;
                    MemEnv env = genMemEnv();
                    // chain
                    Anderson.ref2EnvIn.put(ref, currEnv);
                    Anderson.ref2EnvOut.put(ref, env);
                    env.addParent(currEnv);
                    envOut.get(block).resetParent();
                    envOut.get(block).addParent(env);
                    currEnv = env;
                }
                else if (rop instanceof Ref) {
                    Ref ref = (Ref) rop;
                    // partial chain
                    Anderson.ref2EnvIn.put(ref, currEnv);
                }
                else if (rop instanceof InvokeExpr) {
                    InvokeExpr invoke = (InvokeExpr) rop;
                    MemEnv env = genMemEnv();
                    // chain
                    Anderson.expr2EnvIn.put(invoke, currEnv);
                    Anderson.expr2EnvOut.put(invoke, env);
                    env.addParent(currEnv);
                    envOut.get(block).resetParent();
                    envOut.get(block).addParent(env);
                    currEnv = env;
                }
            }
        }
    }
}
